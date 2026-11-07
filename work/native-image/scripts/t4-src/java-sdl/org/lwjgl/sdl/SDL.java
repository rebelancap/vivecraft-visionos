package org.lwjgl.sdl;

import org.lwjgl.system.SharedLibrary;

/** klsdl: the "SDL" library is libklsdl (see KlSdlNative for how it is found). */
public final class SDL {
    private SDL() { throw new UnsupportedOperationException(); }

    public static SharedLibrary getLibrary() { return KlSdl.LIB; }
}
