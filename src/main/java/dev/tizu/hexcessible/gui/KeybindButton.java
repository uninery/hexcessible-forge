package dev.tizu.hexcessible.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The "change keybindings" button drawn in the bottom-right corner of the
 * spellcasting interface. Drawn last so it always stays reachable; click
 * handling lives in the DrawStateMixin mouse hook.
 */
public final class KeybindButton {
    private KeybindButton() {
    }

    public static boolean isOver(int mx, int my, int screenWidth, int screenHeight) {
        var r = rect(screenWidth, screenHeight);
        return mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
    }

    public static void render(GuiGraphics ctx, int mx, int my) {
        var r = rect(ctx.guiWidth(), ctx.guiHeight());
        var hovered = mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
        ctx.fill(r[0], r[1], r[2], r[3], hovered ? 0x80_3d3f52 : 0x60_15121c);
        ctx.fill(r[0], r[1], r[2], r[1] + 1, hovered ? 0xff_f5d76e : 0x66_6e738d);
        ctx.fill(r[0], r[3] - 1, r[2], r[3], hovered ? 0xff_f5d76e : 0x66_6e738d);
        ctx.fill(r[0], r[1], r[0] + 1, r[3], hovered ? 0xff_f5d76e : 0x66_6e738d);
        ctx.fill(r[2] - 1, r[1], r[2], r[3], hovered ? 0xff_f5d76e : 0x66_6e738d);
        var label = Component.translatable("hexcessible.ui.keybind");
        var font = Minecraft.getInstance().font;
        ctx.drawCenteredString(font, label, (r[0] + r[2]) / 2,
                r[1] + (r[3] - r[1]) / 2 - 4, hovered ? 0xff_ffffff : 0xff_c9c2d6);
    }

    private static int[] rect(int guiWidth, int guiHeight) {
        var label = Component.translatable("hexcessible.ui.keybind");
        var w = Minecraft.getInstance().font.width(label) + 14;
        var x0 = guiWidth - w - 6;
        var y0 = guiHeight - 20;
        return new int[] { x0, y0, guiWidth - 6, guiHeight - 6 };
    }
}
