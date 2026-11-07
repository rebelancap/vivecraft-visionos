package org.lwjgl.sdl;

/** klsdl: there is no SDL to log anything; the output function is kept and never called, priorities are ignored. */
public class SDLLog {
    protected SDLLog() { throw new UnsupportedOperationException(); }

    static volatile SDL_LogOutputFunctionI output;

    public static void SDL_SetLogOutputFunction(SDL_LogOutputFunctionI callback, long userdata) { output = callback; }
    public static void SDL_SetLogPriorities(int priority) { }
}
