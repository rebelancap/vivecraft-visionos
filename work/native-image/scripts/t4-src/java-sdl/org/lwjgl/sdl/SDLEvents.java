package org.lwjgl.sdl;

/** klsdl: the event queue is KlSdl's, filled from libklsdl's ring (KlSdl.pump). */
public class SDLEvents {
    protected SDLEvents() { throw new UnsupportedOperationException(); }

    public static void SDL_PumpEvents() { KlSdl.pump(); }
    /** event == null: is an event pending (left queued). */
    public static boolean SDL_PollEvent(SDL_Event event) { return KlSdl.poll(event); }
    public static void SDL_FlushEvents(int minType, int maxType) { KlSdl.flush(minType, maxType); }
    public static void SDL_FlushEvent(int type) { KlSdl.flush(type, type); }
    public static long SDL_GetWindowFromEvent(SDL_Event event) { return KlSdl.windowOf(event); }
}
