package dev.tizu.hexcessible.keybinds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexDir;
import dev.tizu.hexcessible.Hexcessible;

/**
 * Rebindable shortcuts for the Hexcessible in-casting UI.
 *
 * <p>
 * Every shortcut is an {@link Action}. Bindings are either:
 * <ul>
 * <li><b>CHAR</b> — letters/printables that flow through
 * {@code KeyboardHandler#charTyped} (layout dependent, exactly like the
 * upstream hard-coded q/w/e/a/d keys). The bound value is a single
 * character, matched case-insensitively.</li>
 * <li><b>KEY</b> — physical keys (GLFW key code) with an optional
 * ctrl/shift/alt modifier mask, matched in the screens' {@code keyPressed}.
 * </li>
 * </ul>
 * Bindings are persisted in {@link HexcessibleConfig#keybinds} as a map of
 * {@code <actionName> -> <"c:&lt;char&gt;" | "k:&lt;keycode&gt;:&lt;modmask&gt;">}.
 * Missing entries fall back to the defaults declared on the actions.
 */
public class KeyBinds {
    private KeyBinds() {
    }

    public static final int MOD_MASK = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT;

    public enum Kind {
        CHAR, KEY
    }

    public enum Action {
        // ---- Keyboard drawing, relative mode (letters) ----
        KBD_LEFT(Kind.CHAR, 'q'),
        KBD_FORWARD(Kind.CHAR, 'w'),
        KBD_RIGHT(Kind.CHAR, 'e'),
        KBD_LEFT_BACK(Kind.CHAR, 'a'),
        KBD_RIGHT_BACK(Kind.CHAR, 'd'),
        KBD_UNDO(Kind.CHAR, 's'),

        // ---- Keyboard drawing, absolute mode (letters) ----
        ABS_WEST(Kind.CHAR, 'q'),
        ABS_NORTH_WEST(Kind.CHAR, 'w'),
        ABS_NORTH_EAST(Kind.CHAR, 'e'),
        ABS_SOUTH_WEST(Kind.CHAR, 'a'),
        ABS_SOUTH_EAST(Kind.CHAR, 's'),
        ABS_EAST(Kind.CHAR, 'd'),

        // ---- Manipulating a staged drawing ----
        CONFIRM(Kind.KEY, GLFW.GLFW_KEY_ENTER, 0),
        MOVE_UP(Kind.KEY, GLFW.GLFW_KEY_K, 0),
        MOVE_DOWN(Kind.KEY, GLFW.GLFW_KEY_J, 0),
        MOVE_LEFT(Kind.KEY, GLFW.GLFW_KEY_H, 0),
        MOVE_RIGHT(Kind.KEY, GLFW.GLFW_KEY_L, 0),
        ROTATE_CW(Kind.KEY, GLFW.GLFW_KEY_R, 0),
        ROTATE_CCW(Kind.KEY, GLFW.GLFW_KEY_R, GLFW.GLFW_MOD_SHIFT),

        // ---- Global toggles ----
        MODE_TOGGLE(Kind.KEY, GLFW.GLFW_KEY_GRAVE_ACCENT, 0),
        DOCS(Kind.KEY, GLFW.GLFW_KEY_N, 0),

        // ---- Autocomplete / alias ----
        AUTOCOMPLETE(Kind.KEY, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_MOD_CONTROL),
        ALIAS(Kind.KEY, GLFW.GLFW_KEY_E, GLFW.GLFW_MOD_CONTROL),
        SCROLL_UP(Kind.KEY, GLFW.GLFW_KEY_UP, 0),
        SCROLL_DOWN(Kind.KEY, GLFW.GLFW_KEY_DOWN, 0),
        DEFS_LEFT(Kind.KEY, GLFW.GLFW_KEY_LEFT, 0),
        DEFS_RIGHT(Kind.KEY, GLFW.GLFW_KEY_RIGHT, 0);

        private final Kind kind;
        private final char defaultChar;
        private final int defaultKey;
        private final int defaultMods;

        Action(Kind kind, char defaultChar) {
            this(kind, defaultChar, -1, 0);
        }

        Action(Kind kind, int defaultKey, int defaultMods) {
            this(kind, (char) 0, defaultKey, defaultMods);
        }

        Action(Kind kind, char defaultChar, int defaultKey, int defaultMods) {
            this.kind = kind;
            this.defaultChar = defaultChar;
            this.defaultKey = defaultKey;
            this.defaultMods = defaultMods;
        }

        public Kind kind() {
            return kind;
        }
    }

    // ------------------------------------------------------------------
    // Querying bindings (read live from the config)
    // ------------------------------------------------------------------

    private static String defaultBinding(Action action) {
        return switch (action.kind) {
            case CHAR -> "c:" + action.defaultChar;
            case KEY -> "k:" + action.defaultKey + ":" + action.defaultMods;
        };
    }

    private static boolean isValid(Action action, String raw) {
        if (raw == null)
            return false;
        if (action.kind == Kind.CHAR)
            return raw.length() == 3 && raw.charAt(0) == 'c' && raw.charAt(1) == ':';
        try {
            var parts = raw.split(":");
            if (parts.length != 3 || !parts[0].equals("k"))
                return false;
            Integer.parseInt(parts[1]);
            Integer.parseInt(parts[2]);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String binding(Action action) {
        var raw = Hexcessible.cfg().keybinds.get(action.name());
        return isValid(action, raw) ? raw : defaultBinding(action);
    }

    /** Current bound character for a CHAR action (lowercased). */
    public static char charOf(Action action) {
        var raw = binding(action);
        if (action.kind == Kind.CHAR && raw.length() == 3)
            return Character.toLowerCase(raw.charAt(2));
        return action.defaultChar;
    }

    /** Current bound key code for a KEY action. */
    public static int keyOf(Action action) {
        var raw = binding(action);
        if (action.kind == Kind.KEY) {
            var parts = raw.split(":");
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
            }
        }
        return action.defaultKey;
    }

    /** Current bound modifier mask for a KEY action. */
    public static int modsOf(Action action) {
        var raw = binding(action);
        if (action.kind == Kind.KEY) {
            var parts = raw.split(":");
            try {
                return Integer.parseInt(parts[2]) & MOD_MASK;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
            }
        }
        return action.defaultMods;
    }

    /** True if this KEY action fires for the given key code + modifier state. */
    public static boolean keyMatches(Action action, int keyCode, int modifiers) {
        if (action.kind != Kind.KEY)
            return false;
        if (keyCode != keyOf(action))
            return false;
        return (modifiers & MOD_MASK) == modsOf(action);
    }

    /** True if this CHAR action fires for the given typed character. */
    public static boolean charMatches(Action action, char chr) {
        if (action.kind != Kind.CHAR)
            return false;
        return Character.toLowerCase(chr) == charOf(action);
    }

    /**
     * Whether the typed character is a valid "start drawing" letter in the
     * given drawing mode (checks against the user's bindings).
     */
    public static boolean isDrawChar(char chr, boolean absoluteMode) {
        return drawCharAction(chr, absoluteMode) != null;
    }

    /** Maps a typed character to the draw action it triggers in this mode. */
    @Nullable
    public static Action drawCharAction(char chr, boolean absoluteMode) {
        if (absoluteMode) {
            for (var a : ABS_ACTIONS)
                if (charMatches(a, chr))
                    return a;
            return null;
        }
        for (var a : REL_DIR_ACTIONS)
            if (charMatches(a, chr))
                return a;
        return null;
    }

    /** The undo char action in relative mode (KBD_UNDO) if the char matches. */
    public static boolean isRelativeUndoChar(char chr) {
        return charMatches(Action.KBD_UNDO, chr);
    }

    // ------------------------------------------------------------------
    // Relative-mode: angle of the key pressed, and key of an angle
    // ------------------------------------------------------------------

    public static final List<Action> REL_DIR_ACTIONS = List.of(
            Action.KBD_LEFT, Action.KBD_FORWARD, Action.KBD_RIGHT,
            Action.KBD_LEFT_BACK, Action.KBD_RIGHT_BACK);

    /**
     * Maps a relative-mode draw character to the relative angle it draws
     * (mirrors the hard-coded Utils.angle mapping).
     */
    @Nullable
    public static HexAngle relativeAngleOf(char chr) {
        for (var a : REL_DIR_ACTIONS) {
            if (charMatches(a, chr))
                return switch (a) {
                    case KBD_LEFT -> HexAngle.LEFT;
                    case KBD_FORWARD -> HexAngle.FORWARD;
                    case KBD_RIGHT -> HexAngle.RIGHT;
                    case KBD_LEFT_BACK -> HexAngle.LEFT_BACK;
                    case KBD_RIGHT_BACK -> HexAngle.RIGHT_BACK;
                    default -> null;
                };
        }
        return null;
    }

    /** The current key character shown for a relative angle (markers/tooltips). */
    public static char keyCharForAngle(HexAngle angle) {
        return switch (angle) {
            case LEFT -> charOf(Action.KBD_LEFT);
            case FORWARD -> charOf(Action.KBD_FORWARD);
            case RIGHT -> charOf(Action.KBD_RIGHT);
            case LEFT_BACK -> charOf(Action.KBD_LEFT_BACK);
            case BACK -> charOf(Action.KBD_UNDO);
            case RIGHT_BACK -> charOf(Action.KBD_RIGHT_BACK);
        };
    }

    // ------------------------------------------------------------------
    // Absolute mode: direction of the key pressed, and key of a direction
    // ------------------------------------------------------------------

    public static final List<Action> ABS_ACTIONS = List.of(
            Action.ABS_WEST, Action.ABS_NORTH_WEST, Action.ABS_NORTH_EAST,
            Action.ABS_SOUTH_EAST, Action.ABS_SOUTH_WEST, Action.ABS_EAST);

    /** Absolute (screen-space) direction drawn by this char, or null. */
    @Nullable
    public static HexDir absoluteDirOf(char chr) {
        for (var a : ABS_ACTIONS) {
            if (charMatches(a, chr))
                return switch (a) {
                    case ABS_WEST -> HexDir.WEST;
                    case ABS_NORTH_WEST -> HexDir.NORTH_WEST;
                    case ABS_NORTH_EAST -> HexDir.NORTH_EAST;
                    case ABS_SOUTH_WEST -> HexDir.SOUTH_WEST;
                    case ABS_SOUTH_EAST -> HexDir.SOUTH_EAST;
                    case ABS_EAST -> HexDir.EAST;
                    default -> null;
                };
        }
        return null;
    }

    /** The current key character drawn as the label of an absolute direction. */
    public static char keyCharForDir(HexDir dir) {
        return switch (dir) {
            case EAST -> charOf(Action.ABS_EAST);
            case NORTH_EAST -> charOf(Action.ABS_NORTH_EAST);
            case NORTH_WEST -> charOf(Action.ABS_NORTH_WEST);
            case WEST -> charOf(Action.ABS_WEST);
            case SOUTH_WEST -> charOf(Action.ABS_SOUTH_WEST);
            case SOUTH_EAST -> charOf(Action.ABS_SOUTH_EAST);
        };
    }

    // ------------------------------------------------------------------
    // Labels (for hint panels / config screen)
    // ------------------------------------------------------------------

    /** Human readable label for an action's current binding, e.g. "Ctrl+Space". */
    public static String label(Action action) {
        return switch (action.kind) {
            case CHAR -> String.valueOf(Character.toUpperCase(charOf(action)));
            case KEY -> describe(keyOf(action), modsOf(action));
        };
    }

    public static String describe(int keyCode, int mods) {
        var sb = new StringBuilder();
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0)
            sb.append("Ctrl+");
        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0)
            sb.append("Shift+");
        if ((mods & GLFW.GLFW_MOD_ALT) != 0)
            sb.append("Alt+");
        sb.append(keyName(keyCode));
        return sb.toString();
    }

    private static String keyName(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z)
            return String.valueOf((char) ('A' + keyCode - GLFW.GLFW_KEY_A));
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9)
            return String.valueOf((char) ('0' + keyCode - GLFW.GLFW_KEY_0));
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_KP_ENTER -> "Num Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "Key#" + keyCode;
        };
    }

    // ------------------------------------------------------------------
    // Mutating bindings (config screen)
    // ------------------------------------------------------------------

    public static void setCharBinding(Action action, char chr) {
        if (action.kind != Kind.CHAR)
            throw new IllegalArgumentException("Not a char action: " + action);
        setBinding(action, "c:" + Character.toLowerCase(chr));
    }

    public static void setKeyBinding(Action action, int keyCode, int modifiers) {
        if (action.kind != Kind.KEY)
            throw new IllegalArgumentException("Not a key action: " + action);
        setBinding(action, "k:" + keyCode + ":" + (modifiers & MOD_MASK));
    }

    public static void resetBinding(Action action) {
        setBinding(action, null);
    }

    public static void resetAll() {
        for (var action : Action.values())
            resetBinding(action);
    }

    private static void setBinding(Action action, @Nullable String raw) {
        var map = new HashMap<>(Hexcessible.cfg().keybinds);
        if (raw == null || raw.equals(defaultBinding(action)))
            map.remove(action.name());
        else
            map.put(action.name(), raw);
        Hexcessible.cfg().keybinds = map;
        Hexcessible.cfg().markDirty();
    }

    // ------------------------------------------------------------------
    // Hint strings used by the drawstates (left bottom panel)
    // ------------------------------------------------------------------

    /** "q/w/e/a/d" style label of the relative draw chars. */
    public static String relativeDrawLabel() {
        var chars = new ArrayList<String>();
        for (var a : REL_DIR_ACTIONS)
            chars.add(label(a));
        return String.join("/", chars);
    }

    /** "w/e/q/a/s/d" style label of the absolute draw chars. */
    public static String absoluteDrawLabel() {
        var chars = new ArrayList<String>();
        for (var a : ABS_ACTIONS)
            chars.add(label(a));
        return String.join("/", chars);
    }
}
