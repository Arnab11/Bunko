package karacken.curl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min

/** GLES2 renderer for client-prepared page bitmaps and PlayLikeCurl deformation. */
class PageRenderer(
    private val events: Events,
    initialPaperColor: Int = 0xFFFFFFFF.toInt()
) : GLSurfaceView.Renderer {

    interface Events {
        fun onCapabilitiesAvailable(capabilities: RenderCapabilities)
        fun onFirstFrameRendered()
        fun onDeckPrepared(generationId: Long)
        fun onDeckReleased(generationId: Long, reason: DeckReleaseReason)
        fun onRenderFailure(failure: RenderFailure)
    }

    private val leftMesh = GpuMesh(PageRole.LEFT)
    private val frontMesh = GpuMesh(PageRole.FRONT)
    private val mirroredLeftMesh = GpuMesh(PageRole.LEFT, true)
    private val mirroredFrontMesh = GpuMesh(PageRole.FRONT, true)
    private val rightMesh = GpuMesh(PageRole.RIGHT)
    private val textureCache = LinkedHashMap<String, GpuTexture>()
    private val flatState = PageState(
        PageRole.RIGHT, PlayLikeCurlModel.RIGHT_DEPTH, PlayLikeCurlModel.GRID.toFloat(), 0
    )
    private val turningState = PageState(
        PageRole.FRONT, PlayLikeCurlModel.FRONT_DEPTH, PlayLikeCurlModel.GRID.toFloat(), 0
    )
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val shadowPositionBuffer: FloatBuffer = directFloatBuffer(12)
    private val shadowGradientBuffer: FloatBuffer = directFloatBuffer(4)
    private val shadowIndexBuffer: ShortBuffer = directShortBuffer(SHADOW_INDICES.size)

    @Volatile
    internal var portraitModel: PlayLikeCurlModel? = null
        private set

    @Volatile
    internal var landscapeSpreadModel: LandscapeSpreadModel? = null
        private set

    @Volatile
    private var activeDeck: PageDeck<Bitmap>? = null
    @Volatile
    private var replacementDeck: PageDeck<Bitmap>? = null
    @Volatile
    private var portraitLeftResource: PageImage<Bitmap>? = null
    @Volatile
    private var portraitFrontResource: PageImage<Bitmap>? = null
    @Volatile
    private var portraitRightResource: PageImage<Bitmap>? = null
    @Volatile
    private var spreadPreviousLeftResource: PageImage<Bitmap>? = null
    @Volatile
    private var spreadPreviousRightResource: PageImage<Bitmap>? = null
    @Volatile
    private var spreadCurrentLeftResource: PageImage<Bitmap>? = null
    @Volatile
    private var spreadCurrentRightResource: PageImage<Bitmap>? = null
    @Volatile
    private var spreadNextLeftResource: PageImage<Bitmap>? = null
    @Volatile
    private var spreadNextRightResource: PageImage<Bitmap>? = null

    private var viewportWidth = 1
    private var viewportHeight = 1
    private var program = 0
    private var positionAttribute = 0
    private var textureCoordinateAttribute = 0
    private var matrixUniform = 0
    private var textureUniform = 0
    private var overlayTextureUniform = 0
    private var hasOverlayUniform = 0
    private var backTextureUniform = 0
    private var hasBackTextureUniform = 0
    private var shadowProgram = 0
    private var shadowPositionAttribute = 0
    private var shadowGradientAttribute = 0
    private var shadowMatrixUniform = 0
    private var shadowOpacityUniform = 0
    private var maxTextureSize = 0
    private var gpuBudgetBytes = DEFAULT_GPU_BUDGET_BYTES
    private var glReady = false
    private var disposed = false
    private var firstFrameDrawn = false

    // Bunko: paper background clear color
    private var clearRed = 1f
    private var clearGreen = 1f
    private var clearBlue = 1f

    init {
        setInitialBackgroundColor(
            (initialPaperColor shr 16) and 0xFF,
            (initialPaperColor shr 8) and 0xFF,
            initialPaperColor and 0xFF
        )
    }

    fun setInitialBackgroundColor(red: Int, green: Int, blue: Int) {
        clearRed = max(0, min(255, red)) / 255f
        clearGreen = max(0, min(255, green)) / 255f
        clearBlue = max(0, min(255, blue)) / 255f
    }

    fun prepareDeck(deck: PageDeck<Bitmap>, activateWhenPrepared: Boolean) {
        if (disposed) {
            reportFailure(
                deck.generationId,
                false,
                RenderFailureReason.DISPOSED,
                "Renderer is disposed",
                null
            )
            events.onDeckReleased(deck.generationId, DeckReleaseReason.FAILED)
            return
        }
        var retained = false
        try {
            validateDeck(deck)
            val prospectiveActive = if (activateWhenPrepared) deck else activeDeck
            val prospectivePending = if (activateWhenPrepared) null else deck
            val budget = TextureBudget.evaluate(
                prospectiveActive,
                prospectivePending,
                maxTextureSize,
                gpuBudgetBytes
            )
            if (budget.failureReason != null) {
                reportBudgetFailure(deck.generationId, budget)
                events.onDeckReleased(deck.generationId, DeckReleaseReason.FAILED)
                return
            }
            replacementDeck = deck
            retained = true
            retainDeckTextures()
            if (glReady) {
                uploadDeck(deck)
                if (activateWhenPrepared) {
                    val oldActive = activeDeck
                    activeDeck = deck
                    replacementDeck = null
                    applyActiveDeck(deck)
                    retainDeckTextures()
                    if (oldActive != null && oldActive.generationId != deck.generationId) {
                        events.onDeckReleased(oldActive.generationId, DeckReleaseReason.REPLACED)
                    }
                }
                events.onDeckPrepared(deck.generationId)
            } else if (activateWhenPrepared) {
                activeDeck = deck
                replacementDeck = null
                applyActiveDeck(deck)
            }
        } catch (exception: RuntimeException) {
            reportFailure(
                deck.generationId,
                true,
                RenderFailureReason.BITMAP,
                "Could not prepare page deck",
                exception
            )
            if (retained) {
                releaseDeck(deck.generationId, DeckReleaseReason.FAILED)
            } else {
                events.onDeckReleased(deck.generationId, DeckReleaseReason.FAILED)
            }
        }
    }

    fun setGpuBudgetBytes(gpuBudgetBytes: Long) {
        require(gpuBudgetBytes > 0) { "gpuBudgetBytes must be positive" }
        this.gpuBudgetBytes = gpuBudgetBytes
        publishCapabilities()
    }

    fun activateDeck(generationId: Long) {
        if (disposed) return
        if (replacementDeck != null && replacementDeck!!.generationId == generationId) {
            val releasedDeck = activeDeck
            activeDeck = replacementDeck
            replacementDeck = null
            applyActiveDeck(activeDeck!!)
            retainDeckTextures()
            if (releasedDeck != null && releasedDeck.generationId != activeDeck!!.generationId) {
                events.onDeckReleased(releasedDeck.generationId, DeckReleaseReason.REPLACED)
            }
        }
    }

    fun commitTurn(pageChange: PageChange) {
        if (disposed || activeDeck == null) return
        if (pageChange == PageChange.NEXT) {
            if (portraitFrontResource != null && portraitRightResource != null) {
                portraitLeftResource = portraitFrontResource
                portraitFrontResource = portraitRightResource
            }
            portraitModel?.jumpTo(1)
            if (spreadCurrentLeftResource != null && spreadNextLeftResource != null) {
                spreadPreviousLeftResource = spreadCurrentLeftResource
                spreadPreviousRightResource = spreadCurrentRightResource
                spreadCurrentLeftResource = spreadNextLeftResource
                spreadCurrentRightResource = spreadNextRightResource
            }
            landscapeSpreadModel?.jumpTo(2)
        } else if (pageChange == PageChange.PREVIOUS) {
            if (portraitFrontResource != null && portraitLeftResource != null) {
                portraitRightResource = portraitFrontResource
                portraitFrontResource = portraitLeftResource
            }
            portraitModel?.jumpTo(1)
            if (spreadCurrentLeftResource != null && spreadPreviousLeftResource != null) {
                spreadNextLeftResource = spreadCurrentLeftResource
                spreadNextRightResource = spreadCurrentRightResource
                spreadCurrentLeftResource = spreadPreviousLeftResource
                spreadCurrentRightResource = spreadPreviousRightResource
            }
            landscapeSpreadModel?.jumpTo(2)
        }
    }

    fun setViewport(width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
    }

    /** Bunko: matches the GL clear color to the reader paper color (0-255 channels). */
    fun setBackgroundColor(red: Int, green: Int, blue: Int) {
        clearRed = max(0, min(255, red)) / 255f
        clearGreen = max(0, min(255, green)) / 255f
        clearBlue = max(0, min(255, blue)) / 255f
        if (glReady) {
            queueClearColor()
        }
    }

    private fun queueClearColor() {
        GLES20.glClearColor(clearRed, clearGreen, clearBlue, 1f)
    }

    fun releaseDeck(generationId: Long, reason: DeckReleaseReason) {
        var released = false
        if (activeDeck != null && activeDeck!!.generationId == generationId) {
            activeDeck = null
            clearActiveDeck()
            released = true
        }
        if (replacementDeck != null && replacementDeck!!.generationId == generationId) {
            replacementDeck = null
            released = true
        }
        retainDeckTextures()
        if (released) {
            events.onDeckReleased(generationId, reason)
        }
    }

    fun dispose() {
        if (disposed) return
        val releasedGenerations = LinkedHashSet<Long>()
        if (activeDeck != null) {
            releasedGenerations.add(activeDeck!!.generationId)
        }
        if (replacementDeck != null) {
            releasedGenerations.add(replacementDeck!!.generationId)
        }
        disposed = true
        activeDeck = null
        replacementDeck = null
        clearActiveDeck()
        for (texture in textureCache.values) {
            texture.deleteGl()
        }
        textureCache.clear()
        leftMesh.dispose()
        frontMesh.dispose()
        mirroredLeftMesh.dispose()
        mirroredFrontMesh.dispose()
        rightMesh.dispose()
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        if (shadowProgram != 0) {
            GLES20.glDeleteProgram(shadowProgram)
            shadowProgram = 0
        }
        glReady = false
        for (generationId in releasedGenerations) {
            events.onDeckReleased(generationId, DeckReleaseReason.DISPOSED)
        }
    }

    /**
     * Drops every client bitmap reference after the GL thread has been paused.
     *
     * This is the terminal fallback for a detached surface whose GL event queue can no longer
     * be relied upon to execute [dispose].
     */
    fun abandonClientState() {
        if (disposed) return
        disposed = true
        activeDeck = null
        replacementDeck = null
        clearActiveDeck()
        textureCache.clear()
        glReady = false
    }

    override fun onSurfaceCreated(ignored: GL10?, config: EGLConfig?) {
        if (disposed) return
        try {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            positionAttribute = GLES20.glGetAttribLocation(program, "aPosition")
            textureCoordinateAttribute = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
            matrixUniform = GLES20.glGetUniformLocation(program, "uMvpMatrix")
            textureUniform = GLES20.glGetUniformLocation(program, "uTexture")
            overlayTextureUniform = GLES20.glGetUniformLocation(program, "uOverlayTexture")
            hasOverlayUniform = GLES20.glGetUniformLocation(program, "uHasOverlay")
            backTextureUniform = GLES20.glGetUniformLocation(program, "uBackTexture")
            hasBackTextureUniform = GLES20.glGetUniformLocation(program, "uHasBackTexture")

            shadowProgram = createProgram(SHADOW_VERTEX_SHADER, SHADOW_FRAGMENT_SHADER)
            shadowPositionAttribute = GLES20.glGetAttribLocation(shadowProgram, "aPosition")
            shadowGradientAttribute = GLES20.glGetAttribLocation(shadowProgram, "aGradient")
            shadowMatrixUniform = GLES20.glGetUniformLocation(shadowProgram, "uMvpMatrix")
            shadowOpacityUniform = GLES20.glGetUniformLocation(shadowProgram, "uOpacity")

            GLES20.glClearColor(clearRed, clearGreen, clearBlue, 1f)
            GLES20.glClearDepthf(1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            val textureLimits = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, textureLimits, 0)
            check(textureLimits[0] > 0) { "GL_MAX_TEXTURE_SIZE was not reported" }
            maxTextureSize = textureLimits[0]

            leftMesh.initializeGl()
            frontMesh.initializeGl()
            mirroredLeftMesh.initializeGl()
            mirroredFrontMesh.initializeGl()
            rightMesh.initializeGl()
            for (texture in textureCache.values) {
                texture.resetGl()
            }
            glReady = true
            firstFrameDrawn = false
            publishCapabilities()
            rehydrateRetainedDecks()
        } catch (exception: RuntimeException) {
            glReady = false
            reportFailure(
                activeGeneration(),
                false,
                RenderFailureReason.SHADER,
                "Could not initialize PlayLikeCurl GLES2 renderer",
                exception
            )
        }
    }

    override fun onSurfaceChanged(ignored: GL10?, width: Int, height: Int) {
        setViewport(width, height)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(ignored: GL10?) {
        if (disposed || !glReady || activeDeck == null) return
        try {
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glUniform1i(textureUniform, 0)
            GLES20.glUniform1i(overlayTextureUniform, 1)
            GLES20.glUniform1i(backTextureUniform, 2)

            if (landscapeSpreadModel != null && viewportWidth > viewportHeight) {
                drawLandscapeSpread()
            } else if (portraitModel != null) {
                drawPortraitPage()
            }

            if (!firstFrameDrawn) {
                firstFrameDrawn = true
                events.onFirstFrameRendered()
            }
        } catch (exception: RuntimeException) {
            reportFailure(
                activeGeneration(),
                true,
                RenderFailureReason.CONTEXT,
                "Could not render page frame",
                exception
            )
        }
    }

    private fun applyActiveDeck(deck: PageDeck<Bitmap>) {
        when (deck) {
            is PortraitPageDeck -> {
                portraitLeftResource = deck.previous
                portraitFrontResource = deck.current
                portraitRightResource = deck.next
                portraitModel = PlayLikeCurlModel(3, 1)
                landscapeSpreadModel = null
                clearSpreadResources()
            }
            is LandscapePageDeck -> {
                spreadPreviousLeftResource = deck.previousLeft
                spreadPreviousRightResource = deck.previousRight
                spreadCurrentLeftResource = deck.currentLeft
                spreadCurrentRightResource = deck.currentRight
                spreadNextLeftResource = deck.nextLeft
                spreadNextRightResource = deck.nextRight
                landscapeSpreadModel = LandscapeSpreadModel(6, 2)
                portraitModel = null
                clearPortraitResources()
            }
            else -> throw IllegalArgumentException("Unsupported page deck type")
        }
    }

    private fun clearActiveDeck() {
        portraitModel = null
        landscapeSpreadModel = null
        clearPortraitResources()
        clearSpreadResources()
    }

    private fun clearPortraitResources() {
        portraitLeftResource = null
        portraitFrontResource = null
        portraitRightResource = null
    }

    private fun clearSpreadResources() {
        spreadPreviousLeftResource = null
        spreadPreviousRightResource = null
        spreadCurrentLeftResource = null
        spreadCurrentRightResource = null
        spreadNextLeftResource = null
        spreadNextRightResource = null
    }

    private fun validateDeck(deck: PageDeck<Bitmap>?) {
        requireNotNull(deck) { "deck must not be null" }
        for (page in deck.pages) {
            val bitmap = page.content
            require(!bitmap.isRecycled) { "Bitmap is recycled for ${page.logicalPageId}" }
            require(bitmap.width == page.widthPx && bitmap.height == page.heightPx) {
                "Bitmap dimensions differ from PageImage metadata for ${page.logicalPageId}"
            }
            require(bitmap.config == Bitmap.Config.ARGB_8888) {
                "Bitmap must use ARGB_8888 for ${page.logicalPageId}"
            }
            require(!bitmap.hasAlpha()) {
                "Bitmap must be composited onto an opaque page background for ${page.logicalPageId}"
            }
            val overlay = page.overlayContent
            if (overlay != null) {
                require(!overlay.isRecycled) { "Overlay bitmap is recycled for ${page.logicalPageId}" }
                require(overlay.width == page.widthPx && overlay.height == page.heightPx) {
                    "Overlay dimensions differ from PageImage metadata for ${page.logicalPageId}"
                }
                require(overlay.config == Bitmap.Config.ARGB_8888) {
                    "Overlay bitmap must use ARGB_8888 for ${page.logicalPageId}"
                }
                require(overlay.isPremultiplied && overlay.hasAlpha()) {
                    "Overlay bitmap must be premultiplied and retain alpha for ${page.logicalPageId}"
                }
            }
        }
    }

    private fun publishCapabilities() {
        if (maxTextureSize > 0 && !disposed) {
            events.onCapabilitiesAvailable(RenderCapabilities(maxTextureSize, gpuBudgetBytes))
        }
    }

    private fun reportBudgetFailure(generationId: Long, budget: TextureBudget.Result) {
        val reason = budget.failureReason
        val message = when (reason) {
            RenderFailureReason.TEXTURE_TOO_LARGE -> "Page texture exceeds the device texture-size limit"
            RenderFailureReason.GPU_BUDGET_EXCEEDED -> "Page decks exceed the configured GPU byte budget"
            else -> throw IllegalArgumentException("Unsupported budget failure $reason")
        }
        events.onRenderFailure(
            RenderFailure(
                generationId,
                true,
                reason,
                message,
                null,
                budget.requestedWidthPx,
                budget.requestedHeightPx,
                budget.maxTextureSize,
                budget.requiredBytes,
                budget.gpuBudgetBytes
            )
        )
    }

    private fun uploadDeck(deck: PageDeck<Bitmap>) {
        for (page in deck.pages) {
            var texture = textureCache[page.identityKey()]
            if (texture == null) {
                texture = GpuTexture(page, false)
                textureCache[page.identityKey()] = texture
            }
            texture.ensureUploaded()
            if (page.hasOverlay) {
                var overlay = textureCache[page.overlayIdentityKey()]
                if (overlay == null) {
                    overlay = GpuTexture(page, true)
                    textureCache[page.overlayIdentityKey()] = overlay
                }
                overlay.ensureUploaded()
            }
        }
    }

    private fun rehydrateRetainedDecks() {
        val retainedActive = activeDeck
        if (retainedActive != null) {
            rehydrateDeck(retainedActive, retainedActive, null)
        }
        val retainedReplacement = replacementDeck
        if (retainedReplacement != null) {
            rehydrateDeck(retainedReplacement, activeDeck, retainedReplacement)
        }
    }

    private fun rehydrateDeck(
        deck: PageDeck<Bitmap>,
        prospectiveActive: PageDeck<Bitmap>?,
        prospectivePending: PageDeck<Bitmap>?
    ): Boolean {
        try {
            validateDeck(deck)
        } catch (exception: RuntimeException) {
            reportFailure(
                deck.generationId,
                true,
                RenderFailureReason.BITMAP,
                "Retained page bitmap is no longer valid",
                exception
            )
            releaseDeck(deck.generationId, DeckReleaseReason.FAILED)
            return false
        }

        val budget = TextureBudget.evaluate(
            prospectiveActive,
            prospectivePending,
            maxTextureSize,
            gpuBudgetBytes
        )
        if (budget.failureReason != null) {
            reportBudgetFailure(deck.generationId, budget)
            releaseDeck(deck.generationId, DeckReleaseReason.FAILED)
            return false
        }

        try {
            uploadDeck(deck)
            events.onDeckPrepared(deck.generationId)
            return true
        } catch (exception: RuntimeException) {
            reportFailure(
                deck.generationId,
                true,
                RenderFailureReason.TEXTURE_UPLOAD,
                "Could not restore page textures after GL context recreation",
                exception
            )
            releaseDeck(deck.generationId, DeckReleaseReason.FAILED)
            return false
        }
    }

    private fun retainDeckTextures() {
        val retainedKeys = LinkedHashSet<String>()
        collectDeckKeys(activeDeck, retainedKeys)
        collectDeckKeys(replacementDeck, retainedKeys)
        val iterator = textureCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!retainedKeys.contains(entry.key)) {
                entry.value.deleteGl()
                iterator.remove()
            }
        }
        registerDeck(activeDeck)
        registerDeck(replacementDeck)
    }

    private fun registerDeck(deck: PageDeck<Bitmap>?) {
        if (deck == null) return
        for (page in deck.pages) {
            textureCache.getOrPut(page.identityKey()) { GpuTexture(page, false) }
            if (page.hasOverlay) {
                textureCache.getOrPut(page.overlayIdentityKey()) { GpuTexture(page, true) }
            }
        }
    }

    private fun collectDeckKeys(deck: PageDeck<Bitmap>?, keys: MutableSet<String>) {
        if (deck == null) return
        for (page in deck.pages) {
            keys.add(page.identityKey())
            if (page.hasOverlay) {
                keys.add(page.overlayIdentityKey())
            }
        }
    }

    private fun drawPortraitPage() {
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        updateMvp(viewportWidth, viewportHeight)
        val model = portraitModel ?: return
        if (model.activePage == ActivePage.LEFT) {
            drawPage(
                rightMesh,
                portraitRightResource,
                model.rightPage,
                false,
                PageOrientation.PORTRAIT
            )
            drawPage(
                frontMesh,
                portraitFrontResource,
                model.frontPage,
                false,
                PageOrientation.PORTRAIT
            )
            drawMovingPage(
                leftMesh,
                portraitLeftResource,
                model.leftPage,
                PageOrientation.PORTRAIT
            )
            return
        }
        drawPage(
            leftMesh,
            portraitLeftResource,
            model.leftPage,
            false,
            PageOrientation.PORTRAIT
        )
        drawPage(
            rightMesh,
            portraitRightResource,
            model.rightPage,
            false,
            PageOrientation.PORTRAIT
        )
        drawMovingPage(
            frontMesh,
            portraitFrontResource,
            model.frontPage,
            PageOrientation.PORTRAIT
        )
    }

    /** Single-leaf book turn for two-page landscape spreads. */
    private fun drawLandscapeSpread() {
        val model = landscapeSpreadModel ?: return
        val leftWidth = viewportWidth / 2
        val rightWidth = viewportWidth - leftWidth
        val transition = model.transition

        if (transition.progress == 0f) {
            drawFlatLeaf(0, leftWidth + 1, spreadCurrentLeftResource)
            drawFlatLeaf(leftWidth, rightWidth, spreadCurrentRightResource)
            return
        }

        val progress = transition.progress
        if (transition.isForward) {
            drawFlatLeaf(0, leftWidth + 1, spreadCurrentLeftResource)
            drawFlatLeaf(leftWidth, rightWidth, spreadNextRightResource)
            drawLandscapeTurningLeaf(
                spreadCurrentRightResource,
                spreadNextLeftResource,
                progress,
                true
            )
        } else {
            drawFlatLeaf(leftWidth, rightWidth, spreadCurrentRightResource)
            drawFlatLeaf(0, leftWidth + 1, spreadPreviousLeftResource)
            drawLandscapeTurningLeaf(
                spreadCurrentLeftResource,
                spreadPreviousRightResource,
                progress,
                false
            )
        }
    }

    private fun drawLandscapeTurningLeaf(
        frontResource: PageImage<Bitmap>?,
        backResource: PageImage<Bitmap>?,
        progress: Float,
        forward: Boolean
    ) {
        val frontTex = texture(frontResource) ?: return
        val backTex = texture(backResource)

        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        updateMvp(viewportWidth, viewportHeight)

        frontMesh.ensureGeometry(frontTex.bitmapWidth, frontTex.bitmapHeight, PageOrientation.PORTRAIT)
        PlayLikeBezierCurl.updateLandscape(frontMesh.geometry, progress, forward)
        frontMesh.uploadPositions()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(textureUniform, 0)
        GLES20.glUniform1i(overlayTextureUniform, 1)
        GLES20.glUniform1i(backTextureUniform, 2)
        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, mvpMatrix, 0)

        drawPageTextures(frontResource, frontTex, backTex)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, frontMesh.positionBufferId)
        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, frontMesh.textureBufferId)
        GLES20.glEnableVertexAttribArray(textureCoordinateAttribute)
        GLES20.glVertexAttribPointer(textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, frontMesh.indexBufferId)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            frontMesh.geometry.indices.size,
            GLES20.GL_UNSIGNED_SHORT,
            0
        )

        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glDisableVertexAttribArray(textureCoordinateAttribute)
        GLES20.glUniform1f(hasBackTextureUniform, 0f)
    }

    private fun drawFlatLeaf(x: Int, width: Int, resource: PageImage<Bitmap>?) {
        drawLeaf(x, width, resource, rightMesh, flatState, false)
    }

    private fun drawLeaf(
        x: Int,
        width: Int,
        resource: PageImage<Bitmap>?,
        mesh: GpuMesh,
        state: PageState,
        active: Boolean
    ) {
        GLES20.glViewport(x, 0, width, viewportHeight)
        updateMvp(width, viewportHeight)
        if (active) {
            drawMovingPage(mesh, resource, state, PageOrientation.PORTRAIT)
        } else {
            drawPage(mesh, resource, state, false, PageOrientation.PORTRAIT)
        }
    }

    private fun drawMovingPage(
        mesh: GpuMesh,
        resource: PageImage<Bitmap>?,
        state: PageState,
        orientation: PageOrientation
    ) {
        drawFoldShadow(mesh, resource, state, orientation)
        drawPage(mesh, resource, state, true, orientation)
    }

    private fun drawFoldShadow(
        mesh: GpuMesh,
        resource: PageImage<Bitmap>?,
        state: PageState,
        orientation: PageOrientation
    ) {
        val texture = texture(resource) ?: return
        val shadow = FoldShadowModel.resolve(
            mesh.role, state.curlPosition, mesh.horizontallyMirrored
        )
        if (shadow.opacity <= 0.001f) return

        val bitmapRatio = PlayLikeCurlGeometry.bitmapRatio(
            texture.bitmapWidth, texture.bitmapHeight, orientation
        )
        val heightCorrection = (bitmapRatio - 1f) / 2f
        val bottom = -heightCorrection
        val top = bitmapRatio - heightCorrection
        shadowPositionBuffer.clear()
        shadowPositionBuffer.put(
            floatArrayOf(
                shadow.startX, bottom, FoldShadowModel.SHADOW_DEPTH,
                shadow.endX, bottom, FoldShadowModel.SHADOW_DEPTH,
                shadow.startX, top, FoldShadowModel.SHADOW_DEPTH,
                shadow.endX, top, FoldShadowModel.SHADOW_DEPTH
            )
        ).position(0)
        shadowGradientBuffer.clear()
        shadowGradientBuffer.put(
            if (shadow.isDarkAtStart) {
                floatArrayOf(0f, 1f, 0f, 1f)
            } else {
                floatArrayOf(1f, 0f, 1f, 0f)
            }
        ).position(0)
        shadowIndexBuffer.clear()
        shadowIndexBuffer.put(SHADOW_INDICES).position(0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glUseProgram(shadowProgram)
        GLES20.glUniformMatrix4fv(shadowMatrixUniform, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(shadowOpacityUniform, shadow.opacity)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnableVertexAttribArray(shadowPositionAttribute)
        GLES20.glVertexAttribPointer(
            shadowPositionAttribute, 3, GLES20.GL_FLOAT, false, 0, shadowPositionBuffer
        )
        GLES20.glEnableVertexAttribArray(shadowGradientAttribute)
        GLES20.glVertexAttribPointer(
            shadowGradientAttribute, 1, GLES20.GL_FLOAT, false, 0, shadowGradientBuffer
        )
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            SHADOW_INDICES.size,
            GLES20.GL_UNSIGNED_SHORT,
            shadowIndexBuffer
        )
        GLES20.glDisableVertexAttribArray(shadowPositionAttribute)
        GLES20.glDisableVertexAttribArray(shadowGradientAttribute)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(textureUniform, 0)
        GLES20.glUniform1i(overlayTextureUniform, 1)
    }

    private fun drawPage(
        mesh: GpuMesh,
        resource: PageImage<Bitmap>?,
        state: PageState,
        active: Boolean,
        orientation: PageOrientation
    ) {
        val texture = texture(resource) ?: return
        mesh.ensureGeometry(texture.bitmapWidth, texture.bitmapHeight, orientation)
        PlayLikeCurlGeometry.update(mesh.geometry, state.curlPosition, active)
        mesh.uploadPositions()

        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(hasBackTextureUniform, 0f)
        drawPageTextures(resource, texture)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.positionBufferId)
        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.textureBufferId)
        GLES20.glEnableVertexAttribArray(textureCoordinateAttribute)
        GLES20.glVertexAttribPointer(textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBufferId)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            mesh.geometry.indices.size,
            GLES20.GL_UNSIGNED_SHORT,
            0
        )
        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glDisableVertexAttribArray(textureCoordinateAttribute)
    }

    private fun drawPageTextures(
        resource: PageImage<Bitmap>?,
        baseTexture: GpuTexture,
        backTexture: GpuTexture? = null
    ) {
        val overlayTexture = overlayTexture(resource)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, baseTexture.textureId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            overlayTexture?.textureId ?: 0
        )
        GLES20.glUniform1f(hasOverlayUniform, if (overlayTexture == null) 0f else 1f)

        if (backTexture != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backTexture.textureId)
            GLES20.glUniform1f(hasBackTextureUniform, 1f)
        } else {
            GLES20.glUniform1f(hasBackTextureUniform, 0f)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private fun texture(page: PageImage<Bitmap>?): GpuTexture? {
        if (page == null) return null
        val texture = textureCache[page.identityKey()]
        return if (texture != null && texture.uploaded) texture else null
    }

    private fun overlayTexture(page: PageImage<Bitmap>?): GpuTexture? {
        if (page == null || !page.hasOverlay) return null
        val texture = textureCache[page.overlayIdentityKey()]
        return if (texture != null && texture.uploaded) texture else null
    }

    private fun updateMvp(width: Int, height: Int) {
        val aspect = width / height.toFloat()
        val zNear = 0.1f
        val zFar = 100f
        val left = -0.5f * (zNear / PlayLikeCurlGeometry.CAMERA_DISTANCE)
        val right = 0.5f * (zNear / PlayLikeCurlGeometry.CAMERA_DISTANCE)
        val bottom = -(0.5f / aspect) * (zNear / PlayLikeCurlGeometry.CAMERA_DISTANCE)
        val top = (0.5f / aspect) * (zNear / PlayLikeCurlGeometry.CAMERA_DISTANCE)
        Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, zNear, zFar)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -PlayLikeCurlGeometry.CAMERA_DISTANCE)
        Matrix.translateM(modelMatrix, 0, -0.5f, -0.5f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
    }

    private fun activeGeneration(): Long {
        return activeDeck?.generationId ?: -1L
    }

    private fun reportFailure(
        generationId: Long,
        recoverable: Boolean,
        reason: RenderFailureReason,
        message: String,
        cause: Throwable?
    ) {
        events.onRenderFailure(
            RenderFailure(generationId, recoverable, reason, message, cause)
        )
    }

    private inner class GpuTexture(
        val page: PageImage<Bitmap>,
        val overlay: Boolean
    ) {
        var textureId = 0
        var bitmapWidth = 0
        var bitmapHeight = 0
        var uploaded = false

        fun resetGl() {
            textureId = 0
            uploaded = false
        }

        fun ensureUploaded() {
            if (uploaded) return
            val bitmap = if (overlay) page.overlayContent else page.content
            checkNotNull(bitmap) { "Overlay bitmap is missing for ${page.logicalPageId}" }
            check(!bitmap.isRecycled) {
                "${if (overlay) "Overlay bitmap" else "Bitmap"} was recycled for ${page.logicalPageId}"
            }
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
            bitmapWidth = bitmap.width
            bitmapHeight = bitmap.height
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                deleteGl()
                throw IllegalStateException("Texture upload failed with GLES error $error")
            }
            uploaded = true
        }

        fun deleteGl() {
            if (textureId != 0) {
                val ids = intArrayOf(textureId)
                GLES20.glDeleteTextures(1, ids, 0)
            }
            textureId = 0
            uploaded = false
        }
    }

    private inner class GpuMesh(
        val role: PageRole,
        val horizontallyMirrored: Boolean = false
    ) {
        val positionBuffer: FloatBuffer
        val textureBuffer: FloatBuffer
        val indexBuffer: ShortBuffer
        private val bufferIds = IntArray(3)
        var geometry: PageGeometry = PlayLikeCurlGeometry.createPage(role, 1, 1, PageOrientation.PORTRAIT)
            private set
        private var geometryWidth = -1
        private var geometryHeight = -1
        private var geometryOrientation: PageOrientation? = null
        var positionBufferId = 0
            private set
        var textureBufferId = 0
            private set
        var indexBufferId = 0
            private set

        init {
            if (horizontallyMirrored) {
                mirrorTextureCoordinates(geometry.textureCoordinates)
            }
            positionBuffer = directFloatBuffer(geometry.positions.size)
            textureBuffer = directFloatBuffer(geometry.textureCoordinates.size)
            indexBuffer = directShortBuffer(geometry.indices.size)
        }

        fun initializeGl() {
            dispose()
            GLES20.glGenBuffers(bufferIds.size, bufferIds, 0)
            positionBufferId = bufferIds[0]
            textureBufferId = bufferIds[1]
            indexBufferId = bufferIds[2]

            textureBuffer.clear()
            textureBuffer.put(geometry.textureCoordinates).position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureBufferId)
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                geometry.textureCoordinates.size * java.lang.Float.BYTES,
                textureBuffer,
                GLES20.GL_STATIC_DRAW
            )

            indexBuffer.clear()
            indexBuffer.put(geometry.indices).position(0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
            GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                geometry.indices.size * java.lang.Short.BYTES,
                indexBuffer,
                GLES20.GL_STATIC_DRAW
            )

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId)
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                geometry.positions.size * java.lang.Float.BYTES,
                null,
                GLES20.GL_DYNAMIC_DRAW
            )
            geometryWidth = -1
            geometryHeight = -1
            geometryOrientation = null
        }

        fun ensureGeometry(width: Int, height: Int, orientation: PageOrientation) {
            if (width == geometryWidth && height == geometryHeight && orientation == geometryOrientation) {
                return
            }
            geometry = PlayLikeCurlGeometry.createPage(role, width, height, orientation)
            if (horizontallyMirrored) {
                mirrorTextureCoordinates(geometry.textureCoordinates)
            }
            geometryWidth = width
            geometryHeight = height
            geometryOrientation = orientation
        }

        fun uploadPositions() {
            val positions = geometry.positions
            positionBuffer.clear()
            if (horizontallyMirrored) {
                var offset = 0
                while (offset < positions.size) {
                    positionBuffer.put(1f - positions[offset])
                    positionBuffer.put(positions[offset + 1])
                    positionBuffer.put(positions[offset + 2])
                    offset += 3
                }
            } else {
                positionBuffer.put(positions)
            }
            positionBuffer.position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId)
            GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER,
                0,
                positions.size * java.lang.Float.BYTES,
                positionBuffer
            )
        }

        fun dispose() {
            if (positionBufferId != 0 || textureBufferId != 0 || indexBufferId != 0) {
                val ids = intArrayOf(positionBufferId, textureBufferId, indexBufferId)
                GLES20.glDeleteBuffers(ids.size, ids, 0)
            }
            positionBufferId = 0
            textureBufferId = 0
            indexBufferId = 0
        }
    }

    companion object {
        private const val VERTEX_SHADER =
            "uniform mat4 uMvpMatrix;\n" +
                "attribute vec3 aPosition;\n" +
                "attribute vec2 aTextureCoordinate;\n" +
                "varying vec2 vTextureCoordinate;\n" +
                "varying float vDepth;\n" +
                "void main() {\n" +
                "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);\n" +
                "  vTextureCoordinate = aTextureCoordinate;\n" +
                "  vDepth = aPosition.z;\n" +
                "}\n"

        private const val FRAGMENT_SHADER =
            "precision mediump float;\n" +
                "uniform sampler2D uTexture;\n" +
                "uniform sampler2D uOverlayTexture;\n" +
                "uniform float uHasOverlay;\n" +
                "uniform sampler2D uBackTexture;\n" +
                "uniform float uHasBackTexture;\n" +
                "varying vec2 vTextureCoordinate;\n" +
                "varying float vDepth;\n" +
                "void main() {\n" +
                "  bool isBack = (!gl_FrontFacing && uHasBackTexture > 0.5);\n" +
                "  vec2 texCoord = isBack ? vec2(1.0 - vTextureCoordinate.x, vTextureCoordinate.y) : vTextureCoordinate;\n" +
                "  vec4 base = isBack ? texture2D(uBackTexture, texCoord) : texture2D(uTexture, texCoord);\n" +
                "  vec4 overlay = texture2D(uOverlayTexture, texCoord);\n" +
                "  vec4 color = mix(base, overlay + base * (1.0 - overlay.a), isBack ? 0.0 : uHasOverlay);\n" +
                "  if (vDepth > 0.0) {\n" +
                "    float lift = clamp(vDepth / 0.28, 0.0, 1.0);\n" +
                "    float crest = sin(lift * 3.14159265);\n" +
                "    float smoothFactor = smoothstep(0.0, 0.03, vDepth);\n" +
                "    float targetLight = gl_FrontFacing ? (0.96 + 0.08 * crest) : (0.92 + 0.06 * crest);\n" +
                "    float light = mix(1.0, targetLight, smoothFactor);\n" +
                "    color.rgb *= light;\n" +
                "  }\n" +
                "  gl_FragColor = color;\n" +
                "}\n"

        private const val SHADOW_VERTEX_SHADER =
            "uniform mat4 uMvpMatrix;\n" +
                "attribute vec3 aPosition;\n" +
                "attribute float aGradient;\n" +
                "varying float vGradient;\n" +
                "void main() {\n" +
                "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);\n" +
                "  vGradient = aGradient;\n" +
                "}\n"

        private const val SHADOW_FRAGMENT_SHADER =
            "precision mediump float;\n" +
                "uniform float uOpacity;\n" +
                "varying float vGradient;\n" +
                "void main() {\n" +
                "  float falloff = 1.0 - smoothstep(0.0, 1.0, vGradient);\n" +
                "  gl_FragColor = vec4(0.0, 0.0, 0.0, uOpacity * falloff);\n" +
                "}\n"

        private val SHADOW_INDICES = shortArrayOf(0, 1, 2, 2, 1, 3)
        private const val DEFAULT_GPU_BUDGET_BYTES = 128L * 1024L * 1024L

        private fun mirrorTextureCoordinates(coordinates: FloatArray) {
            var offset = 0
            while (offset < coordinates.size) {
                coordinates[offset] = 1f - coordinates[offset]
                offset += 2
            }
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val createdProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(createdProgram, vertexShader)
            GLES20.glAttachShader(createdProgram, fragmentShader)
            GLES20.glLinkProgram(createdProgram)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(createdProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val log = GLES20.glGetProgramInfoLog(createdProgram)
                GLES20.glDeleteProgram(createdProgram)
                throw IllegalStateException("Could not link PlayLikeCurl GLES2 program: $log")
            }
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return createdProgram
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] != GLES20.GL_TRUE) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw IllegalStateException("Could not compile PlayLikeCurl GLES2 shader: $log")
            }
            return shader
        }

        private fun directFloatBuffer(size: Int): FloatBuffer {
            return ByteBuffer.allocateDirect(size * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        }

        private fun directShortBuffer(size: Int): ShortBuffer {
            return ByteBuffer.allocateDirect(size * java.lang.Short.BYTES)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
        }
    }
}
