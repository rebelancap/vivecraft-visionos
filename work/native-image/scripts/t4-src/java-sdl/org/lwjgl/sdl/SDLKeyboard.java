package org.lwjgl.sdl;

import java.nio.ByteBuffer;

/**
 * klsdl: keyboard state comes from the ring (KlSdl.pump), keycodes from a US keymap (KlSdlKeys). Text input is a flag:
 * between SDL_StartTextInput and SDL_StopTextInput character records become SDL_EVENT_TEXT_INPUT, otherwise they are
 * dropped (as SDL does). There is no IME, so the text input area and composition calls are accepted and ignored.
 */
public class SDLKeyboard {
    protected SDLKeyboard() { throw new UnsupportedOperationException(); }

    public static ByteBuffer SDL_GetKeyboardState() { return KlSdl.keyState; }
    public static short SDL_GetModState() { return (short) KlSdl.modState; }
    public static int SDL_GetKeyFromScancode(int scancode, short modstate, boolean key_event) {
        return KlSdlKeys.keycode(scancode, modstate & 0xFFFF, key_event);
    }
    public static String SDL_GetKeyName(int key) { return KlSdlKeys.keyName(key); }
    public static String SDL_GetScancodeName(int scancode) { return KlSdlKeys.scancodeName(scancode); }
    public static boolean SDL_StartTextInput(long window) {
        if (!KlSdl.textInput) KlSdl.log("text input on");
        KlSdl.textInput = true;
        return true;
    }
    public static boolean SDL_StopTextInput(long window) {
        if (KlSdl.textInput) KlSdl.log("text input off");
        KlSdl.textInput = false;
        return true;
    }
    public static boolean SDL_TextInputActive(long window) { return KlSdl.textInput; }
    public static boolean SDL_SetTextInputArea(long window, SDL_Rect.Buffer rect, int cursor) { return true; }
    public static boolean SDL_ClearComposition(long window) { return true; }
}
