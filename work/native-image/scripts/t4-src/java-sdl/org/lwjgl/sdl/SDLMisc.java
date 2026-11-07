package org.lwjgl.sdl;

/** klsdl: no URL handler (the shell has none for the guest); logged, and false so the caller logs its own warning. */
public class SDLMisc {
    protected SDLMisc() { throw new UnsupportedOperationException(); }

    public static boolean SDL_OpenURL(CharSequence url) {
        KlSdl.log("SDL_OpenURL(" + url + "): no URL handler on this host");
        return KlSdl.fail("klsdl: no URL handler");
    }
}
