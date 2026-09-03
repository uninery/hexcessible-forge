package dev.tizu.hexcessible.drawstate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.render.RenderLib;
import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.HexcessibleConfig;
import dev.tizu.hexcessible.Utils;
import dev.tizu.hexcessible.accessor.CastRef;
import dev.tizu.hexcessible.entries.PatternEntries;
import dev.tizu.hexcessible.keybinds.KeyBinds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

public final class KeyboardDrawing extends DrawState {
    public static final int COLOR1 = 0xff_64c8ff;
    public static final int COLOR2 = 0xff_fecbe6;
    public static final int COLOR3 = 0xaa_363a4f;
    public static final int COLOR4 = 0xaa_6e738d;
    /** Color for the "press this to undo" marker in absolute mode. */
    public static final int COLOR_UNDO = 0xff_f28f8f;

    /** True while drawing by absolute (fixed screen) directions. */
    private final boolean absMode;
    /**
     * In absolute mode: the sequence of absolute segment directions the user
     * has typed. {@link #sig} then holds the equivalent relative angles
     * ({@code absDirs[i] - absDirs[i-1]}) and {@link #originDir} the first one.
     */
    private final List<HexDir> absDirs = new ArrayList<>();

    private List<HexAngle> sig;
    private HexCoord origin;
    private HexDir originDir = HexDir.EAST;
    private @Nullable HexCoord start;
    private @Nullable HexDir startDir;
    private @Nullable HexCoord end;
    private @Nullable HexDir endDir;
    private KeyboardDrawing nextDrawing;

    public KeyboardDrawing(CastRef castref, List<HexAngle> sig) {
        this(castref, sig, Hexcessible.cfg().keyboardDraw.absoluteMode);
    }

    public KeyboardDrawing(CastRef castref, List<HexAngle> sig, boolean absMode) {
        super(castref);
        this.sig = new ArrayList<>(sig);
        this.origin = new HexCoord(0, 0);
        this.absMode = absMode;
        recalculateNewAll();
    }

    /** Starts an absolute-mode drawing whose first segment points {@code dir}. */
    public KeyboardDrawing(CastRef castref, HexDir firstDir) {
        super(castref);
        this.absMode = true;
        this.origin = new HexCoord(0, 0);
        this.absDirs.add(firstDir);
        syncFromAbsDirs();
        recalculateNewAll();
    }

    public KeyboardDrawing(CastRef castref, HexCoord start, List<List<HexAngle>> sigs, HexDir dir) {
        super(castref);
        if (sigs.isEmpty())
            throw new IllegalArgumentException();
        this.sig = new ArrayList<>(sigs.get(0));
        this.origin = start;
        if (sigs.size() > 1)
            this.nextDrawing = new KeyboardDrawing(castref, start,
                    sigs.subList(1, sigs.size()), dir);
        this.originDir = dir;
        this.absMode = false;
        recalculateNewAll();
    }

    @Override
    public void requestExit() {
        if (nextDrawing != null) {
            // we may have placed a thingy where it wasn't when nextDrawing got
            // initialized, so we recalculate it (this fixes chaining overlap)
            nextDrawing.recalculateNewAll();
            nextState = nextDrawing;
        } else
            super.requestExit();
    }

    /** True when nothing has been typed yet (mode toggles allowed). */
    public boolean isBlankTyping() {
        return absMode ? absDirs.isEmpty() : sig.isEmpty();
    }

    private int queuedCount() {
        if (nextDrawing == null)
            return 0;
        return 1 + nextDrawing.queuedCount();
    }

    public void recalculateNewAll() {
        if (sig.isEmpty()) {
            start = origin;
            startDir = originDir;
            end = origin;
            endDir = originDir;
            return;
        }

        var mutated = castref.findClosestAvailable(origin,
                new HexPattern(originDir, sig));
        if (mutated == null) {
            start = null;
            startDir = null;
            end = null;
            endDir = null;
            return;
        }
        start = mutated.coord();
        startDir = mutated.startDir();

        var pat = new HexPattern(startDir, sig);
        end = Utils.finalPos(start, pat);
        endDir = pat.finalDir();
    }

    @Override
    public void onRender(GuiGraphics ctx, int mx, int my) {
        if (absMode ? absDirs.isEmpty() : sig.isEmpty())
            requestExit();
        renderPattern(ctx);
        if (Hexcessible.cfg().keyboardDraw.keyHint)
            renderNextPointTooltips(ctx);
        var pos = castref.coordToPx(end == null ? origin : end);
        var x = pos.x + 20;
        KeyboardDrawing.render(ctx, (int) x, (int) pos.y, sig, start == null,
                Hexcessible.cfg().keyboardDraw.tooltip, queuedCount());
    }

    @Override
    public void onCharType(char chr) {
        if (!Hexcessible.cfg().keyboardDraw.allow)
            return;
        if (absMode) {
            handleAbsoluteChar(chr);
        } else {
            if (KeyBinds.isRelativeUndoChar(chr)) { // go back
                removeCharFromSig();
            } else if (KeyBinds.relativeAngleOf(chr) != null) { // valid
                var angle = KeyBinds.relativeAngleOf(chr);
                if (canGo(angle)) {
                    sig.add(angle);
                    recalculateNewAll();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Absolute mode input handling
    // ------------------------------------------------------------------

    private void handleAbsoluteChar(char chr) {
        var dir = KeyBinds.absoluteDirOf(chr);
        if (dir == null)
            return;
        if (!absDirs.isEmpty() && endDir != null
                && dir == endDir.rotatedBy(HexAngle.BACK)) {
            // pressing the key that points back at the previously drawn spot
            // undoes the last stroke, like backtracking with the mouse
            undoAbsoluteSegment();
            return;
        }
        if (absDirs.isEmpty()) {
            absDirs.add(dir);
            syncFromAbsDirs();
            recalculateNewAll();
            return;
        }
        if (end == null)
            return; // currently nowhere to place this; ignore
        var delta = Utils.angleFromTo(absDirs.get(absDirs.size() - 1), dir);
        if (!canGo(delta))
            return;
        absDirs.add(dir);
        syncFromAbsDirs();
        recalculateNewAll();
    }

    private void undoAbsoluteSegment() {
        if (absDirs.isEmpty())
            return;
        absDirs.remove(absDirs.size() - 1);
        syncFromAbsDirs();
        recalculateNewAll();
    }

    /** Recomputes sig/originDir from the typed absolute directions. */
    private void syncFromAbsDirs() {
        if (absDirs.isEmpty()) {
            sig = new ArrayList<>();
            return;
        }
        originDir = absDirs.get(0);
        var out = new ArrayList<HexAngle>(absDirs.size() - 1);
        for (int i = 1; i < absDirs.size(); i++)
            out.add(Utils.angleFromTo(absDirs.get(i - 1), absDirs.get(i)));
        sig = out;
    }

    @Override
    public void onKeyPress(int keyCode, int modifiers) {
        var shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            removeCharFromSig();
        } else if (KeyBinds.keyMatches(KeyBinds.Action.CONFIRM, keyCode, modifiers)
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_TAB
                || keyCode == GLFW.GLFW_KEY_SPACE) {
            submit();
        } else if (KeyBinds.keyMatches(KeyBinds.Action.MOVE_LEFT, keyCode, modifiers)
                || keyCode == GLFW.GLFW_KEY_LEFT) {
            moveOrigin(-1, 0);
        } else if (KeyBinds.keyMatches(KeyBinds.Action.MOVE_DOWN, keyCode, modifiers)
                || keyCode == GLFW.GLFW_KEY_DOWN) {
            moveOrigin(0, 1);
        } else if (KeyBinds.keyMatches(KeyBinds.Action.MOVE_UP, keyCode, modifiers)
                || keyCode == GLFW.GLFW_KEY_UP) {
            moveOrigin(0, -1);
        } else if (KeyBinds.keyMatches(KeyBinds.Action.MOVE_RIGHT, keyCode, modifiers)
                || keyCode == GLFW.GLFW_KEY_RIGHT) {
            moveOrigin(1, 0);
        } else if (!absMode && KeyBinds.keyMatches(KeyBinds.Action.ROTATE_CCW, keyCode, modifiers)
                && shift) {
            rotate(-1);
        } else if (!absMode && KeyBinds.keyMatches(KeyBinds.Action.ROTATE_CW, keyCode, modifiers)
                && !shift) {
            rotate(1);
        }
    }

    private void submit() {
        recalculateNewAll();
        if (start == null)
            return;
        castref.execute(new HexPattern(startDir, sig), start);
        requestExit();
    }

    private void moveOrigin(int x, int y) {
        var next = origin.plus(new HexCoord(x, y));
        if (castref.isVisible(next)) // don't allow out of bounds
            origin = next;
        recalculateNewAll();
    }

    private void rotate(int delta) {
        var i = Math.floorMod(originDir.ordinal() + delta, HexDir.values().length);
        originDir = HexDir.values()[i];
        recalculateNewAll();
    }

    @Override
    public boolean onMouseScroll(int delta) {
        if (!absMode)
            rotate(-delta);
        return !absMode;
    }

    private void removeCharFromSig() {
        if (!Hexcessible.cfg().keyboardDraw.allow)
            requestExit();
        if (absMode) {
            undoAbsoluteSegment();
            return;
        }
        if (sig.isEmpty())
            return;
        sig.remove(sig.size() - 1);
        recalculateNewAll();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public void renderPattern(GuiGraphics ctx) {
        if (Hexcessible.cfg().keyboardDraw.ghost) {
            if (!absMode || !absDirs.isEmpty())
                renderPattern(ctx, origin, originDir, sig, COLOR3, COLOR4);
        }
        if (start != null)
            renderPattern(ctx, start, startDir, sig, COLOR1, COLOR2);

        if (!Hexcessible.cfg().debug)
            return;
        if (start != null)
            drawLine(ctx, origin, start);
        var mat = ctx.pose().last().pose();
        if (start != null)
            RenderLib.drawSpot(mat, castref.coordToPx(start), 6f, 0f, 0f, 1f, 1f);
        RenderLib.drawSpot(mat, castref.coordToPx(origin), 6f, 0f, 1f, 0f, 1f);
    }

    public void renderPattern(GuiGraphics ctx, HexCoord start, HexDir startDir,
            List<HexAngle> sig, int color1, int color2) {
        if (start == null || startDir == null)
            return;
        var mat = ctx.pose().last().pose();
        var pat = new HexPattern(startDir, sig);
        var duplicates = RenderLib.findDupIndices(pat.positions());

        var points = new ArrayList<Vec2>();
        for (var c : pat.positions())
            points.add(castref.coordToPx(new HexCoord(
                    c.getQ() + start.getQ(),
                    c.getR() + start.getR())));

        RenderLib.drawPatternFromPoints(mat, points, duplicates, false, color1,
                color2, 0.1f, RenderLib.DEFAULT_READABILITY_OFFSET, 1f, 0);
    }

    private void renderNextPointTooltips(GuiGraphics ctx) {
        if (end == null || endDir == null)
            return;
        var tr = Minecraft.getInstance().font;
        var endpx = castref.coordToPx(end);
        if (absMode) {
            renderAbsoluteKeyMarkers(ctx, tr, endpx);
            return;
        }
        for (var angle : HexAngle.values()) {
            var pos = end.plus(endDir.rotatedBy(angle));
            var charstr = KeyBinds.keyCharForAngle(angle);
            if (castref.isUsed(pos) || !canGo(angle) || charstr == 0)
                continue;
            var px = castref.coordToPx(pos);
            var dx = px.x - endpx.x;
            var dy = px.y - endpx.y;
            var distance = Math.sqrt(dx * dx + dy * dy);
            var targetX = endpx.x + (dx / distance) * 20;
            var targetY = endpx.y + (dy / distance) * 20;
            ctx.drawCenteredString(tr, Component.literal(String.valueOf(charstr)),
                    (int) targetX - 1, (int) targetY - 5, 0xff_A8A8A8);
        }
    }

    /** Absolute mode: marks the six directions around the current end. */
    private void renderAbsoluteKeyMarkers(GuiGraphics ctx,
            net.minecraft.client.gui.Font tr, Vec2 endpx) {
        // the direction pointing back at the previously drawn spot undoes
        var backDir = absDirs.isEmpty() || endDir == null
                ? null
                : endDir.rotatedBy(HexAngle.BACK);
        for (var dir : HexDir.values()) {
            var pos = end.plus(dir.asDelta());
            var ch = KeyBinds.keyCharForDir(dir);
            if (ch == 0)
                continue;
            var px = castref.coordToPx(pos);
            var dx = px.x - endpx.x;
            var dy = px.y - endpx.y;
            var distance = Math.sqrt(dx * dx + dy * dy);
            var targetX = endpx.x + (dx / distance) * 20;
            var targetY = endpx.y + (dy / distance) * 20;
            if (dir == backDir) {
                // pressing this undoes the last stroke
                ctx.drawCenteredString(tr, Component.literal(String.valueOf(ch)),
                        (int) targetX - 1, (int) targetY - 5, COLOR_UNDO);
                continue;
            }
            if (castref.isUsed(pos))
                continue;
            ctx.drawCenteredString(tr, Component.literal(String.valueOf(ch)),
                    (int) targetX - 1, (int) targetY - 5, 0xff_A8A8A8);
        }
    }

    private boolean canGo(@Nullable HexAngle angle) {
        if (angle == null || startDir == null)
            return false;
        var pat = new HexPattern(this.startDir, new ArrayList<>(sig));
        return castref.isValidPatternAddition(pat, angle);
    }

    private void drawLine(GuiGraphics ctx, HexCoord start, HexCoord end) {
        var startpx = castref.coordToPx(start);
        var endpx = castref.coordToPx(end);
        var dx = endpx.x - startpx.x;
        var dy = endpx.y - startpx.y;
        var length = Math.sqrt(dx * dx + dy * dy);
        var steps = (int) Math.ceil(length / 2);
        for (var i = 0; i < steps; i++) {
            var x = startpx.x + dx * i / steps;
            var y = startpx.y + dy * i / steps;
            ctx.fill((int) x, (int) y, (int) x + 2, (int) y + 2, COLOR2);
        }
    }

    public static void render(GuiGraphics ctx, int mx, int y, List<HexAngle> sig,
            boolean failed, HexcessibleConfig.Tooltip tooltip, int queued) {
        var tr = Minecraft.getInstance().font;
        if (sig.isEmpty() || !tooltip.visible()) {
            if (failed)
                ctx.renderTooltip(tr, Component.translatable("hexcessible.no_space")
                        .withStyle(ChatFormatting.RED), mx, y);
            return;
        }

        var text = Component.literal(Utils.angle(sig, Hexcessible.cfg().uppercaseSig));
        ctx.renderTooltip(tr, text, mx, y);
        y += 17;

        if (failed) {
            ctx.renderTooltip(tr, Component.translatable("hexcessible.no_space")
                    .withStyle(ChatFormatting.RED), mx, y);
            y += 17;
        }

        if (queued > 0) {
            ctx.renderTooltip(tr, Component.translatable("hexcessible.count_queued",
                    queued).withStyle(ChatFormatting.YELLOW), mx, y);
            y += 17;
        }

        var entry = PatternEntries.INSTANCE.getFromSig(sig);
        if (entry == null || !tooltip.descriptive())
            return;
        var subtext = new ArrayList<Component>();
        subtext.add(Component.literal(entry.toString()).withStyle(ChatFormatting.BLUE));
        for (var impl : entry.impls())
            subtext.add(Component.literal(impl.getArgs()).withStyle(ChatFormatting.DARK_GRAY));
        ctx.renderTooltip(tr, subtext, java.util.Optional.empty(), mx, y);
    }

    @Override
    public void onMouseMove(double mx, double my) {
        origin = castref.pxToCoord(new Vec2((int) mx, (int) my));
        recalculateNewAll();
    }

    @Override
    public boolean allowStartDrawing() {
        return absMode ? absDirs.isEmpty() : sig.isEmpty();
    }

    @Override
    public void onMousePress(double mx, double my, int button) {
        if (button == 1)
            requestExit();
        if (button == 0)
            submit();
    }

    @Override
    public Map<String, String> getHints() {
        var keys = new HashMap<String, String>();

        if (Hexcessible.cfg().keyboardDraw.allow) {
            keys.put(absMode ? KeyBinds.absoluteDrawLabel()
                    : KeyBinds.relativeDrawLabel(), "draw_start");
            keys.put("bksp" + (absMode ? "" : "/" + KeyBinds.label(KeyBinds.Action.KBD_UNDO)),
                    "undo");
        }

        keys.put("lmb/" + KeyBinds.label(KeyBinds.Action.CONFIRM)
                + "/tab/space", "cast");
        keys.put("drag/" + KeyBinds.label(KeyBinds.Action.MOVE_LEFT) + "/"
                + KeyBinds.label(KeyBinds.Action.MOVE_UP) + "/"
                + KeyBinds.label(KeyBinds.Action.MOVE_DOWN) + "/"
                + KeyBinds.label(KeyBinds.Action.MOVE_RIGHT)
                + "/\u2190/\u2193/\u2191/\u2192", "move");
        if (!absMode)
            keys.put("wheel/" + KeyBinds.label(KeyBinds.Action.ROTATE_CW) + "/"
                    + KeyBinds.label(KeyBinds.Action.ROTATE_CCW), "rotate");
        if (isBlankTyping())
            keys.put(KeyBinds.label(KeyBinds.Action.MODE_TOGGLE), "mode");

        return keys;
    }
}
