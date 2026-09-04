package dev.tizu.hexcessible.gui;

import org.jetbrains.annotations.Nullable;

import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.HexcessibleConfig;
import dev.tizu.hexcessible.drawstate.DrawState;
import dev.tizu.hexcessible.drawstate.Idling;
import dev.tizu.hexcessible.drawstate.KeyboardDrawing;
import dev.tizu.hexcessible.drawstate.MouseDrawing;
import dev.tizu.hexcessible.entries.BookEntries;
import dev.tizu.hexcessible.mixin.HexcessibleGuiBookAccessor;
import dev.tizu.hexcessible.mixin.HexcessibleScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

/**
 * Floating Patchouli hex book shown over the spellcasting interface.
 *
 * <p>
 * While it is open, the Patchouli book GUI that would normally take over the
 * screen is installed here (see {@link #navigateTo}) and rendered directly
 * onto the casting screen, translated to the user-draggable floating window
 * position. Rendering re-uses Patchouli's own page-drawing methods
 * ({@link HexcessibleGuiBookAccessor}) but skips the full-screen background,
 * so no dim/overlay covers the pattern grid and no off-screen render target
 * is needed. The book stays fully interactive (page flips, links, search,
 * bookmarks, ...) while the pattern grid around it keeps working.
 *
 * <p>
 * The book survives cast-session boundaries: closing the casting interface
 * while the book is open just hides it and the next time casting starts the
 * book reappears at the remembered window position. Which page it shows is
 * Patchouli's own book state (same as reopening the real book), so no extra
 * "jump to last entry" tracking is done here.
 */
public final class BookOverlay {
    private BookOverlay() {
    }

    /** Extra columns of the book window around the texture (page-turn
     *  buttons on the left, bookmarks/mark-read on the right). */
    private static final int MARGIN_LEFT = 8;
    private static final int MARGIN_RIGHT = 26;
    /** Height of the drag strip painted above the book. */
    private static final int STRIP_H = 13;
    /**
     * The prev/next-page and back buttons hang a few pixels below the book
     * texture (bookTop + FULL_HEIGHT - 6 with height 10). Keep that overhang
     * inside the book's click area so pressing it never leaks through to the
     * pattern grid behind.
     */
    private static final int BOTTOM_PAD = 10;
    /** Pixels of mouse travel before a press on empty book space drags. */
    private static final int DRAG_THRESHOLD = 6;

    private static GuiBook book;
    private static boolean open;
    private static boolean visible;
    private static float winX = -1;
    private static float winY = -1;
    private static float scaleFactor = 1f;

    /** Dragging the window (strip grab, middle-mouse, or empty-space drag). */
    private static boolean dragging;
    /** A left press on book space that may turn into a drag on movement. */
    private static boolean potentialDrag;
    /** A press inside the book was consumed (release must not reach the UI). */
    private static boolean pressConsumed;
    private static float grabDx, grabDy;

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
        dragging = false;
        potentialDrag = false;
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
     * have one (keeps the live page state), otherwise wherever Patchouli's own
     * book state currently is (it natively remembers the last page/stack).
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
            // (Accessors go through the refmap, so this also works on the
            // SRG-obfuscated production Minecraft.)
            var accessor = (HexcessibleScreenAccessor) (Object) gui;
            accessor.hexcessible$children().clear();
            accessor.hexcessible$narratables().clear();
            gui.renderables.clear();
            accessor.hexcessible$setInitialized(false);
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Failed to reset book gui {} for overlay", gui, e);
        }
        try {
            gui.init(mc, mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
            scaleFactor = readScaleFactor(gui);
            gui.onFirstOpened();
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Failed to init book gui {} for overlay", gui, e);
            return;
        }
        book = gui;
        if (winX < 0)
            defaultPosition();
    }

    private static float readScaleFactor(GuiBook gui) {
        try {
            var k = ((HexcessibleGuiBookAccessor) (Object) gui).hexcessible$scaleFactor();
            return k > 0 ? k : 1f;
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Failed to read the book zoom", e);
            return 1f;
        }
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

    private static boolean mouseInContent(double mx, double my) {
        if (book == null)
            return false;
        clampPosition();
        // content plus the overhang of the bottom button row (prev/next/back)
        return mx >= winX && mx <= winX + contentW()
                && my >= winY && my <= winY + contentH() + BOTTOM_PAD * scaleFactor;
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
                    startDrag(mx, my);
                }
            }
            pressConsumed = true;
            return true;
        }
        if (mouseInContent(mx, my)) {
            if (button == 2) {
                // middle mouse drags the window from anywhere
                startDrag(mx, my);
                pressConsumed = true;
                return true;
            }
            if (button == 0 || button == 1 || button == 4 || button == 5) {
                var vx = toVirtualX(mx);
                var vy = toVirtualY(my);
                try {
                    var consumed = book.mouseClickedScaled(vx, vy, button);
                    if (button == 0 && !consumed) {
                        // nothing interactive was pressed: dragging by empty
                        // book space is allowed if the mouse moves a bit
                        potentialDrag = true;
                        grabDx = (float) (mx - winX);
                        grabDy = (float) (my - winY);
                    }
                } catch (Throwable e) {
                    Hexcessible.LOGGER.error("Floating book click failed", e);
                }
            }
            pressConsumed = true;
            return true;
        }
        return false;
    }

    private static void startDrag(double mx, double my) {
        dragging = true;
        potentialDrag = false;
        grabDx = (float) (mx - winX);
        grabDy = (float) (my - winY);
    }

    /**
     * Mouse movement/drag while a button may be held. Returns true if the
     * overlay consumed the event (window drag in progress or started).
     */
    public static boolean onMouseMoved(double mx, double my) {
        if (!visible || book == null)
            return false;
        if (dragging) {
            winX = (float) (mx - grabDx);
            winY = (float) (my - grabDy);
            clampPosition();
            return true;
        }
        if (potentialDrag
                && Math.abs(mx - winX - grabDx) + Math.abs(my - winY - grabDy)
                        > DRAG_THRESHOLD) {
            dragging = true;
            potentialDrag = false;
            winX = (float) (mx - grabDx);
            winY = (float) (my - grabDy);
            clampPosition();
            return true;
        }
        return false;
    }

    /** Ends a press/drag; true if there was one (consume the release). */
    public static boolean onMouseReleased() {
        if (!visible)
            return false;
        var wasActive = dragging || potentialDrag || pressConsumed;
        if (dragging)
            persistState();
        dragging = false;
        potentialDrag = false;
        pressConsumed = false;
        return wasActive;
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

    private static double toVirtualX(double mx) {
        return (book.bookLeft - MARGIN_LEFT) + (mx - winX) / scaleFactor;
    }

    private static double toVirtualY(double my) {
        return book.bookTop + (my - winY) / scaleFactor;
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

    private static void persistState() {
        var cfg = Hexcessible.cfg();
        cfg.docsFloating = open;
        if (winX >= 0) {
            cfg.bookX = Math.round(winX);
            cfg.bookY = Math.round(winY);
        }
        cfg.markDirty();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Renders the floating Hex Notebook over the host screen. The book content
     * itself is drawn by {@link #renderWindowed}, which reuses Patchouli's
     * page drawing but never paints its fullscreen background, so nothing dims
     * or blurs the casting screen.
     */
    public static void render(GuiGraphics ctx, int mx, int my, float pticks) {
        if (!visible || book == null)
            return;
        try {
            clampPosition();
            scaleFactor = readScaleFactor(book);
            drawBackdrops(ctx);
            renderWindowed(ctx, mx, my, pticks);
            drawChrome(ctx, mx, my);
        } catch (Throwable e) {
            Hexcessible.LOGGER.error("Failed to render floating book", e);
        }
    }

    /** Dark backdrops for the columns beside the book texture. */
    private static void drawBackdrops(GuiGraphics ctx) {
        var k = scaleFactor;
        var y0 = (int) winY;
        var y1 = (int) (winY + contentH());
        var leftEnd = (int) (winX + MARGIN_LEFT * k);
        var rightStart = (int) (winX + (MARGIN_LEFT + GuiBook.FULL_WIDTH) * k);
        var rightEnd = (int) (winX + contentW());
        ctx.fill((int) winX, y0, leftEnd, y1, 0xC0_101018);
        ctx.fill(rightStart, y0, rightEnd, y1, 0xC0_101018);
    }

    /**
     * Draws the Patchouli book GUI windowed at {@link #winX}/{@link #winY}.
     * Mirrors {@code GuiBook#render} + {@code drawScreenAfterScale}, but the
     * pose is translated/scaled so the book lands in the floating window and
     * the fullscreen background pass is skipped. Mouse coordinates passed to
     * the book are converted to its own (virtual) coordinate space.
     */
    public static void renderWindowed(GuiGraphics ctx, int mx, int my, float pticks) {
        var gui = book;
        if (gui == null)
            return;
        var k = scaleFactor;
        // window content left/top correspond to bookLeft - MARGIN_LEFT and
        // bookTop in the book's virtual coordinate space
        float shiftX = winX - k * (gui.bookLeft - MARGIN_LEFT);
        float shiftY = winY - k * gui.bookTop;
        float vmx = (mx - shiftX) / k;
        float vmy = (my - shiftY) / k;
        var acc = (HexcessibleGuiBookAccessor) (Object) gui;
        var pose = ctx.pose();
        pose.pushPose();
        pose.translate(shiftX, shiftY, 0f);
        pose.scale(k, k, 1f);
        acc.hexcessible$resetTooltip();
        ctx.setColor(1f, 1f, 1f, 1f);
        // the book texture and the page content live at bookLeft/bookTop
        pose.pushPose();
        pose.translate(gui.bookLeft, gui.bookTop, 0f);
        acc.hexcessible$drawBackgroundElements(ctx, (int) vmx, (int) vmy, pticks);
        acc.hexcessible$drawForegroundElements(ctx, (int) vmx, (int) vmy, pticks);
        pose.popPose();
        // widgets (buttons, bookmarks, search box) are positioned in the
        // virtual screen space
        for (Renderable renderable : gui.renderables)
            renderable.render(ctx, (int) vmx, (int) vmy, pticks);
        acc.hexcessible$drawTooltip(ctx, (int) vmx, (int) vmy);
        pose.popPose();
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
