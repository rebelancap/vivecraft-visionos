package org.lwjgl.sdl;

import java.util.HashMap;

import static org.lwjgl.system.MemoryUtil.*;

/**
 * klsdl: SDL_GetPixelFormatDetails for the 8-bit-per-channel 32-bit formats klsdl reports (display modes are
 * XRGB8888); like SDL, the details are cached and never freed. Other formats: null + error.
 */
public class SDLPixels {
    protected SDLPixels() { throw new UnsupportedOperationException(); }

    private static final HashMap<Integer, SDL_PixelFormatDetails> CACHE = new HashMap<>();

    public static synchronized SDL_PixelFormatDetails SDL_GetPixelFormatDetails(int format) {
        SDL_PixelFormatDetails d = CACHE.get(format);
        if (d != null) return d;
        int r, g, b, a;   // byte shifts of the channel masks (SDL "packed 32" layouts)
        switch (format) {
            case 0x16161804: r = 16; g = 8; b = 0; a = -1; break;   // XRGB8888
            case 0x16362004: r = 16; g = 8; b = 0; a = 24; break;   // ARGB8888
            case 0x16762004: r = 0; g = 8; b = 16; a = 24; break;   // ABGR8888
            case 0x16462004: r = 24; g = 16; b = 8; a = 0; break;   // RGBA8888
            default: KlSdl.fail("klsdl: no details for pixel format 0x" + Integer.toHexString(format)); return null;
        }
        long p = nmemCallocChecked(1, SDL_PixelFormatDetails.SIZEOF);
        memPutInt(p + SDL_PixelFormatDetails.FORMAT, format);
        memPutByte(p + SDL_PixelFormatDetails.BITS_PER_PIXEL, (byte) (a < 0 ? 24 : 32));
        memPutByte(p + SDL_PixelFormatDetails.BYTES_PER_PIXEL, (byte) 4);
        memPutInt(p + SDL_PixelFormatDetails.RMASK, 0xFF << r);
        memPutInt(p + SDL_PixelFormatDetails.GMASK, 0xFF << g);
        memPutInt(p + SDL_PixelFormatDetails.BMASK, 0xFF << b);
        memPutInt(p + SDL_PixelFormatDetails.AMASK, a < 0 ? 0 : 0xFF << a);
        memPutByte(p + SDL_PixelFormatDetails.RBITS, (byte) 8);
        memPutByte(p + SDL_PixelFormatDetails.GBITS, (byte) 8);
        memPutByte(p + SDL_PixelFormatDetails.BBITS, (byte) 8);
        memPutByte(p + SDL_PixelFormatDetails.ABITS, (byte) (a < 0 ? 0 : 8));
        memPutByte(p + SDL_PixelFormatDetails.RSHIFT, (byte) r);
        memPutByte(p + SDL_PixelFormatDetails.GSHIFT, (byte) g);
        memPutByte(p + SDL_PixelFormatDetails.BSHIFT, (byte) b);
        memPutByte(p + SDL_PixelFormatDetails.ASHIFT, (byte) (a < 0 ? 0 : a));
        d = SDL_PixelFormatDetails.create(p);
        CACHE.put(format, d);
        return d;
    }
}
