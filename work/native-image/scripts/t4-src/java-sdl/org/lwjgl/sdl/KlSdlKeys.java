package org.lwjgl.sdl;

/**
 * klsdl key tables: GLFW key codes (what the app shell and KLGLFW_SCRIPT push) -> SDL3 scancodes, SDL scancode ->
 * US-layout SDL keycode (SDL_GetKeyFromScancode: the default US keymap SDL uses when the OS has no other), and the
 * SDL3 key/scancode names (SDL_keyboard.c's tables, for SDL_GetKeyName / SDL_GetScancodeName).
 */
final class KlSdlKeys {
    private KlSdlKeys() { }

    /** GLFW_KEY_* -> SDL_SCANCODE_*; 0 (SDL_SCANCODE_UNKNOWN) for keys SDL has no scancode for. */
    static int scancodeOfGlfw(int k) {
        if (k >= 65 && k <= 90) return 4 + (k - 65);         // A..Z
        if (k >= 49 && k <= 57) return 30 + (k - 49);        // 1..9
        if (k >= 290 && k <= 301) return 58 + (k - 290);     // F1..F12
        if (k >= 302 && k <= 313) return 104 + (k - 302);    // F13..F24
        if (k >= 321 && k <= 329) return 89 + (k - 321);     // KP_1..KP_9
        switch (k) {
            case 48: return 39;   case 32: return 44;   case 39: return 52;   case 44: return 54;   case 45: return 45;
            case 46: return 55;   case 47: return 56;   case 59: return 51;   case 61: return 46;   case 91: return 47;
            case 92: return 49;   case 93: return 48;   case 96: return 53;   case 161: return 100; // WORLD_1 = NONUSBACKSLASH
            case 256: return 41;  case 257: return 40;  case 258: return 43;  case 259: return 42;  case 260: return 73;
            case 261: return 76;  case 262: return 79;  case 263: return 80;  case 264: return 81;  case 265: return 82;
            case 266: return 75;  case 267: return 78;  case 268: return 74;  case 269: return 77;  case 280: return 57;
            case 281: return 71;  case 282: return 83;  case 283: return 70;  case 284: return 72;  case 320: return 98;
            case 330: return 99;  case 331: return 84;  case 332: return 85;  case 333: return 86;  case 334: return 87;
            case 335: return 88;  case 336: return 103;
            case 340: return 225; case 341: return 224; case 342: return 226; case 343: return 227;
            case 344: return 229; case 345: return 228; case 346: return 230; case 347: return 231; case 348: return 101;
            default: return 0;
        }
    }

    /** GLFW_MOUSE_BUTTON_n (0 left, 1 right, 2 middle, 3.. extra) -> SDL_BUTTON_* (1 left, 2 middle, 3 right, 4 X1, ...). */
    static int buttonOfGlfw(int b) {
        switch (b) { case 0: return 1; case 1: return 3; case 2: return 2; default: return b + 1; }
    }

    private static final String DIGITS_SHIFTED = "!@#$%^&*()";

    /**
     * SDL_GetKeyFromScancode on a US keymap. key_event = true gives the keycode SDL puts in key events (unshifted:
     * SDL_HINT_KEYCODE_OPTIONS' default); false applies Shift / Caps Lock from modstate.
     */
    static int keycode(int sc, int mod, boolean keyEvent) {
        boolean shift = !keyEvent && (mod & (KlSdl.KMOD_LSHIFT | KlSdl.KMOD_RSHIFT)) != 0;
        boolean caps = !keyEvent && (mod & KlSdl.KMOD_CAPS) != 0;
        if (sc >= 4 && sc <= 29) return (shift ^ caps ? 'A' : 'a') + (sc - 4);
        if (sc >= 30 && sc <= 39) {
            int i = sc - 30;   // 0..8 = '1'..'9', 9 = '0'
            return shift ? DIGITS_SHIFTED.charAt(i) : (i == 9 ? '0' : '1' + i);
        }
        switch (sc) {
            case 40: return '\r';
            case 41: return 0x1B;
            case 42: return '\b';
            case 43: return '\t';
            case 44: return ' ';
            case 45: return shift ? '_' : '-';
            case 46: return shift ? '+' : '=';
            case 47: return shift ? '{' : '[';
            case 48: return shift ? '}' : ']';
            case 49: return shift ? '|' : '\\';
            case 50: return shift ? '~' : '#';
            case 51: return shift ? ':' : ';';
            case 52: return shift ? '"' : '\'';
            case 53: return shift ? '~' : '`';
            case 54: return shift ? '<' : ',';
            case 55: return shift ? '>' : '.';
            case 56: return shift ? '?' : '/';
            case 76: return 0x7F;
            default: return sc > 0 && sc < KlSdl.SCANCODE_COUNT ? sc | KlSdl.KEYCODE_SCANCODE_MASK : 0;
        }
    }

    /** SDL_GetKeyName: the upper-case character for a character key, else the key's SDL name. */
    static String keyName(int key) {
        if ((key & KlSdl.KEYCODE_SCANCODE_MASK) != 0) return scancodeName(key & ~KlSdl.KEYCODE_SCANCODE_MASK);
        switch (key) {
            case 0: return "";
            case '\r': return "Return";
            case 0x1B: return "Escape";
            case '\b': return "Backspace";
            case '\t': return "Tab";
            case ' ': return "Space";
            case 0x7F: return "Delete";
            default:
                if (key < 0x20 || !Character.isValidCodePoint(key)) return "";
                return new String(Character.toChars(key)).toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static final String[] NAMES = new String[KlSdl.SCANCODE_COUNT];
    static {
        for (int i = 0; i < 26; i++) NAMES[4 + i] = String.valueOf((char) ('A' + i));
        for (int i = 0; i < 9; i++) NAMES[30 + i] = String.valueOf((char) ('1' + i));
        NAMES[39] = "0";
        String[] a = { "Return", "Escape", "Backspace", "Tab", "Space", "-", "=", "[", "]", "\\", "#", ";", "'", "`", ",", ".",
                       "/", "CapsLock" };
        for (int i = 0; i < a.length; i++) NAMES[40 + i] = a[i];
        for (int i = 0; i < 12; i++) NAMES[58 + i] = "F" + (i + 1);
        String[] b = { "PrintScreen", "ScrollLock", "Pause", "Insert", "Home", "PageUp", "Delete", "End", "PageDown", "Right",
                       "Left", "Down", "Up", "Numlock", "Keypad /", "Keypad *", "Keypad -", "Keypad +", "Keypad Enter" };
        for (int i = 0; i < b.length; i++) NAMES[70 + i] = b[i];
        for (int i = 0; i < 9; i++) NAMES[89 + i] = "Keypad " + (i + 1);
        NAMES[98] = "Keypad 0"; NAMES[99] = "Keypad ."; NAMES[100] = "NonUSBackslash"; NAMES[101] = "Application";
        NAMES[102] = "Power"; NAMES[103] = "Keypad =";
        for (int i = 0; i < 12; i++) NAMES[104 + i] = "F" + (i + 13);
        String[] m = { "Left Ctrl", "Left Shift", "Left Alt", "Left GUI", "Right Ctrl", "Right Shift", "Right Alt", "Right GUI" };
        for (int i = 0; i < m.length; i++) NAMES[224 + i] = m[i];
    }

    /** SDL_GetScancodeName: "" for scancodes this table does not name (as SDL does for unnamed ones). */
    static String scancodeName(int sc) {
        return sc >= 0 && sc < NAMES.length && NAMES[sc] != null ? NAMES[sc] : "";
    }
}
