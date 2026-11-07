package org.lwjgl.sdl;

import java.nio.ByteBuffer;

/** klsdl: surfaces exist only to carry window icons, which klsdl ignores; a struct that points at the caller's pixels. */
public class SDLSurface {
    protected SDLSurface() { throw new UnsupportedOperationException(); }

    public static SDL_Surface SDL_CreateSurfaceFrom(int width, int height, int format, ByteBuffer pixels, int pitch) {
        return SDL_Surface.calloc().flags(0).format(format).w(width).h(height).pitch(pitch).pixels(pixels).refcount(1);
    }
    public static void SDL_DestroySurface(SDL_Surface surface) { if (surface != null) surface.free(); }
    public static boolean SDL_AddSurfaceAlternateImage(SDL_Surface surface, SDL_Surface image) { return true; }
}
