package dev.tizu.hexcessible.gui;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import dev.tizu.hexcessible.keybinds.KeyBinds;
import dev.tizu.hexcessible.keybinds.KeyBinds.Action;
import dev.tizu.hexcessible.keybinds.KeyBinds.Kind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-casting shortcut configuration screen. One row per rebindable function;
 * click a row, then press the new key(s). Esc cancels the capture, Delete
 * restores the default. Opened from the button in the bottom-right corner of
 * the spellcasting interface.
 */
public class KeyConfigScreen extends Screen {
    private final Screen parent;

    private int scroll = 0;
    private @Nullable Action capturing = null;
    private boolean captureCharAction = false;

    private final List<Row> rows = new ArrayList<>();
    private int totalContentHeight = 0;

    private record Group(String title, List<Action> actions) {
    }

    private record Row(@Nullable Group group, int groupIndex, int indexInGroup,
            @Nullable Action action) {
    }

    private static final List<Group> GROUPS = List.of(
            new Group("rel", List.of(Action.KBD_LEFT, Action.KBD_FORWARD,
                    Action.KBD_RIGHT, Action.KBD_LEFT_BACK,
                    Action.KBD_RIGHT_BACK, Action.KBD_UNDO)),
            new Group("abs", List.of(Action.ABS_WEST, Action.ABS_NORTH_WEST,
                    Action.ABS_NORTH_EAST, Action.ABS_SOUTH_WEST,
                    Action.ABS_SOUTH_EAST, Action.ABS_EAST)),
            new Group("draw", List.of(Action.CONFIRM, Action.MOVE_UP,
                    Action.MOVE_DOWN, Action.MOVE_LEFT, Action.MOVE_RIGHT,
                    Action.ROTATE_CW, Action.ROTATE_CCW)),
            new Group("global", List.of(Action.MODE_TOGGLE, Action.DOCS,
                    Action.AUTOCOMPLETE, Action.ALIAS, Action.SCROLL_UP,
                    Action.SCROLL_DOWN, Action.DEFS_LEFT, Action.DEFS_RIGHT)));

    private static final int ROW_H = 21;
    private static final int GROUP_H = 16;

    public KeyConfigScreen(Screen parent) {
        super(Component.translatable("hexcessible.keybind.title"));
        this.parent = parent;
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        for (int g = 0; g < GROUPS.size(); g++) {
            var group = GROUPS.get(g);
            for (int i = 0; i < group.actions().size(); i++)
                rows.add(new Row(group, g, i, group.actions().get(i)));
        }
        totalContentHeight = 0;
        for (var row : rows) {
            if (isFirstOfGroup(row))
                totalContentHeight += GROUP_H;
            totalContentHeight += ROW_H;
        }
    }

    private boolean isFirstOfGroup(Row row) {
        return row.indexInGroup() == 0;
    }

    private int yFor(Row row) {
        int y = 0;
        for (var r : rows) {
            if (isFirstOfGroup(r))
                y += GROUP_H;
            if (r == row)
                return y;
            y += ROW_H;
        }
        return y;
    }

    @Override
    public void init() {
        var done = Button.builder(
                Component.translatable("hexcessible.keybind.done"),
                btn -> onClose())
                .bounds(this.width / 2 + 8, 8, 120, 20)
                .build();
        var reset = Button.builder(
                Component.translatable("hexcessible.keybind.reset_all"),
                btn -> {
                    KeyBinds.resetAll();
                    capturing = null;
                })
                .bounds(this.width / 2 - 128, 8, 120, 20)
                .build();
        addRenderableWidget(reset);
        addRenderableWidget(done);
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        super.render(ctx, mx, my, delta);
        var tr = Minecraft.getInstance().font;

        ctx.drawCenteredString(tr, this.title, this.width / 2, 34, 0xFFFFFF);

        if (capturing != null) {
            var hint = captureCharAction
                    ? Component.translatable("hexcessible.keybind.capture_hint_char",
                            actionTitle(capturing))
                    : Component.translatable("hexcessible.keybind.capture_hint",
                            actionTitle(capturing));
            ctx.drawCenteredString(tr, hint, this.width / 2, 48, 0xff_f5d76e);
        } else {
            ctx.drawCenteredString(tr,
                    Component.translatable("hexcessible.keybind.click_hint")
                            .withStyle(ChatFormatting.DARK_GRAY),
                    this.width / 2, 48, 0);
        }

        var topY = 62;
        var bottomY = this.height - 22;
        int y = topY - scroll;
        for (var row : rows) {
            if (isFirstOfGroup(row)) {
                if (y >= topY - GROUP_H && y < bottomY)
                    ctx.drawString(tr,
                            Component.translatable("hexcessible.keybind.group." + row.group().title())
                                    .withStyle(ChatFormatting.GRAY),
                            24, y, 0xFFFFFF);
                y += GROUP_H;
                if (y - ROW_H > bottomY)
                    break;
            }
            if (y >= topY - ROW_H && y < bottomY)
                drawRow(ctx, row, y, mx, my);
            y += ROW_H;
            if (y > bottomY + ROW_H)
                break;
        }
    }

    private void drawRow(GuiGraphics ctx, Row row, int y, int mx, int my) {
        var tr = Minecraft.getInstance().font;
        var action = row.action();
        var selected = capturing == action;
        var hovered = mx >= 16 && mx <= this.width - 16 && my >= y && my < y + ROW_H;
        int bg = selected ? 0x80_3d3f52
                : hovered ? 0x40_3d3f52 : 0x20_000000;
        ctx.fill(16, y + 2, this.width - 16, y + ROW_H, bg);

        var name = Component.translatable("hexcessible.keybind.action." + action.name() + ".name");
        var key = Component.literal(KeyBinds.label(action))
                .withStyle(selected ? ChatFormatting.GOLD
                        : ChatFormatting.YELLOW);
        ctx.drawString(tr, name, 24, y + 6, 0xFFFFFF);
        ctx.drawString(tr, key, this.width - 40 - tr.width(key), y + 6, 0xFFFFFF);
        if (selected) {
            ctx.fill(16, y + ROW_H - 1, this.width - 16, y + ROW_H, 0xff_f5d76e);
        }
    }

    private String actionTitle(Action action) {
        return Component.translatable(
                "hexcessible.keybind.action." + action.name() + ".name").getString();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            var row = rowAt((int) my);
            if (row != null) {
                var action = row.action();
                if (capturing == action) {
                    capturing = null;
                } else {
                    capturing = action;
                    captureCharAction = action.kind() == Kind.CHAR;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Nullable
    private Row rowAt(int my) {
        if (my < 62 || my >= this.height - 22)
            return null;
        int y = 62 - scroll;
        for (var row : rows) {
            if (isFirstOfGroup(row))
                y += GROUP_H;
            if (my >= y + 2 && my < y + ROW_H)
                return row;
            y += ROW_H;
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        var maxScroll = Math.max(0,
                (GROUP_H * GROUPS.size() + rows.size() * ROW_H) - (this.height - 84));
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - delta * 14));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (capturing != null && captureCharAction) {
            if (Character.isISOControl(chr))
                return true;
            KeyBinds.setCharBinding(capturing, chr);
            capturing = null;
            return true;
        }
        return super.charTyped(chr, mods);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturing = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                KeyBinds.resetBinding(capturing);
                capturing = null;
                return true;
            }
            if (!captureCharAction) {
                // ignore pure modifier presses
                var pureMod = switch (keyCode) {
                    case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
                            GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
                            GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
                            GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> true;
                    default -> false;
                };
                if (!pureMod) {
                    KeyBinds.setKeyBinding(capturing, keyCode, modifiers);
                    capturing = null;
                    return true;
                }
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        var mc = Minecraft.getInstance();
        mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
