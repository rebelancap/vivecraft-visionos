package org.lwjgl.sdl;

import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.FunctionProvider;

import static org.lwjgl.system.MemoryUtil.*;

/**
 * klsdl video: a window is a libklsdl EGL surface (pbuffer on the Mac, kl_egl's window surface for the shown window
 * in the app), a GL context is an ES 3.0 context made through libltw (klsdl.c). There is one display, the size of
 * KLGLFW_MONITOR / KLSDL_MONITOR (default 1920x1080); windows cannot move, resize or go fullscreen for real -- the
 * calls record what was asked, and size requests are answered with the surface's real size (KlSdl.requestSize).
 */
public class SDLVideo {
    protected SDLVideo() { throw new UnsupportedOperationException(); }

    // ---- windows -------------------------------------------------------------------------------------------------
    public static long SDL_CreateWindow(CharSequence title, int w, int h, long flags) { return KlSdl.createWindow(title, w, h, flags); }
    public static void SDL_DestroyWindow(long window) { KlSdl.destroyWindow(window); }
    public static long SDL_GetWindowFlags(long window) { return KlSdl.windowFlags(window); }
    public static int SDL_GetWindowProperties(long window) { return KlSdl.win(window) == null ? 0 : 1; }
    public static float SDL_GetWindowPixelDensity(long window) { return KlSdl.win(window) == null ? 0f : 1f; }
    public static float SDL_GetWindowDisplayScale(long window) { return KlSdl.win(window) == null ? 0f : 1f; }

    public static boolean SDL_GetWindowSizeInPixels(long window, IntBuffer w, IntBuffer h) {
        if (KlSdl.win(window) == null) return false;
        long s = KlSdlNative.nGetWindowSize(window);
        if (w != null) w.put(w.position(), (int) (s >>> 32));
        if (h != null) h.put(h.position(), (int) s);
        return true;
    }
    public static boolean SDL_GetWindowSize(long window, IntBuffer w, IntBuffer h) { return SDL_GetWindowSizeInPixels(window, w, h); }
    public static boolean SDL_SetWindowSize(long window, int w, int h) { KlSdl.requestSize(window, w, h); return KlSdl.win(window) != null; }

    public static boolean SDL_GetWindowPosition(long window, IntBuffer x, IntBuffer y) {
        KlSdl.Win win = KlSdl.win(window);
        if (win == null) return false;
        if (x != null) x.put(x.position(), win.x);
        if (y != null) y.put(y.position(), win.y);
        return true;
    }
    public static boolean SDL_SetWindowPosition(long window, int x, int y) {
        KlSdl.Win win = KlSdl.win(window);
        if (win == null) return false;
        win.x = x; win.y = y;
        return true;
    }

    public static boolean SDL_SetWindowFullscreen(long window, boolean fullscreen) { KlSdl.setFullscreen(window, fullscreen); return KlSdl.win(window) != null; }
    /** Always "borderless desktop" fullscreen (null): there is no exclusive mode to report. */
    public static SDL_DisplayMode SDL_GetWindowFullscreenMode(long window) { return null; }
    public static boolean SDL_SetWindowFullscreenMode(long window, SDL_DisplayMode mode) { return KlSdl.win(window) != null; }

    public static boolean SDL_SetWindowTitle(long window, CharSequence title) { return KlSdl.win(window) != null; }
    public static boolean SDL_SetWindowIcon(long window, SDL_Surface icon) { return KlSdl.win(window) != null; }
    public static boolean SDL_SetWindowMinimumSize(long window, int min_w, int min_h) { return KlSdl.win(window) != null; }
    public static boolean SDL_SetWindowMaximumSize(long window, int max_w, int max_h) { return KlSdl.win(window) != null; }
    public static boolean SDL_SetWindowBordered(long window, boolean bordered) { return KlSdl.win(window) != null; }
    public static boolean SDL_SetWindowMouseGrab(long window, boolean grabbed) { return KlSdl.win(window) != null; }
    public static boolean SDL_RestoreWindow(long window) { return KlSdl.win(window) != null; }
    public static boolean SDL_ShowWindow(long window) { return KlSdl.win(window) != null; }
    public static boolean SDL_SyncWindow(long window) { return KlSdl.win(window) != null; }

    public static String SDL_GetCurrentVideoDriver() { return KlSdl.videoUp() ? "klsdl" : null; }

    // ---- displays ------------------------------------------------------------------------------------------------
    public static IntBuffer SDL_GetDisplays() { IntBuffer b = memAllocInt(1); b.put(0, KlSdl.DISPLAY_ID); return b; }
    public static int SDL_GetPrimaryDisplay() { return KlSdl.DISPLAY_ID; }
    public static int SDL_GetDisplayForWindow(long window) { return KlSdl.win(window) == null ? 0 : KlSdl.DISPLAY_ID; }
    public static String SDL_GetDisplayName(int displayID) { return KlSdl.checkDisplay(displayID) ? "Klepton" : null; }
    public static boolean SDL_GetDisplayBounds(int displayID, SDL_Rect rect) {
        if (!KlSdl.checkDisplay(displayID)) return false;
        rect.x(0).y(0).w(KlSdl.monitorW()).h(KlSdl.monitorH());
        return true;
    }
    public static SDL_DisplayMode SDL_GetDesktopDisplayMode(int displayID) { return KlSdl.checkDisplay(displayID) ? KlSdl.mode() : null; }
    public static SDL_DisplayMode SDL_GetCurrentDisplayMode(int displayID) { return KlSdl.checkDisplay(displayID) ? KlSdl.mode() : null; }
    /** One mode: the display's. The array is the caller's to SDL_free; the mode it points at is klsdl's. */
    public static PointerBuffer SDL_GetFullscreenDisplayModes(int displayID) {
        if (!KlSdl.checkDisplay(displayID)) return null;
        PointerBuffer b = memAllocPointer(1);
        b.put(0, KlSdl.mode().address());
        return b;
    }
    public static boolean SDL_GetClosestFullscreenDisplayMode(int displayID, int w, int h, float refresh_rate,
                                                              boolean include_high_density_modes, SDL_DisplayMode closest) {
        if (!KlSdl.checkDisplay(displayID)) return false;
        KlSdl.fillMode(closest);
        return true;
    }

    // ---- OpenGL --------------------------------------------------------------------------------------------------
    /** path = GL.getFunctionProvider()'s library (libltw): klsdl's EGL comes up on it if SDL_Init did not already. */
    public static boolean SDL_GL_LoadLibrary(CharSequence path) {
        if (KlSdl.videoUp()) return true;
        String p = path != null ? path.toString() : System.getProperty("org.lwjgl.opengl.libname");
        if (p == null) return KlSdl.fail("SDL_GL_LoadLibrary: no GL library (set -Dorg.lwjgl.opengl.libname=<libltw.dylib>)");
        return KlSdl.initVideo(p);
    }
    public static void SDL_GL_UnloadLibrary() { }
    /**
     * LWJGL's own GL function provider when it exists, so the address is exactly the one LWJGL resolves (26.3's
     * GlBackend compares the two for glGetError); libklsdl's lookup (the GL library's OSMesaGetProcAddress) before that.
     */
    public static long SDL_GL_GetProcAddress(CharSequence proc) {
        FunctionProvider fp = GL.getFunctionProvider();
        long a = fp != null ? fp.getFunctionAddress(proc) : NULL;
        return a != NULL ? a : KlSdlNative.nGetProcAddress(proc.toString());
    }
    public static boolean SDL_GL_SetAttribute(int attr, int value) {
        if (attr < 0 || attr >= KlSdl.GL_ATTR_COUNT) return KlSdl.fail("Unknown OpenGL attribute " + attr);
        KlSdl.glAttr[attr] = value;
        return true;
    }
    /**
     * The context is always ES 3.0 through libltw, which reports desktop GL 3.3 once 3.3+ was asked for
     * (LTW_REPORT_GL_VERSION, klsdl.c); the version attributes answer what was asked, the rest the EGL config
     * (RGBA8, D24, S8, double-buffered).
     */
    public static boolean SDL_GL_GetAttribute(int attr, IntBuffer value) {
        int v;
        switch (attr) {
            case 0: case 1: case 2: case 3: v = 8; break;   // RED/GREEN/BLUE/ALPHA_SIZE
            case 4: v = 32; break;                          // BUFFER_SIZE
            case 5: v = 1; break;                           // DOUBLEBUFFER
            case 6: v = 24; break;                          // DEPTH_SIZE
            case 7: v = 8; break;                           // STENCIL_SIZE
            case 15: v = 1; break;                          // ACCELERATED_VISUAL
            default:
                if (attr < 0 || attr >= KlSdl.GL_ATTR_COUNT) return KlSdl.fail("Unknown OpenGL attribute " + attr);
                v = KlSdl.glAttr[attr];
        }
        value.put(value.position(), v);
        return true;
    }
    public static long SDL_GL_CreateContext(long window) {
        if (KlSdl.win(window) == null) return NULL;
        long share = KlSdl.glAttr[KlSdl.GL_SHARE_WITH_CURRENT_CONTEXT] != 0 ? KlSdl.currentContext : NULL;
        long c = KlSdlNative.nCreateContext(window, KlSdl.glAttr[KlSdl.GL_CONTEXT_MAJOR_VERSION], KlSdl.glAttr[KlSdl.GL_CONTEXT_MINOR_VERSION], share);
        if (c == NULL) { KlSdl.failNative("SDL_GL_CreateContext"); return NULL; }
        KlSdl.currentContext = c;
        return c;
    }
    public static boolean SDL_GL_DestroyContext(long context) {
        if (KlSdl.currentContext == context) KlSdl.currentContext = NULL;
        return KlSdlNative.nDestroyContext(context) || KlSdl.failNative("SDL_GL_DestroyContext");
    }
    public static boolean SDL_GL_MakeCurrent(long window, long context) {
        if (!KlSdlNative.nMakeCurrent(window, context)) return KlSdl.failNative("SDL_GL_MakeCurrent");
        KlSdl.currentContext = context;
        return true;
    }
    public static boolean SDL_GL_SetSwapInterval(int interval) { KlSdlNative.nSwapInterval(interval); return true; }
    public static boolean SDL_GL_SwapWindow(long window) {
        if (KlSdl.win(window) == null) return false;
        KlSdlNative.nSwapWindow(window);
        return true;
    }
}
