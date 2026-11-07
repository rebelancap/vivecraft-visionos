package org.lwjgl.sdl;

/** klsdl: the last error any klsdl call reported ("" when none, as SDL). */
public class SDLError {
    protected SDLError() { throw new UnsupportedOperationException(); }

    public static String SDL_GetError() { return KlSdl.error(); }
    public static boolean SDL_ClearError() { KlSdl.clearError(); return true; }
}
