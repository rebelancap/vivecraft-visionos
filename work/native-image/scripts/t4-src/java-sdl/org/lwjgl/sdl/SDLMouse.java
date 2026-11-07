package org.lwjgl.sdl;

import java.nio.FloatBuffer;

/**
 * klsdl: position and buttons come from the ring. Relative mode is recorded as GLFW_CURSOR_DISABLED in libklsdl so the
 * shell's klglfw_get_cursor_mode sees the grab; warps update the position the shell reads back (klglfw_get_cursor_pos)
 * and send no motion event. Cursors are tokens: there is no pointer image to change.
 */
public class SDLMouse {
    protected SDLMouse() { throw new UnsupportedOperationException(); }

    private static final long DEFAULT_CURSOR = 1000;

    public static int SDL_GetMouseState(FloatBuffer x, FloatBuffer y) {
        if (x != null) x.put(x.position(), (float) KlSdl.cx);
        if (y != null) y.put(y.position(), (float) KlSdl.cy);
        return KlSdl.buttons;
    }
    public static int SDL_GetGlobalMouseState(FloatBuffer x, FloatBuffer y) {
        // global = the game window's position + the window-relative position (one window, one display)
        if (x != null) x.put(x.position(), (float) (KlSdl.cx + KlSdl.mainX()));
        if (y != null) y.put(y.position(), (float) (KlSdl.cy + KlSdl.mainY()));
        return KlSdl.buttons;
    }
    public static boolean SDL_SetWindowRelativeMouseMode(long window, boolean enabled) { KlSdl.setRelative(window, enabled); return true; }
    public static boolean SDL_GetWindowRelativeMouseMode(long window) { return KlSdl.relative; }
    public static void SDL_WarpMouseInWindow(long window, float x, float y) { KlSdl.warp(window, x, y); }
    public static long SDL_CreateSystemCursor(int id) { return id + 1L; }
    public static long SDL_GetDefaultCursor() { return DEFAULT_CURSOR; }
    public static boolean SDL_SetCursor(long cursor) { return true; }
    public static void SDL_DestroyCursor(long cursor) { }
}
