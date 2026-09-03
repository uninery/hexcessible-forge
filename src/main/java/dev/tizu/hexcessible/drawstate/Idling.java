package dev.tizu.hexcessible.drawstate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import dev.tizu.hexcessible.Hexcessible;
import dev.tizu.hexcessible.accessor.CastRef;
import dev.tizu.hexcessible.entries.PatternEntries;
import dev.tizu.hexcessible.keybinds.KeyBinds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;

public final class Idling extends DrawState {

    private @Nullable HexPattern hoveredOver;
    private @Nullable PatternEntries.Entry hoveredOverEntry;
    private long hoveredOverStart = 0;
    private Vec2 mousePos = new Vec2(0, 0);

    public Idling(CastRef castref) {
        super(castref);
    }

    @Override
    public void requestExit() {
        wantsExit = true;
    }

    @Override
    public void onCharType(char chr) {
        if (!Hexcessible.cfg().keyboardDraw.allow)
            return;
        var abs = Hexcessible.cfg().keyboardDraw.absoluteMode;
        var dir = KeyBinds.absoluteDirOf(chr);
        if (abs && dir != null) {
            nextState = new KeyboardDrawing(castref, dir);
        } else if (!abs && KeyBinds.relativeAngleOf(chr) != null) {
            nextState = new KeyboardDrawing(castref,
                    List.of(KeyBinds.relativeAngleOf(chr)));
        }
    }

    @Override
    public void onKeyPress(int keyCode, int modifiers) {
        if (KeyBinds.keyMatches(KeyBinds.Action.AUTOCOMPLETE, keyCode, modifiers)
                && Hexcessible.cfg().autoComplete.allow) {
            var pos = castref.pxToCoord(mousePos);
            nextState = new AutoCompleting(castref, pos);
        }
        if (KeyBinds.keyMatches(KeyBinds.Action.ALIAS, keyCode, modifiers)
                && hoveredOverEntry != null)
            nextState = new AliasChanging(castref, hoveredOverEntry);
    }

    @Override
    public void onRender(GuiGraphics ctx, int mx, int my) {
        mousePos = new Vec2((float) mx, (float) my);
        var hovered = castref.getPatternAt(mx, my);
        if (hovered == null) {
            hoveredOver = null;
            hoveredOverEntry = null;
        } else if (hovered != hoveredOver) {
            hoveredOverStart = System.currentTimeMillis();
            hoveredOver = hovered;
            hoveredOverEntry = PatternEntries.INSTANCE.getFromSig(hovered.getAngles());
        } else if (hoveredOverStart + 500 < System.currentTimeMillis()) {
            KeyboardDrawing.render(ctx, mx, my, hovered.getAngles(), false,
                    Hexcessible.cfg().idle.tooltip, 0);
        }
    }

    /*
     * @Override
     * public void onRender(GuiGraphics ctx, int mx, int my) {
     * var allDrawMethodsDisabled = !Hexcessible.cfg().keyboardDraw.allow
     * && !Hexcessible.cfg().mouseDraw.allow
     * && !Hexcessible.cfg().autoComplete.allow;
     * var tr = Minecraft.getInstance().font;
     * if (allDrawMethodsDisabled)
     * ctx.drawCenteredString(tr,
     * Component.translatable("hexcessible.no_draw_methods"),
     * ctx.guiWidth() / 2, ctx.guiHeight() / 2, 16733525);
     * }
     */

    @Override
    public Map<String, String> getHints() {
        var keys = new HashMap<String, String>();

        if (Hexcessible.cfg().keyboardDraw.allow) {
            var drawKeys = Hexcessible.cfg().keyboardDraw.absoluteMode
                    ? KeyBinds.absoluteDrawLabel()
                    : KeyBinds.relativeDrawLabel();
            keys.put("lmb/" + drawKeys, "draw_start");
        } else {
            keys.put("lmb", "draw_start");
        }

        if (Hexcessible.cfg().autoComplete.allow)
            keys.put(KeyBinds.label(KeyBinds.Action.AUTOCOMPLETE), "auto_complete");
        if (hoveredOverEntry != null)
            keys.put(KeyBinds.label(KeyBinds.Action.ALIAS), "alias");
        if (Hexcessible.cfg().keyboardDraw.allow)
            keys.put(KeyBinds.label(KeyBinds.Action.MODE_TOGGLE), "mode");
        if (Hexcessible.cfg().keyDocs != dev.tizu.hexcessible.HexcessibleConfig.KeyDocs.OFF)
            keys.put(KeyBinds.label(KeyBinds.Action.DOCS), "docs");

        return keys;
    }
}
