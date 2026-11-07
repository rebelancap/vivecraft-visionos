package org.lwjgl.sdl;

import org.lwjgl.system.Configuration;
import org.lwjgl.system.Library;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;

/**
 * JNI surface of libklsdl (scripts/t4-src/native/klsdl.c, which documents each native; everything not SDL-specific
 * is klglfw.c's). Plain downcalls only, so the native image needs no JNI configuration for it.
 * <p>
 * The library is -Dklsdl.libpath=&lt;file&gt; if set, else -Dorg.lwjgl.sdl.libname (the property stock LWJGL reads
 * for libSDL3), else libklsdl.dylib on LWJGL's library path (-Dorg.lwjgl.librarypath). It is loaded twice, as
 * klglfw's is once: System.load binds the natives below, and LWJGL's SharedLibrary for the same file is what
 * {@link SDL#getLibrary()} returns (NativeLibrariesBootstrap loads "SDL" eagerly and only checks it is non-null).
 */
public final class KlSdlNative {
    private KlSdlNative() { }

    private static SharedLibrary lib;

    static synchronized SharedLibrary load() {
        if (lib != null) return lib;
        String p = System.getProperty("klsdl.libpath");
        if (p == null) p = Configuration.SDL_LIBRARY_NAME.get(Platform.mapLibraryNameBundled("klsdl"));
        Library.loadSystem(System::load, System::loadLibrary, KlSdlNative.class, "org.lwjgl.sdl", p);
        lib = Library.loadNative(KlSdlNative.class, "org.lwjgl.sdl", p, true);
        return lib;
    }

    static native boolean nInit(String glLibrary);
    static native String nLastError();
    static native String nDescribe();
    static native void nTerminate();
    /** (w << 32) | h: KLGLFW_MONITOR / KLSDL_MONITOR, default 1920x1080. */
    static native long nGetMonitorSize();
    static native long nCreateWindow(int width, int height, boolean hidden);
    static native void nDestroyWindow(long window);
    /** (w << 32) | h of the window's surface. */
    static native long nGetWindowSize(long window);
    static native long nCreateContext(long window, int glMajor, int glMinor, long share);
    static native boolean nDestroyContext(long context);
    static native boolean nMakeCurrent(long window, long context);
    static native void nSwapWindow(long window);
    static native void nSwapInterval(int interval);
    static native long nGetProcAddress(String name);
    /** Copies up to capacity 48-byte GLFW-coded event records (klglfw.c struct klglfw_event) into buffer. */
    static native int nPollEvents(long buffer, int capacity);
    static native void nWaitEvents(double timeout);
    static native void nPostEmptyEvent();
    static native void nSetCursorPos(long window, double x, double y);
    static native void nSetInputMode(long window, int mode, int value);
    static native long nGetTimerValue();
    /** which: 0 EGLDisplay, 1 EGLContext, 2 EGLSurface, 3 EGLConfig. */
    static native long nGetEGLHandle(long window, int which);
}
