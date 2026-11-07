package org.lwjgl.sdl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;

import static org.lwjgl.system.MemoryUtil.*;

/** klsdl: SDL_free for the arrays klsdl hands out (all from MemoryUtil's allocator). */
public class SDLStdinc {
    protected SDLStdinc() { throw new UnsupportedOperationException(); }

    public static void SDL_free(long mem) { nmemFree(mem); }
    public static void SDL_free(ByteBuffer mem) { memFree(mem); }
    public static void SDL_free(IntBuffer mem) { memFree(mem); }
    public static void SDL_free(PointerBuffer mem) { memFree(mem); }
}
