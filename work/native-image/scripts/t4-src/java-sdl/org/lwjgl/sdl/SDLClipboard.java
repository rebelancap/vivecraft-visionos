package org.lwjgl.sdl;

/** klsdl: an in-process clipboard (the headset shell has no system clipboard hook). */
public class SDLClipboard {
    protected SDLClipboard() { throw new UnsupportedOperationException(); }

    public static String SDL_GetClipboardText() { synchronized (KlSdl.class) { return KlSdl.clipboard; } }
    public static boolean SDL_SetClipboardText(CharSequence text) {
        synchronized (KlSdl.class) { KlSdl.clipboard = text == null ? "" : text.toString(); }
        return true;
    }
    public static boolean SDL_HasClipboardText() { synchronized (KlSdl.class) { return !KlSdl.clipboard.isEmpty(); } }
}
