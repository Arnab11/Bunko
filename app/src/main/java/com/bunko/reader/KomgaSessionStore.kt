package com.bunko.reader

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.komgaDataStore by preferencesDataStore("komga_session")

data class KomgaSession(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = ""
)

data class KomgaServerProfile(
    val id: String,
    val name: String,
    val session: KomgaSession,
    val openByDefault: Boolean
)

@Serializable
private data class StoredKomgaProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val openByDefault: Boolean = false
)

class KomgaSessionStore(private val context: Context) {
    private val KEY_PROFILES = stringPreferencesKey("komgaServerProfiles")

    private val json = Json { ignoreUnknownKeys = true }
    private var transientSession: KomgaSession? = null
    private var activeProfileId: String? = null

    @Volatile
    private var cachedActiveSession: KomgaSession? = null

    fun useTransient(session: KomgaSession) {
        transientSession = session.normalized()
    }

    suspend fun load(): KomgaSession {
        transientSession?.let { return it }
        cachedActiveSession?.let { return it }
        val resolved = activeProfile()?.session ?: KomgaSession()
        cachedActiveSession = resolved
        return resolved
    }

    suspend fun loadDefault(): KomgaSession {
        transientSession?.let { return it }
        return profiles().firstOrNull { it.openByDefault }?.session ?: KomgaSession()
    }

    suspend fun profiles(): List<KomgaServerProfile> {
        val prefs = context.applicationContext.komgaDataStore.data.first()
        val stored = decodeStoredProfiles(prefs[KEY_PROFILES])
        return stored.map { it.toProfile() }
    }

    suspend fun activeProfile(): KomgaServerProfile? {
        val all = profiles()
        return all.firstOrNull { it.id == activeProfileId }
            ?: all.firstOrNull { it.openByDefault }
            ?: all.firstOrNull()
    }

    fun selectProfile(id: String?) {
        activeProfileId = id
        transientSession = null
        cachedActiveSession = null
    }

    suspend fun saveProfile(
        profileId: String?,
        session: KomgaSession,
        rememberAuth: Boolean = true,
        openByDefault: Boolean? = null
    ): KomgaServerProfile {
        val normalized = session.normalized()
        val existingProfiles = profiles()
        val existing = existingProfiles.firstOrNull { it.id == profileId }
            ?: existingProfiles.firstOrNull { it.matchesServerIdentity(normalized) }
        val savedSession = if (rememberAuth) {
            normalized
        } else {
            normalized.copy(username = "", password = "", apiKey = "")
        }
        val savedProfile = KomgaServerProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = profileName(normalized.baseUrl, normalized.username),
            session = savedSession,
            openByDefault = openByDefault ?: existing?.openByDefault ?: existingProfiles.none { it.openByDefault }
        )
        val nextProfiles = existingProfiles
            .filterNot { it.id == savedProfile.id || it.matchesServerIdentity(normalized) }
            .plus(savedProfile)
            .normalizeDefault(savedProfile.id, savedProfile.openByDefault)

        activeProfileId = savedProfile.id
        transientSession = normalized
        writeProfiles(nextProfiles)
        return savedProfile
    }

    suspend fun setOpenByDefault(profileId: String, openByDefault: Boolean) {
        val nextProfiles = profiles().map { profile ->
            when {
                profile.id == profileId -> profile.copy(openByDefault = openByDefault)
                openByDefault -> profile.copy(openByDefault = false)
                else -> profile
            }
        }
        writeProfiles(nextProfiles)
    }

    suspend fun setDefaultProfile(profileId: String) {
        setOpenByDefault(profileId, true)
    }

    suspend fun deleteProfile(profileId: String?) {
        val targetId = profileId ?: return
        val nextProfiles = profiles().filterNot { it.id == targetId }
        if (activeProfileId == targetId) {
            activeProfileId = nextProfiles.firstOrNull { it.openByDefault }?.id ?: nextProfiles.firstOrNull()?.id
            transientSession = null
        }
        writeProfiles(nextProfiles)
    }

    private suspend fun writeProfiles(profiles: List<KomgaServerProfile>) {
        cachedActiveSession = null
        val stored = profiles.map { it.toStored() }
        context.applicationContext.komgaDataStore.edit { prefs ->
            prefs[KEY_PROFILES] = json.encodeToString(ListSerializer(StoredKomgaProfile.serializer()), stored)
        }
        cachedActiveSession = null
    }

    private fun decodeStoredProfiles(value: String?): List<StoredKomgaProfile> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(StoredKomgaProfile.serializer()), value)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun StoredKomgaProfile.toProfile(): KomgaServerProfile {
        val session = KomgaSession(
            baseUrl = baseUrl,
            username = KomgaSecureCipher.decryptOrLegacy(username),
            password = KomgaSecureCipher.decryptOrLegacy(password),
            apiKey = KomgaSecureCipher.decryptOrLegacy(apiKey)
        ).normalized()
        return KomgaServerProfile(
            id = id,
            name = name.ifBlank { profileName(session.baseUrl, session.username) },
            session = session,
            openByDefault = openByDefault
        )
    }

    private fun KomgaServerProfile.toStored(): StoredKomgaProfile {
        return StoredKomgaProfile(
            id = id,
            name = name,
            baseUrl = session.baseUrl,
            username = KomgaSecureCipher.encrypt(session.username.trim()),
            password = KomgaSecureCipher.encrypt(session.password),
            apiKey = KomgaSecureCipher.encrypt(session.apiKey.trim()),
            openByDefault = openByDefault
        )
    }

    private fun List<KomgaServerProfile>.normalizeDefault(
        changedProfileId: String,
        changedProfileIsDefault: Boolean
    ): List<KomgaServerProfile> {
        return if (changedProfileIsDefault) {
            map { profile -> profile.copy(openByDefault = profile.id == changedProfileId) }
        } else {
            this
        }
    }

    private fun KomgaSession.normalized(): KomgaSession {
        return copy(baseUrl = baseUrl.trim().trimEnd('/'), username = username.trim(), password = password, apiKey = apiKey.trim())
    }

    private fun profileName(baseUrl: String, username: String): String {
        val server = baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .ifBlank { "New Server" }
        return if (username.isBlank()) server else "$server / $username"
    }

    private fun KomgaServerProfile.matchesServerIdentity(session: KomgaSession): Boolean {
        if (session.baseUrl.isBlank() || this.session.baseUrl.isBlank()) return false
        if (!this.session.baseUrl.equals(session.baseUrl, ignoreCase = true)) return false
        return this.session.username.equals(session.username, ignoreCase = true) ||
            this.session.username.isBlank() ||
            session.username.isBlank()
    }
}

private object KomgaSecureCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bunko_komga_session_v1"
    private const val PREFIX = "aesgcm-v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$PREFIX:$iv:$payload"
    }

    fun decryptOrLegacy(value: String): String {
        if (value.isBlank()) return ""
        if (!value.startsWith("$PREFIX:")) return value
        val parts = value.split(":")
        require(parts.size == 3) { "Invalid encrypted session value" }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
