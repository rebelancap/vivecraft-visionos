package org.lwjgl.sdl;

/** klsdl: monotonic time since klsdl was loaded (SDL counts from its own init). */
public class SDLTimer {
    protected SDLTimer() { throw new UnsupportedOperationException(); }

    public static long SDL_GetTicksNS() { return KlSdl.ticksNS(); }
    public static long SDL_GetTicks() { return KlSdl.ticksNS() / 1_000_000L; }
}
