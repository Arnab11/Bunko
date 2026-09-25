package com.bunko.reader.tts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.bunko.reader.BunkoLog
import com.bunko.reader.reader.internal.EpubSubpage
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * E-book-only Text-To-Speech controller.
 *
 * Owns one Android [TextToSpeech] instance, the chunk queue for the current
 * subpage, and the word highlight derived from [UtteranceProgressListener.onRangeStart]
 * (API 26+, which matches minSdk 26).
 *
 * Speak flow: [speakSubpage] -> [TtsSession] -> [chunkForTts] -> QUEUE_ADD chain.
 * Offsets from onRangeStart are per-utterance, so they are rebased with the
 * chunk start offset and mapped back to a block via [TtsSession.highlightFor].
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tts = TextToSpeech(appContext, this)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _highlight = MutableStateFlow<TtsHighlight?>(null)
    val highlight: StateFlow<TtsHighlight?> = _highlight.asStateFlow()

    /** Global char offset currently spoken (in session.combined space), for resume. */
    private val _spokenOffset = MutableStateFlow(0)
    val spokenOffset: StateFlow<Int> = _spokenOffset.asStateFlow()

    var onCompleted: (() -> Unit)? = null

    private var session: TtsSession? = null
    private var chunks: List<Pair<Int, String>> = emptyList()
    private var initRate = 1f
    private var volume = 1f

    /** Applied once the engine is ready, and on every rate change after that. */
    fun setSpeechRate(rate: Float) {
        initRate = rate.coerceIn(0.25f, 3f)
        if (_ready.value) runCatching { tts.setSpeechRate(initRate) }
    }

    /** Set TTS speech volume (0.0 to 1.0). */
    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
    }

    /** Best-effort locale pick; returns false when nothing suitable is available. */
    fun setLanguage(locale: Locale): Boolean {
        if (!_ready.value) return false
        return runCatching {
            val availability = tts.isLanguageAvailable(locale)
            if (availability == TextToSpeech.LANG_MISSING_DATA ||
                availability == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                false
            } else {
                tts.language = locale
                true
            }
        }.getOrDefault(false)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            BunkoLog.w("TTS engine init failed with status $status.")
            return
        }
        runCatching { tts.setSpeechRate(initRate) }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    _speaking.value = true
                    val sess = session ?: return@launch
                    val chunkIndex = utteranceId
                        ?.removePrefix(UTTERANCE_PREFIX)
                        ?.toIntOrNull() ?: return@launch
                    val (chunkStart, chunkText) = chunks.getOrNull(chunkIndex) ?: return@launch
                    _spokenOffset.value = chunkStart
                    val h = sess.highlightFor(chunkStart, chunkStart + chunkText.length)
                    if (_highlight.value == null || _highlight.value?.blockIndex != h?.blockIndex) {
                        _highlight.value = h
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    if (utteranceId == lastUtteranceId()) {
                        _speaking.value = false
                        _highlight.value = null
                        val cb = onCompleted
                        onCompleted = null
                        cb?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch { _speaking.value = false }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                scope.launch { _speaking.value = false }
                BunkoLog.w("TTS error on $utteranceId: $errorCode.")
            }

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                val sess = session ?: return
                val chunkIndex = utteranceId
                    ?.removePrefix(UTTERANCE_PREFIX)
                    ?.toIntOrNull() ?: return
                val chunkStart = chunks.getOrNull(chunkIndex)?.first ?: 0
                val globalStart = chunkStart + start
                val globalEnd = chunkStart + end
                val h = sess.highlightFor(globalStart, globalEnd)
                if (h != null) {
                    scope.launch {
                        _highlight.value = h
                        _spokenOffset.value = globalStart
                    }
                }
            }
        })
        _ready.value = true
    }

    private fun lastUtteranceId(): String? =
        if (chunks.isEmpty()) null else "$UTTERANCE_PREFIX${chunks.lastIndex}"

    /**
     * Speaks the text blocks of [subpage]. Returns false when there is no
     * speakable text (image-only page).
     */
    fun speakSubpage(subpage: EpubSubpage): Boolean {
        val sess = buildTtsSession(subpage.ttsParagraphs())
        if (sess.isEmpty) return false
        return speakSession(sess)
    }

    /**
     * Resume from a global offset (used after page change / pause). Clamps into range.
     * Kept simple: re-queues the tail of the same session from the nearest chunk start.
     */
    fun resumeFrom(offset: Int): Boolean {
        val sess = session ?: return false
        if (sess.isEmpty) return false
        val safe = offset.coerceIn(0, sess.combined.length)
        // Find first chunk overlapping the offset, then speak from there.
        val all = chunkForTts(sess.combined)
        val startIdx = all.indexOfLast { (chunkStart, _) -> chunkStart <= safe }.coerceAtLeast(0)
        enqueueChunks(sess, all, fromIndex = startIdx)
        return true
    }

    private fun speakSession(sess: TtsSession): Boolean {
        if (!_ready.value) {
            BunkoLog.w("TTS speak requested before engine ready.")
            return false
        }
        session = sess
        val all = chunkForTts(sess.combined)
        enqueueChunks(sess, all, fromIndex = 0)
        return true
    }

    private fun enqueueChunks(sess: TtsSession, all: List<Pair<Int, String>>, fromIndex: Int) {
        runCatching { tts.stop() }
        chunks = all
        _highlight.value = null
        _spokenOffset.value = all.getOrNull(fromIndex)?.first ?: 0
        _speaking.value = true
        all.drop(fromIndex).forEachIndexed { i, (_, text) ->
            val utteranceId = "$UTTERANCE_PREFIX${fromIndex + i}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            }
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val rc = tts.speak(text, mode, params, utteranceId)
            if (rc == TextToSpeech.ERROR) {
                BunkoLog.w("TTS speak() rejected chunk $utteranceId.")
            }
        }
        void(sess)
    }

    private fun void(@Suppress("UNUSED_PARAMETER") sess: TtsSession) = Unit

    fun stop() {
        runCatching { tts.stop() }
        _speaking.value = false
        _highlight.value = null
        onCompleted = null
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
        _speaking.value = false
        _ready.value = false
    }

    companion object {
        private const val UTTERANCE_PREFIX = "bunko-tts-"

        /** Intent to send the user to install TTS voice data when init fails. */
        fun installVoiceDataIntent(): Intent =
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }
}
