package org.lwjgl.sdl;

/** klsdl: SDL_Init brings up libklsdl's EGL side for SDL_INIT_VIDEO; app metadata is accepted and dropped. */
public class SDLInit {
    protected SDLInit() { throw new UnsupportedOperationException(); }

    public static boolean SDL_Init(int flags) { return KlSdl.init(flags); }
    public static boolean SDL_InitSubSystem(int flags) { return KlSdl.init(flags); }
    public static void SDL_Quit() { KlSdl.quit(); }
    public static boolean SDL_SetAppMetadataProperty(CharSequence name, CharSequence value) { return true; }
}
