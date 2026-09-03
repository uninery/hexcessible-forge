package dev.tizu.hexcessible.gui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.HexcessibleConfig;
import dev.tizu.hexcessible.drawstate.DrawState;
import dev.tizu.hexcessible.drawstate.Idling;
import dev.tizu.hexcessible.drawstate.KeyboardDrawing;
import dev.tizu.hexcessible.drawstate.MouseDrawing;
import dev.tizu.hexcessible.entries.BookEntries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

/**
 * Floating Patchouli hex book shown over the spellcasting interface.
 *
 * <p>
 * While it is open, the book GUI that Patchouli would normally show as a
 * full-screen {@link Screen} is instead installed here (see
 * {@link #navigateTo}) and rendered every frame into an off-screen
 * {@link RenderTarget} the size of the window. Only the region around the
 * book pages is then copied back onto the casting screen at a user-draggable
 * position, so the book stays fully interactive (page flips, links, search,
 * bookmarks, ...) while the pattern grid around it keeps working.
 *
 * <p>
 * The book survives cast-session boundaries: closing the casting interface
 * while the book is open just hides it and the next time casting starts the
 * book reappears at the remembered position, on the remembered page.
 */
public final class BookOverlay {
    private BookOverlay() {
    }

    /** Extra columns of the book texture we crop around (page margin). */
    private static final int MARGIN_LEFT = 8;
    private static final int MARGIN_RIGHT = 26;
    /** Height of the drag strip painted above the book. */
    private static final int STRIP_H = 13;

    private static GuiBook book;
    private static boolean open;
    private static boolean visible;
    private static float winX = -1;
    private static float winY = -1;
    private static float scaleFactor = 1f;

    private static boolean dragging;
    private static float dragDx, dragDy;

    private static RenderTarget fbo;
    private static int fboW = -1;
    private static int fboH = -1;

    private static MethodHandle screenChildren;
    private static MethodHandle screenNarratables;
    private static MethodHandle screenInitialized;
    private static MethodHandle guiBookScaleFactor;

    static {
        try {
            var priv = MethodHandles.privateLookupIn(Screen.class,
                    MethodHandles.lookup());
            screenChildren = priv.findGetter(Screen.class, "children", List.class);
            screenNarratables = priv.findGetter(Screen.class, "narratables", List.class);
            screenInitialized = priv.findSetter(Screen.class, "initialized", boolean.class);
            var privBook = MethodHandles.privateLookupIn(GuiBook.class,
                    MethodHandles.lookup());
            guiBookScaleFactor = privBook.findGetter(GuiBook.class, "scaleFactor",
                    float.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public static boolean isOpen() {
        return open;
    }

    public static boolean isVisible() {
        return visible;
    }

    public static boolean isDragging() {
        return dragging;
    }

    /**
     * True while the pointer rests on the book and it can take keyboard
     * input (docs/mode keys are then left for the book's own use).
     */
    public static boolean isOverlayFocused() {
        return visible && book != null && mouseInContent(lastMx, lastMy);
    }

    /** Whether the docs key may act right now (keyDocs config + draw state). */
    public static boolean docsActionAllowed(DrawState state) {
        var cfg = Hexcessible.cfg();
        if (cfg.keyDocs == HexcessibleConfig.KeyDocs.OFF)
            return false;
        var ok = switch (cfg.keyDocs) {
            case ALWAYS -> state instanceof Idling || state instanceof MouseDrawing
                    || state instanceof KeyboardDrawing;
            default -> state instanceof Idling;
        };
        return ok;
    }

    /** Lets the floating book animate while the casting UI ticks. */
    public static void onTick() {
        if (!visible || book == null)
            return;
        try {
            book.tick();
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Floating book tick failed", e);
        }
    }

    /** Called when a spellcasting screen opens (from the DrawStateMixin). */
    public static void onCastUiOpened() {
        if (!Hexcessible.cfg().docsFloating)
            return;
        open = true;
        visible = true;
        var target = currentTarget();
        if (target != null)
            install(target);
        restorePosition();
    }

    /** Called when the spellcasting screen goes away (any reason). */
    public static void onCastUiClosed() {
        if (visible)
            persistState();
        visible = false;
    }

    /** Toggle the floating book (from the docs key). */
    public static void toggleDocs() {
        if (open)
            close();
        else
            openDocs();
    }

    public static void close() {
        if (!open)
            return;
        open = false;
        visible = false;
        persistState();
    }

    public static void openDocs() {
        if (open) {
            visible = true;
            return;
        }
        open = true;
        visible = true;
        var target = currentTarget();
        if (target == null) {
            open = false;
            visible = false;
            return;
        }
        install(target);
        restorePosition();
        persistState();
    }

    /**
     * What to show when (re)opening: the book instance from this session if we
     * have one (keeps the live page state), otherwise the persisted entry/spread
     * from the config if it is still unlocked, otherwise wherever the book was
     * last.
     */
    @Nullable
    private static GuiBook currentTarget() {
        var mc = Minecraft.getInstance();
        var b = bookAt(mc);
        if (b == null)
            return null;
        var contents = b.getContents();
        contents.checkValidCurrentEntry();
        if (book != null)
            return book;
        var cfg = Hexcessible.cfg();
        if ("entry".equals(cfg.bookKind) && cfg.bookEntry != null
                && !cfg.bookEntry.isBlank()) {
            var id = ResourceLocation.tryParse(cfg.bookEntry);
            var entry = id == null ? null : contents.entries.get(id);
            if (entry != null && !entry.isLocked())
                return new GuiBookEntry(b, entry, clampSpread(entry, cfg.bookSpread));
        }
        return contents.getCurrentGui();
    }

    @Nullable
    private static Book bookAt(Minecraft mc) {
        var b = BookRegistry.INSTANCE.books.get(BookEntries.BOOKID);
        if (b == null)
            Hexcessible.LOGGER.error("Hex book {} not loaded; cannot float it",
                    BookEntries.BOOKID);
        return b;
    }

    private static int clampSpread(BookEntry entry, int spread) {
        int max = Math.max(0, (entry.getPages().size() + 1) / 2 - 1);
        return Math.max(0, Math.min(spread, max));
    }

    // ------------------------------------------------------------------
    // Installing book GUIs (navigation)
    // ------------------------------------------------------------------

    /**
     * Reroutes Patchouli navigation into the overlay while it is active
     * (called from the BookContents mixin instead of {@code mc.setScreen}).
     */
    public static void navigateTo(GuiBook gui, boolean push) {
        if (!open)
            return;
        if (push && book != null && gui != book)
            gui.book.getContents().guiStack.push(book);
        install(gui);
    }

    private static void install(GuiBook gui) {
        var mc = Minecraft.getInstance();
        try {
            // Make this instance behave like a freshly shown screen: drop old
            // widgets, then let the vanilla Screen.init() bootstrap run.
            ((List<?>) screenChildren.invoke(gui)).clear();
            ((List<?>) screenNarratables.invoke(gui)).clear();
            gui.renderables.clear();
            screenInitialized.invoke(gui, false);
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Failed to reset book gui {} for overlay", gui, e);
        }
        try {
            gui.init(mc, mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
            scaleFactor = (float) guiBookScaleFactor.invoke(gui);
            if (scaleFactor <= 0)
                scaleFactor = 1f;
            gui.onFirstOpened();
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Failed to init book gui {} for overlay", gui, e);
            return;
        }
        book = gui;
        recordBrowsingState();
        if (winX < 0)
            defaultPosition();
    }

    // ------------------------------------------------------------------
    // Window geometry
    // ------------------------------------------------------------------

    private static float contentW() {
        return (GuiBook.FULL_WIDTH + MARGIN_LEFT + MARGIN_RIGHT) * scaleFactor;
    }

    private static float contentH() {
        return GuiBook.FULL_HEIGHT * scaleFactor;
    }

    private static void defaultPosition() {
        var mc = Minecraft.getInstance();
        var w = mc.getWindow().getGuiScaledWidth();
        var h = mc.getWindow().getGuiScaledHeight();
        winX = (w - contentW()) / 2f;
        winY = (h - contentH()) / 2f;
    }

    private static void restorePosition() {
        if (Hexcessible.cfg().bookX >= 0) {
            winX = Hexcessible.cfg().bookX;
            winY = Hexcessible.cfg().bookY;
        } else if (winX < 0) {
            defaultPosition();
        }
        clampPosition();
    }

    private static void clampPosition() {
        var mc = Minecraft.getInstance();
        var w = mc.getWindow().getGuiScaledWidth();
        var h = mc.getWindow().getGuiScaledHeight();
        var cw = contentW();
        var ch = contentH();
        winX = Math.max(2, Math.min(winX, Math.max(2, w - cw - 2)));
        winY = Math.max(STRIP_H + 2,
                Math.min(winY, Math.max(STRIP_H + 2, h - ch - 2)));
    }

    /** Where the book was rendered inside the off-screen buffer. */
    private static float[] cropRect() {
        var w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        var h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        var x0 = (book.bookLeft - MARGIN_LEFT) * scaleFactor;
        var y0 = book.bookTop * scaleFactor;
        var x1 = x0 + contentW();
        var y1 = y0 + contentH();
        // The crop may extend past the buffer (zoom) -- clip it.
        x1 = Math.min(x1, w);
        y1 = Math.min(y1, h);
        return new float[] { x0, y0, x1, y1 };
    }

    private static boolean mouseInContent(double mx, double my) {
        if (book == null)
            return false;
        clampPosition();
        return mx >= winX && mx <= winX + contentW()
                && my >= winY && my <= winY + contentH();
    }

    private static boolean mouseInStrip(double mx, double my) {
        if (book == null)
            return false;
        return mx >= winX && mx <= winX + contentW()
                && my >= winY - STRIP_H && my <= winY;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** Returns true if the press was consumed by the book overlay. */
    public static boolean onMousePress(double mx, double my, int button) {
        if (!visible || book == null)
            return false;
        clampPosition();
        if (mouseInStrip(mx, my)) {
            if (button == 0) {
                var closeZone = winX + contentW() - 11;
                if (mx >= closeZone) {
                    close();
                } else {
                    dragging = true;
                    dragDx = (float) (mx - winX);
                    dragDy = (float) (my - winY);
                }
            }
            return true;
        }
        if (mouseInContent(mx, my)) {
            if (button == 0 || button == 1 || button == 2) {
                var vx = toVirtualX(mx);
                var vy = toVirtualY(my);
                try {
                    book.mouseClickedScaled(vx, vy, button);
                } catch (Throwable e) {
                    Hexcessible.LOGGER.error("Floating book click failed", e);
                }
            }
            return true;
        }
        return false;
    }

    private static double toVirtualX(double mx) {
        return (book.bookLeft - MARGIN_LEFT) + (mx - winX) / scaleFactor;
    }

    private static double toVirtualY(double my) {
        return book.bookTop + (my - winY) / scaleFactor;
    }

    /** Moves the window while dragging; true if consumed. */
    public static boolean onMouseMoved(double mx, double my) {
        if (!visible || !dragging || book == null)
            return false;
        winX = (float) (mx - dragDx);
        winY = (float) (my - dragDy);
        clampPosition();
        return true;
    }

    /** Ends a window drag; true if there was one. */
    public static boolean onMouseReleased() {
        if (!visible || !dragging)
            return false;
        dragging = false;
        persistState();
        return true;
    }

    /** Wheel over the book flips pages; true if consumed. */
    public static boolean onMouseScroll(double mx, double my, double delta) {
        if (!visible || book == null)
            return false;
        if (!mouseInContent(mx, my))
            return false;
        try {
            // positive delta = wheel up = flip one spread back (Patchouli
            // convention: scroll > 0 -> changePage(left))
            book.mouseScrolled(mx, my, delta);
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Floating book scroll failed", e);
        }
        return true;
    }

    /**
     * Keyboard access to the floating book: only when the pointer rests on
     * the book and no drawing/typing flow is active.
     */
    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers,
            DrawState state) {
        if (!visible || book == null)
            return false;
        if (!(state instanceof Idling) && !(state instanceof MouseDrawing))
            return false;
        if (!mouseInContent(lastMx, lastMy))
            return false;
        var mc = Minecraft.getInstance();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                || mc.options.keyInventory.matches(keyCode, scanCode))
            return false;
        try {
            return book.keyPressed(keyCode, scanCode, modifiers);
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Floating book key failed", e);
            return false;
        }
    }

    public static boolean onCharTyped(char chr, int modifiers, DrawState state) {
        if (!visible || book == null)
            return false;
        if (!(state instanceof Idling) && !(state instanceof MouseDrawing))
            return false;
        if (!mouseInContent(lastMx, lastMy))
            return false;
        try {
            return book.charTyped(chr, modifiers);
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Floating book char failed", e);
            return false;
        }
    }

    private static double lastMx = 0;
    private static double lastMy = 0;

    /** Every frame the host renders (mouse pos for hover-based routing). */
    public static void onHostRender(double mx, double my) {
        lastMx = mx;
        lastMy = my;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private static void recordBrowsingState() {
        var cfg = Hexcessible.cfg();
        if (book instanceof GuiBookEntry entryGui) {
            cfg.bookKind = "entry";
            var id = entryGui.getEntry().getId();
            cfg.bookEntry = id == null ? "" : id.toString();
            cfg.bookSpread = entryGui.getSpread();
        } else {
            cfg.bookKind = "landing";
            cfg.bookEntry = "";
            cfg.bookSpread = 0;
        }
    }

    private static void persistState() {
        var cfg = Hexcessible.cfg();
        cfg.docsFloating = open;
        if (winX >= 0) {
            cfg.bookX = Math.round(winX);
            cfg.bookY = Math.round(winY);
        }
        recordBrowsingState();
        cfg.markDirty();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Renders the floating book over the host screen (call at end of frame). */
    public static void render(GuiGraphics ctx, int mx, int my, float pticks) {
        if (!visible || book == null)
            return;
        var mc = Minecraft.getInstance();
        try {
            clampPosition();
            ensureFbo(mc);
            // 1) paint the book GUI into the off-screen buffer
            fbo.bindWrite(false);
            fbo.setClearColor(0f, 0f, 0f, 0f);
            fbo.clear(Minecraft.ON_OSX);

            var bookGraphics = new GuiGraphics(mc,
                    MultiBufferSource.immediate(Tesselator.getInstance().getBuilder()));
            var crop = cropRect();
            // mouse coords as if the book were fullscreen, where it was rendered
            var fx = (int) (crop[0] + (mx - winX));
            var fy = (int) (crop[1] + (my - winY));
            book.render(bookGraphics, fx, fy, pticks);
            bookGraphics.flush();

            mc.getMainRenderTarget().bindWrite(false);

            // 2) copy the book region back onto the casting screen
            blitCrop(ctx, crop);
            // 3) window chrome (drag strip, close button)
            drawChrome(ctx, mx, my);
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Failed to render floating book", e);
            mc.getMainRenderTarget().bindWrite(false);
        }
    }

    private static void ensureFbo(Minecraft mc) {
        var w = mc.getWindow().getWidth();
        var h = mc.getWindow().getHeight();
        if (fbo == null || fboW != w || fboH != h) {
            if (fbo != null)
                fbo.destroyBuffers();
            fbo = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            fboW = w;
            fboH = h;
        }
    }

    private static void blitCrop(GuiGraphics ctx, float[] crop) {
        var mc = Minecraft.getInstance();
        var guiScale = mc.getWindow().getGuiScale();
        // source region in physical pixels of the fbo (measured from the top)
        float sx0 = crop[0] * (float) guiScale;
        float sy0 = crop[1] * (float) guiScale;
        float sx1 = crop[2] * (float) guiScale;
        float sy1 = crop[3] * (float) guiScale;
        sx0 = Math.max(0, sx0);
        sy0 = Math.max(0, sy0);
        sx1 = Math.min(fboW, sx1);
        sy1 = Math.min(fboH, sy1);
        if (sx1 <= sx0 || sy1 <= sy0)
            return;
        float fw = fboW;
        float fh = fboH;
        // Framebuffer rows are stored bottom-up (v=0 is the bottom row), so
        // content that was at crop height y sits at v = 1 - y/fh.
        float u0 = sx0 / fw;
        float u1 = sx1 / fw;
        float v0 = 1f - sy0 / fh; // v at the crop's top
        float v1 = 1f - sy1 / fh; // v at the crop's bottom

        float destX0 = winX + (sx0 / (float) guiScale - crop[0]);
        float destY0 = winY + (sy0 / (float) guiScale - crop[1]);
        float destX1 = winX + (sx1 / (float) guiScale - crop[0]);
        float destY1 = winY + (sy1 / (float) guiScale - crop[1]);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, fbo.getColorTextureId());
        var m = ctx.pose().last().pose();
        var bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, destX0, destY0, 0).uv(u0, v0).endVertex();
        bb.vertex(m, destX0, destY1, 0).uv(u0, v1).endVertex();
        bb.vertex(m, destX1, destY1, 0).uv(u1, v1).endVertex();
        bb.vertex(m, destX1, destY0, 0).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawChrome(GuiGraphics ctx, int mx, int my) {
        var font = Minecraft.getInstance().font;
        float x0 = winX;
        float y0 = winY - STRIP_H;
        float x1 = winX + contentW();
        float y1 = winY;
        // title strip
        ctx.fill((int) x0, (int) y0, (int) x1, (int) y1, 0xE0_181420);
        // close button
        boolean overClose = mx >= x1 - 11 && mx <= x1 && my >= y0 && my <= y1;
        ctx.fill((int) x1 - 11, (int) y0, (int) x1, (int) y1,
                overClose ? 0xB0_8f2833 : 0x60_2b2437);
        ctx.drawString(font, "\u00d7", (int) x1 - 8, (int) y0 + 2, 0xff_ffffff);
        var title = Component.translatable("hexcessible.book_overlay.title");
        ctx.drawString(font, title, (int) x0 + 5, (int) y0 + 2, 0xff_c9c2d6);
    }
}
