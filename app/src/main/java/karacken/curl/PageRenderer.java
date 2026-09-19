package karacken.curl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** GLES2 renderer for client-prepared page bitmaps and PlayLikeCurl deformation. */
public final class PageRenderer implements GLSurfaceView.Renderer {
    interface Events {
        void onCapabilitiesAvailable(RenderCapabilities capabilities);

        void onFirstFrameRendered();

        void onDeckPrepared(long generationId);

        void onDeckReleased(long generationId, DeckReleaseReason reason);

        void onRenderFailure(RenderFailure failure);
    }

    private static final String VERTEX_SHADER =
            "uniform mat4 uMvpMatrix;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute vec2 aTextureCoordinate;\n"
                    + "varying vec2 vTextureCoordinate;\n"
                    + "varying float vDepth;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);\n"
                    + "  vTextureCoordinate = aTextureCoordinate;\n"
                    + "  vDepth = aPosition.z;\n"
                    + "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform sampler2D uTexture;\n"
                    + "uniform sampler2D uOverlayTexture;\n"
                    + "uniform float uHasOverlay;\n"
                    + "uniform sampler2D uBackTexture;\n"
                    + "uniform float uHasBackTexture;\n"
                    + "varying vec2 vTextureCoordinate;\n"
                    + "varying float vDepth;\n"
                    + "void main() {\n"
                    + "  bool isBack = (!gl_FrontFacing && uHasBackTexture > 0.5);\n"
                    + "  vec2 texCoord = isBack ? vec2(1.0 - vTextureCoordinate.x, vTextureCoordinate.y) : vTextureCoordinate;\n"
                    + "  vec4 base = isBack ? texture2D(uBackTexture, texCoord) : texture2D(uTexture, texCoord);\n"
                    + "  vec4 overlay = texture2D(uOverlayTexture, texCoord);\n"
                    + "  vec4 color = mix(base, overlay + base * (1.0 - overlay.a), isBack ? 0.0 : uHasOverlay);\n"
                    + "  if (vDepth > 0.0) {\n"
                    + "    float lift = clamp(vDepth / 0.28, 0.0, 1.0);\n"
                    + "    float crest = sin(lift * 3.14159265);\n"
                    + "    float smoothFactor = smoothstep(0.0, 0.03, vDepth);\n"
                    + "    float targetLight = gl_FrontFacing ? (0.96 + 0.08 * crest) : (0.92 + 0.06 * crest);\n"
                    + "    float light = mix(1.0, targetLight, smoothFactor);\n"
                    + "    color.rgb *= light;\n"
                    + "  }\n"
                    + "  gl_FragColor = color;\n"
                    + "}\n";

    private static final String SHADOW_VERTEX_SHADER =
            "uniform mat4 uMvpMatrix;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute float aGradient;\n"
                    + "varying float vGradient;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);\n"
                    + "  vGradient = aGradient;\n"
                    + "}\n";

    private static final String SHADOW_FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform float uOpacity;\n"
                    + "varying float vGradient;\n"
                    + "void main() {\n"
                    + "  float falloff = 1.0 - smoothstep(0.0, 1.0, vGradient);\n"
                    + "  gl_FragColor = vec4(0.0, 0.0, 0.0, uOpacity * falloff);\n"
                    + "}\n";

    private static final short[] SHADOW_INDICES = {0, 1, 2, 2, 1, 3};
    private static final long DEFAULT_GPU_BUDGET_BYTES = 128L * 1024L * 1024L;

    private final Events events;
    private final GpuMesh leftMesh = new GpuMesh(PageRole.LEFT);
    private final GpuMesh frontMesh = new GpuMesh(PageRole.FRONT);
    private final GpuMesh mirroredLeftMesh = new GpuMesh(PageRole.LEFT, true);
    private final GpuMesh mirroredFrontMesh = new GpuMesh(PageRole.FRONT, true);
    private final GpuMesh rightMesh = new GpuMesh(PageRole.RIGHT);
    private final Map<String, GpuTexture> textureCache = new LinkedHashMap<>();
    private final PageState flatState = new PageState(
            PageRole.RIGHT, PlayLikeCurlModel.RIGHT_DEPTH, PlayLikeCurlModel.GRID, 0);
    private final PageState turningState = new PageState(
            PageRole.FRONT, PlayLikeCurlModel.FRONT_DEPTH, PlayLikeCurlModel.GRID, 0);
    private final float[] projectionMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];
    private final FloatBuffer shadowPositionBuffer = directFloatBuffer(12);
    private final FloatBuffer shadowGradientBuffer = directFloatBuffer(4);
    private final ShortBuffer shadowIndexBuffer = directShortBuffer(SHADOW_INDICES.length);

    private PlayLikeCurlModel portraitModel;
    private LandscapeSpreadModel landscapeSpreadModel;
    private PageDeck<Bitmap> activeDeck;
    private PageDeck<Bitmap> replacementDeck;
    private PageImage<Bitmap> portraitLeftResource;
    private PageImage<Bitmap> portraitFrontResource;
    private PageImage<Bitmap> portraitRightResource;
    private PageImage<Bitmap> spreadPreviousLeftResource;
    private PageImage<Bitmap> spreadPreviousRightResource;
    private PageImage<Bitmap> spreadCurrentLeftResource;
    private PageImage<Bitmap> spreadCurrentRightResource;
    private PageImage<Bitmap> spreadNextLeftResource;
    private PageImage<Bitmap> spreadNextRightResource;
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private int program;
    private int positionAttribute;
    private int textureCoordinateAttribute;
    private int matrixUniform;
    private int textureUniform;
    private int overlayTextureUniform;
    private int hasOverlayUniform;
    private int backTextureUniform;
    private int hasBackTextureUniform;
    private int shadowProgram;
    private int shadowPositionAttribute;
    private int shadowGradientAttribute;
    private int shadowMatrixUniform;
    private int shadowOpacityUniform;
    private int maxTextureSize;
    private long gpuBudgetBytes = DEFAULT_GPU_BUDGET_BYTES;
    private boolean glReady;
    private boolean disposed;
    private boolean firstFrameDrawn;
    // Bunko: paper background clear color so letterbox margins and any sub-pixel
    // seams between spread halves match the reader paper instead of black.
    private float clearRed = 1f;
    private float clearGreen = 1f;
    private float clearBlue = 1f;

    PageRenderer(Events events) {
        this(events, 0xFFFFFFFF);
    }

    PageRenderer(Events events, int initialPaperColor) {
        this.events = events;
        setInitialBackgroundColor(
                (initialPaperColor >> 16) & 0xFF,
                (initialPaperColor >> 8) & 0xFF,
                initialPaperColor & 0xFF);
    }

    void setInitialBackgroundColor(int red, int green, int blue) {
        clearRed = Math.max(0, Math.min(255, red)) / 255f;
        clearGreen = Math.max(0, Math.min(255, green)) / 255f;
        clearBlue = Math.max(0, Math.min(255, blue)) / 255f;
    }

    void prepareDeck(PageDeck<Bitmap> deck, boolean activateWhenPrepared) {
        if (disposed) {
            reportFailure(
                    deck.getGenerationId(),
                    false,
                    RenderFailureReason.DISPOSED,
                    "Renderer is disposed",
                    null);
            events.onDeckReleased(deck.getGenerationId(), DeckReleaseReason.FAILED);
            return;
        }
        boolean retained = false;
        try {
            validateDeck(deck);
            PageDeck<Bitmap> prospectiveActive =
                    activateWhenPrepared ? deck : activeDeck;
            PageDeck<Bitmap> prospectivePending =
                    activateWhenPrepared ? null : deck;
            TextureBudget.Result budget = TextureBudget.evaluate(
                    prospectiveActive,
                    prospectivePending,
                    maxTextureSize,
                    gpuBudgetBytes);
            if (budget.getFailureReason() != null) {
                reportBudgetFailure(deck.getGenerationId(), budget);
                events.onDeckReleased(deck.getGenerationId(), DeckReleaseReason.FAILED);
                return;
            }
            if (!activateWhenPrepared) {
                replacementDeck = deck;
            } else {
                replacementDeck = deck; // Retain active deck textures while uploading new ones
            }
            retained = true;
            retainDeckTextures();
            if (glReady) {
                uploadDeck(deck);
                if (activateWhenPrepared) {
                    PageDeck<Bitmap> oldActive = activeDeck;
                    activeDeck = deck;
                    replacementDeck = null;
                    applyActiveDeck(deck);
                    retainDeckTextures();
                    if (oldActive != null && oldActive.getGenerationId() != deck.getGenerationId()) {
                        events.onDeckReleased(oldActive.getGenerationId(), DeckReleaseReason.REPLACED);
                    }
                }
                events.onDeckPrepared(deck.getGenerationId());
            } else if (activateWhenPrepared) {
                activeDeck = deck;
                replacementDeck = null;
                applyActiveDeck(deck);
            }
        } catch (RuntimeException exception) {
            reportFailure(
                    deck.getGenerationId(),
                    true,
                    RenderFailureReason.BITMAP,
                    "Could not prepare page deck",
                    exception);
            if (retained) {
                releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED);
            } else {
                events.onDeckReleased(deck.getGenerationId(), DeckReleaseReason.FAILED);
            }
        }
    }

    void setGpuBudgetBytes(long gpuBudgetBytes) {
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }
        this.gpuBudgetBytes = gpuBudgetBytes;
        publishCapabilities();
    }

    void activateDeck(long generationId) {
        if (disposed) {
            return;
        }
        if (replacementDeck != null && replacementDeck.getGenerationId() == generationId) {
            PageDeck<Bitmap> releasedDeck = activeDeck;
            activeDeck = replacementDeck;
            replacementDeck = null;
            applyActiveDeck(activeDeck);
            retainDeckTextures();
            if (releasedDeck != null
                    && releasedDeck.getGenerationId() != activeDeck.getGenerationId()) {
                events.onDeckReleased(
                        releasedDeck.getGenerationId(),
                        DeckReleaseReason.REPLACED);
            }
        }
    }

    void commitTurn(PageChange pageChange) {
        if (disposed || activeDeck == null) {
            return;
        }
        if (pageChange == PageChange.NEXT) {
            if (portraitFrontResource != null && portraitRightResource != null) {
                portraitLeftResource = portraitFrontResource;
                portraitFrontResource = portraitRightResource;
            }
            if (spreadCurrentLeftResource != null && spreadNextLeftResource != null) {
                spreadPreviousLeftResource = spreadCurrentLeftResource;
                spreadPreviousRightResource = spreadCurrentRightResource;
                spreadCurrentLeftResource = spreadNextLeftResource;
                spreadCurrentRightResource = spreadNextRightResource;
            }
        } else if (pageChange == PageChange.PREVIOUS) {
            if (portraitFrontResource != null && portraitLeftResource != null) {
                portraitRightResource = portraitFrontResource;
                portraitFrontResource = portraitLeftResource;
            }
            if (spreadCurrentLeftResource != null && spreadPreviousLeftResource != null) {
                spreadNextLeftResource = spreadCurrentLeftResource;
                spreadNextRightResource = spreadCurrentRightResource;
                spreadCurrentLeftResource = spreadPreviousLeftResource;
                spreadCurrentRightResource = spreadPreviousRightResource;
            }
        }
    }

    PlayLikeCurlModel getPortraitModel() {
        return portraitModel;
    }

    LandscapeSpreadModel getLandscapeSpreadModel() {
        return landscapeSpreadModel;
    }

    void setViewport(int width, int height) {
        viewportWidth = Math.max(width, 1);
        viewportHeight = Math.max(height, 1);
    }

    /** Bunko: matches the GL clear color to the reader paper color (0-255 channels). */
    void setBackgroundColor(int red, int green, int blue) {
        clearRed = Math.max(0, Math.min(255, red)) / 255f;
        clearGreen = Math.max(0, Math.min(255, green)) / 255f;
        clearBlue = Math.max(0, Math.min(255, blue)) / 255f;
        if (glReady) {
            queueClearColor();
        }
    }

    private void queueClearColor() {
        final float r = clearRed;
        final float g = clearGreen;
        final float b = clearBlue;
        // Called on the GL thread during surface creation; direct call is safe there.
        GLES20.glClearColor(r, g, b, 1f);
    }

    void releaseDeck(long generationId, DeckReleaseReason reason) {
        boolean released = false;
        if (activeDeck != null && activeDeck.getGenerationId() == generationId) {
            activeDeck = null;
            clearActiveDeck();
            released = true;
        }
        if (replacementDeck != null && replacementDeck.getGenerationId() == generationId) {
            replacementDeck = null;
            released = true;
        }
        retainDeckTextures();
        if (released) {
            events.onDeckReleased(generationId, reason);
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        Set<Long> releasedGenerations = new LinkedHashSet<>();
        if (activeDeck != null) {
            releasedGenerations.add(activeDeck.getGenerationId());
        }
        if (replacementDeck != null) {
            releasedGenerations.add(replacementDeck.getGenerationId());
        }
        disposed = true;
        activeDeck = null;
        replacementDeck = null;
        clearActiveDeck();
        for (GpuTexture texture : textureCache.values()) {
            texture.deleteGl();
        }
        textureCache.clear();
        leftMesh.dispose();
        frontMesh.dispose();
        mirroredLeftMesh.dispose();
        mirroredFrontMesh.dispose();
        rightMesh.dispose();
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        if (shadowProgram != 0) {
            GLES20.glDeleteProgram(shadowProgram);
            shadowProgram = 0;
        }
        glReady = false;
        for (long generationId : releasedGenerations) {
            events.onDeckReleased(generationId, DeckReleaseReason.DISPOSED);
        }
    }

    /**
     * Drops every client bitmap reference after the GL thread has been paused.
     *
     * <p>This is the terminal fallback for a detached surface whose GL event queue can no longer
     * be relied upon to execute {@link #dispose()}. The EGL context owns any remaining GPU object
     * deletion when the terminal pause destroys that context.
     */
    void abandonClientState() {
        if (disposed) {
            return;
        }
        disposed = true;
        activeDeck = null;
        replacementDeck = null;
        clearActiveDeck();
        textureCache.clear();
        glReady = false;
    }

    @Override
    public void onSurfaceCreated(GL10 ignored, EGLConfig config) {
        if (disposed) {
            return;
        }
        try {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            positionAttribute = GLES20.glGetAttribLocation(program, "aPosition");
            textureCoordinateAttribute =
                    GLES20.glGetAttribLocation(program, "aTextureCoordinate");
            matrixUniform = GLES20.glGetUniformLocation(program, "uMvpMatrix");
            textureUniform = GLES20.glGetUniformLocation(program, "uTexture");
            overlayTextureUniform =
                    GLES20.glGetUniformLocation(program, "uOverlayTexture");
            hasOverlayUniform = GLES20.glGetUniformLocation(program, "uHasOverlay");
            backTextureUniform = GLES20.glGetUniformLocation(program, "uBackTexture");
            hasBackTextureUniform = GLES20.glGetUniformLocation(program, "uHasBackTexture");
            shadowProgram = createProgram(SHADOW_VERTEX_SHADER, SHADOW_FRAGMENT_SHADER);
            shadowPositionAttribute =
                    GLES20.glGetAttribLocation(shadowProgram, "aPosition");
            shadowGradientAttribute =
                    GLES20.glGetAttribLocation(shadowProgram, "aGradient");
            shadowMatrixUniform =
                    GLES20.glGetUniformLocation(shadowProgram, "uMvpMatrix");
            shadowOpacityUniform =
                    GLES20.glGetUniformLocation(shadowProgram, "uOpacity");

            GLES20.glClearColor(clearRed, clearGreen, clearBlue, 1f);
            GLES20.glClearDepthf(1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            int[] textureLimits = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, textureLimits, 0);
            if (textureLimits[0] <= 0) {
                throw new IllegalStateException(
                        "GL_MAX_TEXTURE_SIZE was not reported");
            }
            maxTextureSize = textureLimits[0];

            leftMesh.initializeGl();
            frontMesh.initializeGl();
            mirroredLeftMesh.initializeGl();
            mirroredFrontMesh.initializeGl();
            rightMesh.initializeGl();
            for (GpuTexture texture : textureCache.values()) {
                texture.resetGl();
            }
            glReady = true;
            firstFrameDrawn = false;
            publishCapabilities();
            rehydrateRetainedDecks();
        } catch (RuntimeException exception) {
            glReady = false;
            reportFailure(
                    activeGeneration(),
                    false,
                    RenderFailureReason.SHADER,
                    "Could not initialize PlayLikeCurl GLES2 renderer",
                    exception);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 ignored, int width, int height) {
        setViewport(width, height);
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
    }

    @Override
    public void onDrawFrame(GL10 ignored) {
        if (disposed || !glReady || activeDeck == null) {
            return;
        }
        try {
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glUniform1i(textureUniform, 0);
            GLES20.glUniform1i(overlayTextureUniform, 1);
            GLES20.glUniform1i(backTextureUniform, 2);

            if (landscapeSpreadModel != null && viewportWidth > viewportHeight) {
                drawLandscapeSpread();
            } else if (portraitModel != null) {
                drawPortraitPage();
            }

            if (!firstFrameDrawn) {
                firstFrameDrawn = true;
                events.onFirstFrameRendered();
            }
        } catch (RuntimeException exception) {
            reportFailure(
                    activeGeneration(),
                    true,
                    RenderFailureReason.CONTEXT,
                    "Could not render page frame",
                    exception);
        }
    }

    private void applyActiveDeck(PageDeck<Bitmap> deck) {
        if (deck instanceof PortraitPageDeck) {
            PortraitPageDeck<Bitmap> portrait = (PortraitPageDeck<Bitmap>) deck;
            portraitLeftResource = portrait.getPrevious();
            portraitFrontResource = portrait.getCurrent();
            portraitRightResource = portrait.getNext();
            portraitModel = new PlayLikeCurlModel(3, 1);
            landscapeSpreadModel = null;
            clearSpreadResources();
        } else if (deck instanceof LandscapePageDeck) {
            LandscapePageDeck<Bitmap> spread = (LandscapePageDeck<Bitmap>) deck;
            spreadPreviousLeftResource = spread.getPreviousLeft();
            spreadPreviousRightResource = spread.getPreviousRight();
            spreadCurrentLeftResource = spread.getCurrentLeft();
            spreadCurrentRightResource = spread.getCurrentRight();
            spreadNextLeftResource = spread.getNextLeft();
            spreadNextRightResource = spread.getNextRight();
            landscapeSpreadModel = new LandscapeSpreadModel(6, 2);
            portraitModel = null;
            clearPortraitResources();
        } else {
            throw new IllegalArgumentException("Unsupported page deck type");
        }
    }

    private void clearActiveDeck() {
        portraitModel = null;
        landscapeSpreadModel = null;
        clearPortraitResources();
        clearSpreadResources();
    }

    private void clearPortraitResources() {
        portraitLeftResource = null;
        portraitFrontResource = null;
        portraitRightResource = null;
    }

    private void clearSpreadResources() {
        spreadPreviousLeftResource = null;
        spreadPreviousRightResource = null;
        spreadCurrentLeftResource = null;
        spreadCurrentRightResource = null;
        spreadNextLeftResource = null;
        spreadNextRightResource = null;
    }

    private void validateDeck(PageDeck<Bitmap> deck) {
        if (deck == null) {
            throw new IllegalArgumentException("deck must not be null");
        }
        for (PageImage<Bitmap> page : deck.getPages()) {
            Bitmap bitmap = page.getContent();
            if (bitmap.isRecycled()) {
                throw new IllegalArgumentException(
                        "Bitmap is recycled for " + page.getLogicalPageId());
            }
            if (bitmap.getWidth() != page.getWidthPx()
                    || bitmap.getHeight() != page.getHeightPx()) {
                throw new IllegalArgumentException(
                        "Bitmap dimensions differ from PageImage metadata for "
                                + page.getLogicalPageId());
            }
            if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                throw new IllegalArgumentException(
                        "Bitmap must use ARGB_8888 for "
                                + page.getLogicalPageId());
            }
            if (bitmap.hasAlpha()) {
                throw new IllegalArgumentException(
                        "Bitmap must be composited onto an opaque page background for "
                                + page.getLogicalPageId());
            }
            Bitmap overlay = page.getOverlayContent();
            if (overlay != null) {
                if (overlay.isRecycled()) {
                    throw new IllegalArgumentException(
                            "Overlay bitmap is recycled for " + page.getLogicalPageId());
                }
                if (overlay.getWidth() != page.getWidthPx()
                        || overlay.getHeight() != page.getHeightPx()) {
                    throw new IllegalArgumentException(
                            "Overlay dimensions differ from PageImage metadata for "
                                    + page.getLogicalPageId());
                }
                if (overlay.getConfig() != Bitmap.Config.ARGB_8888) {
                    throw new IllegalArgumentException(
                            "Overlay bitmap must use ARGB_8888 for "
                                    + page.getLogicalPageId());
                }
                if (!overlay.isPremultiplied() || !overlay.hasAlpha()) {
                    throw new IllegalArgumentException(
                            "Overlay bitmap must be premultiplied and retain alpha for "
                                    + page.getLogicalPageId());
                }
            }
        }
    }

    private void publishCapabilities() {
        if (maxTextureSize > 0 && !disposed) {
            events.onCapabilitiesAvailable(
                    new RenderCapabilities(maxTextureSize, gpuBudgetBytes));
        }
    }

    private void reportBudgetFailure(
            long generationId,
            TextureBudget.Result budget) {
        RenderFailureReason reason = budget.getFailureReason();
        String message;
        if (reason == RenderFailureReason.TEXTURE_TOO_LARGE) {
            message = "Page texture exceeds the device texture-size limit";
        } else if (reason == RenderFailureReason.GPU_BUDGET_EXCEEDED) {
            message = "Page decks exceed the configured GPU byte budget";
        } else {
            throw new IllegalArgumentException("Unsupported budget failure " + reason);
        }
        events.onRenderFailure(
                new RenderFailure(
                        generationId,
                        true,
                        reason,
                        message,
                        null,
                        budget.getRequestedWidthPx(),
                        budget.getRequestedHeightPx(),
                        budget.getMaxTextureSize(),
                        budget.getRequiredBytes(),
                        budget.getGpuBudgetBytes()));
    }

    private void uploadDeck(PageDeck<Bitmap> deck) {
        for (PageImage<Bitmap> page : deck.getPages()) {
            GpuTexture texture = textureCache.get(page.identityKey());
            if (texture == null) {
                texture = new GpuTexture(page, false);
                textureCache.put(page.identityKey(), texture);
            }
            texture.ensureUploaded();
            if (page.hasOverlay()) {
                GpuTexture overlay = textureCache.get(page.overlayIdentityKey());
                if (overlay == null) {
                    overlay = new GpuTexture(page, true);
                    textureCache.put(page.overlayIdentityKey(), overlay);
                }
                overlay.ensureUploaded();
            }
        }
    }

    private void rehydrateRetainedDecks() {
        PageDeck<Bitmap> retainedActive = activeDeck;
        if (retainedActive != null) {
            rehydrateDeck(retainedActive, retainedActive, null);
        }
        PageDeck<Bitmap> retainedReplacement = replacementDeck;
        if (retainedReplacement != null) {
            rehydrateDeck(
                    retainedReplacement,
                    activeDeck,
                    retainedReplacement);
        }
    }

    private boolean rehydrateDeck(
            PageDeck<Bitmap> deck,
            PageDeck<Bitmap> prospectiveActive,
            PageDeck<Bitmap> prospectivePending) {
        try {
            validateDeck(deck);
        } catch (RuntimeException exception) {
            reportFailure(
                    deck.getGenerationId(),
                    true,
                    RenderFailureReason.BITMAP,
                    "Retained page bitmap is no longer valid",
                    exception);
            releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED);
            return false;
        }

        TextureBudget.Result budget = TextureBudget.evaluate(
                prospectiveActive,
                prospectivePending,
                maxTextureSize,
                gpuBudgetBytes);
        if (budget.getFailureReason() != null) {
            reportBudgetFailure(deck.getGenerationId(), budget);
            releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED);
            return false;
        }

        try {
            uploadDeck(deck);
            events.onDeckPrepared(deck.getGenerationId());
            return true;
        } catch (RuntimeException exception) {
            reportFailure(
                    deck.getGenerationId(),
                    true,
                    RenderFailureReason.TEXTURE_UPLOAD,
                    "Could not restore page textures after GL context recreation",
                    exception);
            releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED);
            return false;
        }
    }

    private void retainDeckTextures() {
        Set<String> retainedKeys = new LinkedHashSet<>();
        collectDeckKeys(activeDeck, retainedKeys);
        collectDeckKeys(replacementDeck, retainedKeys);
        Iterator<Map.Entry<String, GpuTexture>> iterator =
                textureCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, GpuTexture> entry = iterator.next();
            if (!retainedKeys.contains(entry.getKey())) {
                entry.getValue().deleteGl();
                iterator.remove();
            }
        }
        registerDeck(activeDeck);
        registerDeck(replacementDeck);
    }

    private void registerDeck(PageDeck<Bitmap> deck) {
        if (deck == null) {
            return;
        }
        for (PageImage<Bitmap> page : deck.getPages()) {
            textureCache.computeIfAbsent(
                    page.identityKey(),
                    key -> new GpuTexture(page, false));
            if (page.hasOverlay()) {
                textureCache.computeIfAbsent(
                        page.overlayIdentityKey(),
                        key -> new GpuTexture(page, true));
            }
        }
    }

    private static void collectDeckKeys(PageDeck<Bitmap> deck, Set<String> keys) {
        if (deck == null) {
            return;
        }
        for (PageImage<Bitmap> page : deck.getPages()) {
            keys.add(page.identityKey());
            if (page.hasOverlay()) {
                keys.add(page.overlayIdentityKey());
            }
        }
    }

    private void drawPortraitPage() {
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
        updateMvp(viewportWidth, viewportHeight);
        if (portraitModel.getActivePage() == ActivePage.LEFT) {
            drawPage(
                    rightMesh,
                    portraitRightResource,
                    portraitModel.getRightPage(),
                    false,
                    PageOrientation.PORTRAIT);
            drawPage(
                    frontMesh,
                    portraitFrontResource,
                    portraitModel.getFrontPage(),
                    false,
                    PageOrientation.PORTRAIT);
            drawMovingPage(
                    leftMesh,
                    portraitLeftResource,
                    portraitModel.getLeftPage(),
                    PageOrientation.PORTRAIT);
            return;
        }
        drawPage(
                leftMesh,
                portraitLeftResource,
                portraitModel.getLeftPage(),
                false,
                PageOrientation.PORTRAIT);
        drawPage(
                rightMesh,
                portraitRightResource,
                portraitModel.getRightPage(),
                false,
                PageOrientation.PORTRAIT);
        drawMovingPage(
                frontMesh,
                portraitFrontResource,
                portraitModel.getFrontPage(),
                PageOrientation.PORTRAIT);
    }

    /**
     * Single-leaf book turn for two-page landscape spreads.
     *
     * <p>One leaf travels the full spread — forward it starts flat on the right half
     * (the current right page), peels from its outer edge, sweeps left across the
     * spine inside a sliding half-width viewport, and exits past the left edge; the
     * landed spread is revealed flat beneath it. Backward mirrors this: the current
     * left page sweeps out to the right. There is no two-phase hand-off, so nothing
     * dives under one page or pops up from another mid-turn.
     *
     * <p>The flat spread drawn beneath swaps its landing half exactly mid-turn
     * (progress 0.5), while the traveling leaf still covers that region, so the swap
     * is never visible. Both ends are pixel-seamless: at rest the leaf sits flat over
     * identical content, and at full progress it has fully exited.
     */
    private void drawLandscapeSpread() {
        preloadSpreadWindow();
        int leftWidth = viewportWidth / 2;
        int rightWidth = viewportWidth - leftWidth;
        LandscapeSpreadTransition transition = landscapeSpreadModel.getTransition();

        if (transition.getProgress() == 0f) {
            drawFlatLeaf(0, leftWidth + 1, spreadCurrentLeftResource);
            drawFlatLeaf(leftWidth, rightWidth, spreadCurrentRightResource);
            return;
        }

        float progress = transition.getProgress();

        if (transition.isForward()) {
            drawFlatLeaf(0, leftWidth + 1, spreadCurrentLeftResource);
            drawFlatLeaf(leftWidth, rightWidth, spreadNextRightResource);
            drawLandscapeTurningLeaf(
                    spreadCurrentRightResource,
                    spreadNextLeftResource,
                    progress,
                    true);
        } else {
            drawFlatLeaf(leftWidth, rightWidth, spreadCurrentRightResource);
            drawFlatLeaf(0, leftWidth + 1, spreadPreviousLeftResource);
            drawLandscapeTurningLeaf(
                    spreadCurrentLeftResource,
                    spreadPreviousRightResource,
                    progress,
                    false);
        }
    }

    private void drawLandscapeTurningLeaf(
            PageImage<Bitmap> frontResource,
            PageImage<Bitmap> backResource,
            float progress,
            boolean forward) {
        GpuTexture frontTex = texture(frontResource);
        GpuTexture backTex = texture(backResource);
        if (frontTex == null) {
            return;
        }

        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
        updateMvp(viewportWidth, viewportHeight);

        frontMesh.ensureGeometry(frontTex.bitmapWidth, frontTex.bitmapHeight, PageOrientation.PORTRAIT);
        PlayLikeBezierCurl.updateLandscape(frontMesh.geometry, progress, forward);
        frontMesh.uploadPositions();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(textureUniform, 0);
        GLES20.glUniform1i(overlayTextureUniform, 1);
        GLES20.glUniform1i(backTextureUniform, 2);
        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, mvpMatrix, 0);

        drawPageTextures(frontResource, frontTex, backTex);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, frontMesh.positionBufferId);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, frontMesh.textureBufferId);
        GLES20.glEnableVertexAttribArray(textureCoordinateAttribute);
        GLES20.glVertexAttribPointer(
                textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, frontMesh.indexBufferId);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                frontMesh.geometry.getIndices().length,
                GLES20.GL_UNSIGNED_SHORT,
                0);

        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(textureCoordinateAttribute);
        GLES20.glUniform1f(hasBackTextureUniform, 0f);
    }

    private void drawFlatLeaf(int x, int width, PageImage<Bitmap> resource) {
        drawLeaf(x, width, resource, rightMesh, flatState, false);
    }

    private void drawLeaf(
            int x,
            int width,
            PageImage<Bitmap> resource,
            GpuMesh mesh,
            PageState state,
            boolean active) {
        GLES20.glViewport(x, 0, width, viewportHeight);
        updateMvp(width, viewportHeight);
        if (active) {
            drawMovingPage(mesh, resource, state, PageOrientation.PORTRAIT);
        } else {
            drawPage(mesh, resource, state, false, PageOrientation.PORTRAIT);
        }
    }

    private void drawMovingPage(
            GpuMesh mesh,
            PageImage<Bitmap> resource,
            PageState state,
            PageOrientation orientation) {
        drawFoldShadow(mesh, resource, state, orientation);
        drawPage(mesh, resource, state, true, orientation);
    }

    private void drawFoldShadow(
            GpuMesh mesh,
            PageImage<Bitmap> resource,
            PageState state,
            PageOrientation orientation) {
        GpuTexture texture = texture(resource);
        if (texture == null) {
            return;
        }
        FoldShadowModel.State shadow = FoldShadowModel.resolve(
                mesh.role, state.getCurlPosition(), mesh.horizontallyMirrored);
        if (shadow.getOpacity() <= 0.001f) {
            return;
        }

        float bitmapRatio = PlayLikeCurlGeometry.bitmapRatio(
                texture.bitmapWidth, texture.bitmapHeight, orientation);
        float heightCorrection = (bitmapRatio - 1f) / 2f;
        float bottom = -heightCorrection;
        float top = bitmapRatio - heightCorrection;
        shadowPositionBuffer.clear();
        shadowPositionBuffer.put(new float[] {
                shadow.getStartX(), bottom, FoldShadowModel.SHADOW_DEPTH,
                shadow.getEndX(), bottom, FoldShadowModel.SHADOW_DEPTH,
                shadow.getStartX(), top, FoldShadowModel.SHADOW_DEPTH,
                shadow.getEndX(), top, FoldShadowModel.SHADOW_DEPTH
        }).position(0);
        shadowGradientBuffer.clear();
        shadowGradientBuffer.put(shadow.isDarkAtStart()
                ? new float[] {0f, 1f, 0f, 1f}
                : new float[] {1f, 0f, 1f, 0f}).position(0);
        shadowIndexBuffer.clear();
        shadowIndexBuffer.put(SHADOW_INDICES).position(0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glUseProgram(shadowProgram);
        GLES20.glUniformMatrix4fv(shadowMatrixUniform, 1, false, mvpMatrix, 0);
        GLES20.glUniform1f(shadowOpacityUniform, shadow.getOpacity());
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnableVertexAttribArray(shadowPositionAttribute);
        GLES20.glVertexAttribPointer(
                shadowPositionAttribute, 3, GLES20.GL_FLOAT, false, 0, shadowPositionBuffer);
        GLES20.glEnableVertexAttribArray(shadowGradientAttribute);
        GLES20.glVertexAttribPointer(
                shadowGradientAttribute, 1, GLES20.GL_FLOAT, false, 0, shadowGradientBuffer);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                SHADOW_INDICES.length,
                GLES20.GL_UNSIGNED_SHORT,
                shadowIndexBuffer);
        GLES20.glDisableVertexAttribArray(shadowPositionAttribute);
        GLES20.glDisableVertexAttribArray(shadowGradientAttribute);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(textureUniform, 0);
        GLES20.glUniform1i(overlayTextureUniform, 1);
    }

    private void preloadSpreadWindow() {
        // Deck preparation uploads the complete six-leaf window before readiness is reported.
    }

    private void drawPage(
            GpuMesh mesh,
            PageImage<Bitmap> resource,
            PageState state,
            boolean active,
            PageOrientation orientation) {
        GpuTexture texture = texture(resource);
        if (texture == null) {
            return;
        }
        mesh.ensureGeometry(texture.bitmapWidth, texture.bitmapHeight, orientation);
        PlayLikeCurlGeometry.update(mesh.geometry, state.getCurlPosition(), active);
        mesh.uploadPositions();

        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, mvpMatrix, 0);
        GLES20.glUniform1f(hasBackTextureUniform, 0f);
        drawPageTextures(resource, texture);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.positionBufferId);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.textureBufferId);
        GLES20.glEnableVertexAttribArray(textureCoordinateAttribute);
        GLES20.glVertexAttribPointer(
                textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBufferId);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                mesh.geometry.getIndices().length,
                GLES20.GL_UNSIGNED_SHORT,
                0);
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(textureCoordinateAttribute);
    }

    private void drawPageTextures(
            PageImage<Bitmap> resource,
            GpuTexture baseTexture) {
        drawPageTextures(resource, baseTexture, null);
    }

    private void drawPageTextures(
            PageImage<Bitmap> resource,
            GpuTexture baseTexture,
            GpuTexture backTexture) {
        GpuTexture overlayTexture = overlayTexture(resource);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, baseTexture.textureId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                overlayTexture == null ? 0 : overlayTexture.textureId);
        GLES20.glUniform1f(hasOverlayUniform, overlayTexture == null ? 0f : 1f);

        if (backTexture != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backTexture.textureId);
            GLES20.glUniform1f(hasBackTextureUniform, 1f);
        } else {
            GLES20.glUniform1f(hasBackTextureUniform, 0f);
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    private GpuTexture texture(PageImage<Bitmap> page) {
        if (page == null) {
            return null;
        }
        GpuTexture texture = textureCache.get(page.identityKey());
        return texture != null && texture.uploaded ? texture : null;
    }

    private GpuTexture overlayTexture(PageImage<Bitmap> page) {
        if (page == null || !page.hasOverlay()) {
            return null;
        }
        GpuTexture texture = textureCache.get(page.overlayIdentityKey());
        return texture != null && texture.uploaded ? texture : null;
    }

    private void updateMvp(int width, int height) {
        float aspect = width / (float) height;
        float zNear = 0.1f;
        float zFar = 100f;
        float left = -0.5f * (zNear / PlayLikeCurlGeometry.CAMERA_DISTANCE);
        float right = 0.5f * (zNear / PlayLikeCurlGeometry.CAMERA_DISTANCE);
        float bottom = -(0.5f / aspect) * (zNear / PlayLikeCurlGeometry.CAMERA_DISTANCE);
        float top = (0.5f / aspect) * (zNear / PlayLikeCurlGeometry.CAMERA_DISTANCE);
        Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, zNear, zFar);
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -PlayLikeCurlGeometry.CAMERA_DISTANCE);
        Matrix.translateM(modelMatrix, 0, -0.5f, -0.5f, 0f);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0);
    }

    private long activeGeneration() {
        return activeDeck == null ? -1L : activeDeck.getGenerationId();
    }

    private void reportFailure(
            long generationId,
            boolean recoverable,
            RenderFailureReason reason,
            String message,
            Throwable cause) {
        events.onRenderFailure(
                new RenderFailure(generationId, recoverable, reason, message, cause));
    }

    private final class GpuTexture {
        private final PageImage<Bitmap> page;
        private final boolean overlay;
        private int textureId;
        private int bitmapWidth;
        private int bitmapHeight;
        private boolean uploaded;

        GpuTexture(PageImage<Bitmap> page, boolean overlay) {
            this.page = page;
            this.overlay = overlay;
        }

        void resetGl() {
            textureId = 0;
            uploaded = false;
        }

        void ensureUploaded() {
            if (uploaded) {
                return;
            }
            Bitmap bitmap = overlay ? page.getOverlayContent() : page.getContent();
            if (bitmap == null) {
                throw new IllegalStateException(
                        "Overlay bitmap is missing for " + page.getLogicalPageId());
            }
            if (bitmap.isRecycled()) {
                throw new IllegalStateException(
                        (overlay ? "Overlay bitmap" : "Bitmap")
                                + " was recycled for "
                                + page.getLogicalPageId());
            }
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            textureId = ids[0];
            bitmapWidth = bitmap.getWidth();
            bitmapHeight = bitmap.getHeight();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                deleteGl();
                throw new IllegalStateException(
                        "Texture upload failed with GLES error " + error);
            }
            uploaded = true;
        }

        void deleteGl() {
            if (textureId != 0) {
                int[] ids = {textureId};
                GLES20.glDeleteTextures(1, ids, 0);
            }
            textureId = 0;
            uploaded = false;
        }
    }

    private final class GpuMesh {
        private final PageRole role;
        private final boolean horizontallyMirrored;
        private final FloatBuffer positionBuffer;
        private final FloatBuffer textureBuffer;
        private final ShortBuffer indexBuffer;
        private final int[] bufferIds = new int[3];
        private PageGeometry geometry;
        private int geometryWidth = -1;
        private int geometryHeight = -1;
        private PageOrientation geometryOrientation;
        private int positionBufferId;
        private int textureBufferId;
        private int indexBufferId;

        GpuMesh(PageRole role) {
            this(role, false);
        }

        GpuMesh(PageRole role, boolean horizontallyMirrored) {
            this.role = role;
            this.horizontallyMirrored = horizontallyMirrored;
            geometry = PlayLikeCurlGeometry.createPage(role, 1, 1, PageOrientation.PORTRAIT);
            if (horizontallyMirrored) {
                mirrorTextureCoordinates(geometry.getTextureCoordinates());
            }
            positionBuffer = directFloatBuffer(geometry.getPositions().length);
            textureBuffer = directFloatBuffer(geometry.getTextureCoordinates().length);
            indexBuffer = directShortBuffer(geometry.getIndices().length);
        }

        void initializeGl() {
            dispose();
            GLES20.glGenBuffers(bufferIds.length, bufferIds, 0);
            positionBufferId = bufferIds[0];
            textureBufferId = bufferIds[1];
            indexBufferId = bufferIds[2];

            textureBuffer.clear();
            textureBuffer.put(geometry.getTextureCoordinates()).position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    geometry.getTextureCoordinates().length * Float.BYTES,
                    textureBuffer,
                    GLES20.GL_STATIC_DRAW);

            indexBuffer.clear();
            indexBuffer.put(geometry.getIndices()).position(0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    geometry.getIndices().length * Short.BYTES,
                    indexBuffer,
                    GLES20.GL_STATIC_DRAW);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    geometry.getPositions().length * Float.BYTES,
                    null,
                    GLES20.GL_DYNAMIC_DRAW);
            geometryWidth = -1;
            geometryHeight = -1;
            geometryOrientation = null;
        }

        void ensureGeometry(int width, int height, PageOrientation orientation) {
            if (width == geometryWidth
                    && height == geometryHeight
                    && orientation == geometryOrientation) {
                return;
            }
            geometry = PlayLikeCurlGeometry.createPage(role, width, height, orientation);
            if (horizontallyMirrored) {
                mirrorTextureCoordinates(geometry.getTextureCoordinates());
            }
            geometryWidth = width;
            geometryHeight = height;
            geometryOrientation = orientation;
        }

        void uploadPositions() {
            float[] positions = geometry.getPositions();
            positionBuffer.clear();
            if (horizontallyMirrored) {
                for (int offset = 0; offset < positions.length; offset += 3) {
                    positionBuffer.put(1f - positions[offset]);
                    positionBuffer.put(positions[offset + 1]);
                    positionBuffer.put(positions[offset + 2]);
                }
            } else {
                positionBuffer.put(positions);
            }
            positionBuffer.position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId);
            GLES20.glBufferSubData(
                    GLES20.GL_ARRAY_BUFFER,
                    0,
                    positions.length * Float.BYTES,
                    positionBuffer);
        }

        void dispose() {
            if (positionBufferId != 0 || textureBufferId != 0 || indexBufferId != 0) {
                int[] ids = {positionBufferId, textureBufferId, indexBufferId};
                GLES20.glDeleteBuffers(ids.length, ids, 0);
            }
            positionBufferId = 0;
            textureBufferId = 0;
            indexBufferId = 0;
        }
    }

    private static void mirrorTextureCoordinates(float[] coordinates) {
        for (int offset = 0; offset < coordinates.length; offset += 2) {
            coordinates[offset] = 1f - coordinates[offset];
        }
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int createdProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(createdProgram, vertexShader);
        GLES20.glAttachShader(createdProgram, fragmentShader);
        GLES20.glLinkProgram(createdProgram);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(createdProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetProgramInfoLog(createdProgram);
            GLES20.glDeleteProgram(createdProgram);
            throw new IllegalStateException(
                    "Could not link PlayLikeCurl GLES2 program: " + log);
        }
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return createdProgram;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException(
                    "Could not compile PlayLikeCurl GLES2 shader: " + log);
        }
        return shader;
    }

    private static FloatBuffer directFloatBuffer(int size) {
        return ByteBuffer.allocateDirect(size * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }

    private static ShortBuffer directShortBuffer(int size) {
        return ByteBuffer.allocateDirect(size * Short.BYTES)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
    }
}
