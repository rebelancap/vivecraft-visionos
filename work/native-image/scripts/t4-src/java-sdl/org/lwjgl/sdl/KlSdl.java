/*
 * Klepton SDL3 stand-in (klsdl) for Minecraft 26.3+: the state behind the org.lwjgl.sdl.SDL* function classes of this
 * directory, which replace LWJGL's (the stock lwjgl-sdl struct and callback classes are kept: they are plain
 * MemoryUtil views with no native dependency). Native half: libklsdl (scripts/t4-src/native/klsdl.c = klglfw.c's EGL,
 * ring and screenshots + SDL's window/context split). Built by scripts/t2/build-klsdl.sh.
 *
 * Event path: the app shell and the KLGLFW_SCRIPT test input push GLFW-coded records into the native ring
 * (klglfw_push_event); pump() copies them out with ONE JNI call and turns each into SDL3 events here -- GLFW key ->
 * SDL scancode + US-layout keycode + SDL_Keymod, GLFW button -> SDL button, cursor position -> motion with xrel/yrel,
 * window size / focus / enter / close -> SDL window events. Keyboard state, mod state, mouse position and buttons
 * change at pump time, as SDL's do. SDL_PollEvent hands the queue out one SDL_Event at a time.
 *
 * Documented no-ops (each returns success unless noted): hints, app metadata, log output function/priorities, cursors
 * (handles are tokens), window title/icon/position/min/max size/border/grab/restore/sync, text input area and
 * composition, SDL_OpenURL (logs, returns false: no URL handler in the shell), message boxes (logged; the default
 * button is the answer), Vulkan (unavailable: false/0/null), window properties (an empty property set).
 * SDL_SetWindowSize cannot resize the surface: it answers with a resize event carrying the real size.
 */
package org.lwjgl.sdl;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.SharedLibrary;

import static org.lwjgl.system.MemoryUtil.*;

final class KlSdl {
    private KlSdl() { }

    static final SharedLibrary LIB = KlSdlNative.load();
    private static final long T0 = System.nanoTime();

    // ---- SDL3 constants used here (values checked against stock lwjgl-sdl 3.4.3) -------------------------------------
    static final int INIT_VIDEO = 0x20;
    static final long WINDOW_FULLSCREEN = 0x1, WINDOW_OPENGL = 0x2, WINDOW_HIDDEN = 0x8, WINDOW_MOUSE_GRABBED = 0x100,
        WINDOW_INPUT_FOCUS = 0x200, WINDOW_MOUSE_FOCUS = 0x400, WINDOW_MOUSE_RELATIVE_MODE = 0x8000;
    static final int EV_QUIT = 256, EV_WINDOW_EXPOSED = 516, EV_WINDOW_MOVED = 517, EV_WINDOW_RESIZED = 518,
        EV_WINDOW_PIXEL_SIZE_CHANGED = 519, EV_WINDOW_MINIMIZED = 521, EV_WINDOW_RESTORED = 523,
        EV_WINDOW_MOUSE_ENTER = 524, EV_WINDOW_MOUSE_LEAVE = 525, EV_WINDOW_FOCUS_GAINED = 526, EV_WINDOW_FOCUS_LOST = 527,
        EV_WINDOW_CLOSE_REQUESTED = 528, EV_WINDOW_ENTER_FULLSCREEN = 535, EV_WINDOW_LEAVE_FULLSCREEN = 536,
        EV_WINDOW_FIRST = 514, EV_WINDOW_LAST = 0x2FF,
        EV_KEY_DOWN = 768, EV_KEY_UP = 769, EV_TEXT_INPUT = 771,
        EV_MOUSE_MOTION = 1024, EV_MOUSE_BUTTON_DOWN = 1025, EV_MOUSE_BUTTON_UP = 1026, EV_MOUSE_WHEEL = 1027;
    static final int GL_ATTR_COUNT = 32, GL_CONTEXT_MAJOR_VERSION = 17, GL_CONTEXT_MINOR_VERSION = 18,
        GL_SHARE_WITH_CURRENT_CONTEXT = 21;
    static final int SCANCODE_COUNT = 512, KEYCODE_SCANCODE_MASK = 1 << 30;
    static final int KMOD_LSHIFT = 0x1, KMOD_RSHIFT = 0x2, KMOD_LCTRL = 0x40, KMOD_RCTRL = 0x80, KMOD_LALT = 0x100,
        KMOD_RALT = 0x200, KMOD_LGUI = 0x400, KMOD_RGUI = 0x800, KMOD_NUM = 0x1000, KMOD_CAPS = 0x2000;
    static final int PIXELFORMAT_XRGB8888 = 0x16161804;
    // GLFW values the native side and the shell speak
    private static final int GLFW_CURSOR = 0x33001, GLFW_CURSOR_NORMAL = 0x34001, GLFW_CURSOR_DISABLED = 0x34003;
    // klglfw ring records (klglfw.c KLGLFW_EV_*, struct klglfw_event: 48 bytes)
    private static final int R_KEY = 1, R_CHAR = 2, R_MOUSE_BUTTON = 3, R_CURSOR_POS = 4, R_SCROLL = 5,
        R_FRAMEBUFFER_SIZE = 6, R_WINDOW_SIZE = 7, R_FOCUS = 8, R_CURSOR_ENTER = 9, R_CLOSE = 10, R_ICONIFY = 11,
        R_CONTENT_SCALE = 12, R_REFRESH = 13, R_WINDOW_POS = 14;
    private static final int REC = 48, REC_CAP = 256;

    static void log(String s) { System.out.println("[klsdl] " + s); }

    // ---- errors --------------------------------------------------------------------------------------------------
    private static volatile String error = "";
    static boolean fail(String msg) { error = msg; System.err.println("[klsdl] error: " + msg); return false; }
    static boolean failNative(String what) { return fail(what + ": " + KlSdlNative.nLastError()); }
    static String error() { return error; }
    static void clearError() { error = ""; }

    static long ticksNS() { return System.nanoTime() - T0; }

    // ---- init ----------------------------------------------------------------------------------------------------
    private static boolean videoUp;

    /** Bring up libklsdl's EGL side once: glLib = the GL library (libltw), as klglfw's glfwInit. */
    static synchronized boolean initVideo(String glLib) {
        if (videoUp) return true;
        if (!KlSdlNative.nInit(glLib)) return failNative("klsdl init (GL library " + glLib + ")");
        videoUp = true;
        long m = KlSdlNative.nGetMonitorSize();
        log("SDL3 stand-in up: " + KlSdlNative.nDescribe() + "; display " + (int) (m >>> 32) + "x" + (int) m);
        return true;
    }
    static boolean videoUp() { return videoUp; }

    static boolean init(int flags) {
        if ((flags & INIT_VIDEO) != 0 && !videoUp) {
            String gl = System.getProperty("org.lwjgl.opengl.libname");
            // without the property GlBackend's SDL_GL_LoadLibrary(<GL.getFunctionProvider() path>) brings video up
            if (gl != null && !initVideo(gl)) return false;
        }
        return true;
    }

    static void quit() {
        if (videoUp) KlSdlNative.nTerminate();
        videoUp = false;
    }

    // ---- windows / contexts --------------------------------------------------------------------------------------
    static final class Win {
        final long handle; final int id; final boolean hidden; final String title;
        long flags; int x, y; boolean fullscreen;
        Win(long handle, int id, boolean hidden, long flags, String title) {
            this.handle = handle; this.id = id; this.hidden = hidden; this.flags = flags; this.title = title;
        }
    }
    private static final Map<Long, Win> byHandle = new HashMap<>();
    private static final Map<Integer, Win> byId = new HashMap<>();
    private static int nextId = 1;
    private static Win main;   // the last shown (non-hidden) window: where shell input goes
    static final int[] glAttr = new int[GL_ATTR_COUNT];
    static { glAttr[GL_CONTEXT_MAJOR_VERSION] = 3; glAttr[GL_CONTEXT_MINOR_VERSION] = 0; }
    static long currentContext;

    static synchronized long createWindow(CharSequence title, int w, int h, long flags) {
        if (!videoUp) {
            String gl = System.getProperty("org.lwjgl.opengl.libname");
            if (gl == null) { fail("SDL_CreateWindow before the GL library is known (SDL_GL_LoadLibrary / -Dorg.lwjgl.opengl.libname)"); return NULL; }
            if (!initVideo(gl)) return NULL;
        }
        boolean hidden = (flags & WINDOW_HIDDEN) != 0;
        long h0 = KlSdlNative.nCreateWindow(w, h, hidden);
        if (h0 == NULL) { failNative("SDL_CreateWindow"); return NULL; }
        Win win = new Win(h0, nextId++, hidden, flags | WINDOW_OPENGL, title == null ? "" : title.toString());
        byHandle.put(h0, win);
        byId.put(win.id, win);
        if (!hidden) main = win;
        long s = KlSdlNative.nGetWindowSize(h0);
        log("window " + win.id + " (0x" + Long.toHexString(h0) + ") " + (int) (s >>> 32) + "x" + (int) s
            + (hidden ? " hidden" : "") + " flags 0x" + Long.toHexString(flags) + " \"" + win.title + "\"");
        return h0;
    }

    static synchronized void destroyWindow(long h) {
        Win w = byHandle.remove(h);
        if (w == null) return;
        byId.remove(w.id);
        if (main == w) main = null;
        KlSdlNative.nDestroyWindow(h);
    }

    static synchronized Win win(long h) {
        Win w = byHandle.get(h);
        if (w == null) fail("Invalid window 0x" + Long.toHexString(h));
        return w;
    }

    static synchronized long windowFromId(int id) {
        Win w = byId.get(id);
        return w == null ? NULL : w.handle;
    }

    static long windowFlags(long h) {
        Win w = win(h);
        if (w == null) return 0;
        long f = w.flags & ~WINDOW_FULLSCREEN;
        if (w.fullscreen) f |= WINDOW_FULLSCREEN;
        if (w == main && focused) f |= WINDOW_INPUT_FOCUS | WINDOW_MOUSE_FOCUS;
        if (w == main && relative) f |= WINDOW_MOUSE_RELATIVE_MODE | WINDOW_MOUSE_GRABBED;
        return f;
    }

    /** SDL_SetWindowSize: the surface keeps its size; tell the game the size it really has. */
    static synchronized void requestSize(long h, int w, int hh) {
        Win win = win(h);
        if (win == null) return;
        long s = KlSdlNative.nGetWindowSize(h);
        int rw = (int) (s >>> 32), rh = (int) s;
        if (rw == w && rh == hh) return;
        log("SDL_SetWindowSize(" + w + "x" + hh + "): the surface is fixed at " + rw + "x" + rh + ", answering with that size");
        enqueue(windowEvent(EV_WINDOW_RESIZED, win.id, rw, rh));
        enqueue(windowEvent(EV_WINDOW_PIXEL_SIZE_CHANGED, win.id, rw, rh));
    }

    static synchronized void setFullscreen(long h, boolean on) {
        Win w = win(h);
        if (w == null || w.fullscreen == on) return;
        w.fullscreen = on;
        enqueue(windowEvent(on ? EV_WINDOW_ENTER_FULLSCREEN : EV_WINDOW_LEAVE_FULLSCREEN, w.id, 0, 0));
    }

    // ---- display -------------------------------------------------------------------------------------------------
    static final int DISPLAY_ID = 1;
    private static SDL_DisplayMode mode;
    static int monitorW() { return (int) (KlSdlNative.nGetMonitorSize() >>> 32); }
    static int monitorH() { return (int) KlSdlNative.nGetMonitorSize(); }
    static boolean checkDisplay(int id) { return id == DISPLAY_ID || fail("Invalid display " + id); }
    static void fillMode(SDL_DisplayMode m) {
        m.displayID(DISPLAY_ID).format(PIXELFORMAT_XRGB8888).w(monitorW()).h(monitorH()).pixel_density(1f)
            .refresh_rate(60f).refresh_rate_numerator(60).refresh_rate_denominator(1);
    }
    /** SDL owns the mode it returns from SDL_GetDesktopDisplayMode etc.: one struct, never freed. */
    static synchronized SDL_DisplayMode mode() {
        if (mode == null) mode = SDL_DisplayMode.calloc();
        fillMode(mode);
        return mode;
    }

    // ---- input state ---------------------------------------------------------------------------------------------
    static final ByteBuffer keyState = BufferUtils.createByteBuffer(SCANCODE_COUNT);
    static volatile int modState;
    static volatile double cx, cy;
    static volatile int buttons;
    static volatile boolean relative, focused = true, textInput;
    static String clipboard = "";
    private static long droppedText;

    // ---- events --------------------------------------------------------------------------------------------------
    static final class Ev {
        int type, win, i1, i2, state; short mod; boolean down, repeat;
        float x, y, xr, yr; String text; long ts;
    }
    private static final ArrayDeque<Ev> queue = new ArrayDeque<>();
    private static ByteBuffer ring;
    private static ByteBuffer lastText;

    static Ev windowEvent(int type, int winId, int d1, int d2) {
        Ev e = new Ev(); e.type = type; e.win = winId; e.i1 = d1; e.i2 = d2; e.ts = ticksNS(); return e;
    }
    private static void enqueue(Ev e) { queue.addLast(e); }

    private static int mainId() { return main == null ? 0 : main.id; }
    static synchronized int mainX() { return main == null ? 0 : main.x; }
    static synchronized int mainY() { return main == null ? 0 : main.y; }

    /** SDL_PumpEvents: one JNI call copies the native ring; each record becomes SDL events. */
    static synchronized void pump() {
        if (!videoUp) return;
        if (ring == null) ring = BufferUtils.createByteBuffer(REC * REC_CAP);
        long a = memAddress(ring);
        int n;
        do {
            n = KlSdlNative.nPollEvents(a, REC_CAP);
            for (int i = 0; i < n; i++) translate(a + (long) i * REC);
        } while (n == REC_CAP);
    }

    private static void translate(long r) {
        long winPtr = memGetLong(r);
        int type = memGetInt(r + 8), ra = memGetInt(r + 12), rb = memGetInt(r + 16), rc = memGetInt(r + 20), rd = memGetInt(r + 24);
        double x = memGetDouble(r + 32), y = memGetDouble(r + 40);
        Win w = winPtr == 0 ? main : byHandle.get(winPtr);
        int wid = w == null ? mainId() : w.id;
        long ts = ticksNS();
        Ev e = new Ev(); e.win = wid; e.ts = ts;
        switch (type) {
            case R_KEY -> {
                int sc = KlSdlKeys.scancodeOfGlfw(ra);
                if (sc == 0) { log("key: GLFW key " + ra + " has no SDL scancode, dropped"); return; }
                boolean down = rc != 0;
                keyState.put(sc, (byte) (down ? 1 : 0));
                int m = 0;
                if (keyState.get(225) != 0) m |= KMOD_LSHIFT;
                if (keyState.get(229) != 0) m |= KMOD_RSHIFT;
                if (keyState.get(224) != 0) m |= KMOD_LCTRL;
                if (keyState.get(228) != 0) m |= KMOD_RCTRL;
                if (keyState.get(226) != 0) m |= KMOD_LALT;
                if (keyState.get(230) != 0) m |= KMOD_RALT;
                if (keyState.get(227) != 0) m |= KMOD_LGUI;
                if (keyState.get(231) != 0) m |= KMOD_RGUI;
                if ((rd & 0x10) != 0) m |= KMOD_CAPS;   // GLFW_MOD_CAPS_LOCK
                if ((rd & 0x20) != 0) m |= KMOD_NUM;    // GLFW_MOD_NUM_LOCK
                modState = m;
                e.type = down ? EV_KEY_DOWN : EV_KEY_UP;
                e.i1 = sc; e.i2 = KlSdlKeys.keycode(sc, m, true); e.mod = (short) m; e.down = down; e.repeat = rc == 2;
            }
            case R_CHAR -> {
                if (!textInput) {
                    if (droppedText++ == 0) log("text input is off (SDL_StartTextInput not called): character events are dropped, as SDL does");
                    return;
                }
                e.type = EV_TEXT_INPUT; e.text = new String(Character.toChars(ra));
            }
            case R_MOUSE_BUTTON -> {
                int b = KlSdlKeys.buttonOfGlfw(ra);
                boolean down = rb != 0;
                buttons = down ? buttons | (1 << (b - 1)) : buttons & ~(1 << (b - 1));
                e.type = down ? EV_MOUSE_BUTTON_DOWN : EV_MOUSE_BUTTON_UP;
                e.i1 = b; e.down = down; e.x = (float) cx; e.y = (float) cy;
            }
            case R_CURSOR_POS -> {
                e.type = EV_MOUSE_MOTION;
                e.xr = (float) (x - cx); e.yr = (float) (y - cy);
                cx = x; cy = y;
                e.x = (float) x; e.y = (float) y; e.state = buttons;
            }
            case R_SCROLL -> { e.type = EV_MOUSE_WHEEL; e.x = (float) x; e.y = (float) y; e.xr = (float) cx; e.yr = (float) cy; }
            case R_WINDOW_SIZE -> {
                enqueue(windowEvent(EV_WINDOW_RESIZED, wid, ra, rb));
                e.type = EV_WINDOW_PIXEL_SIZE_CHANGED; e.i1 = ra; e.i2 = rb;
            }
            case R_FRAMEBUFFER_SIZE -> { e.type = EV_WINDOW_PIXEL_SIZE_CHANGED; e.i1 = ra; e.i2 = rb; }
            case R_FOCUS -> { focused = ra != 0; e.type = focused ? EV_WINDOW_FOCUS_GAINED : EV_WINDOW_FOCUS_LOST; }
            case R_CURSOR_ENTER -> e.type = ra != 0 ? EV_WINDOW_MOUSE_ENTER : EV_WINDOW_MOUSE_LEAVE;
            case R_CLOSE -> e.type = EV_WINDOW_CLOSE_REQUESTED;
            case R_ICONIFY -> e.type = ra != 0 ? EV_WINDOW_MINIMIZED : EV_WINDOW_RESTORED;
            case R_WINDOW_POS -> {
                if (w != null) { w.x = ra; w.y = rb; }
                e.type = EV_WINDOW_MOVED; e.i1 = ra; e.i2 = rb;
            }
            case R_REFRESH -> e.type = EV_WINDOW_EXPOSED;
            case R_CONTENT_SCALE -> { return; }   // pixel density stays 1
            default -> { log("unknown ring record type " + type); return; }
        }
        enqueue(e);
    }

    static synchronized boolean poll(SDL_Event out) {
        if (lastText != null) { memFree(lastText); lastText = null; }
        if (queue.isEmpty()) pump();
        Ev e = out == null ? queue.peekFirst() : queue.pollFirst();
        if (e == null) return false;
        if (out != null) write(out, e);
        return true;
    }

    static synchronized void flush(int min, int max) {
        pump();
        for (Iterator<Ev> it = queue.iterator(); it.hasNext(); ) { int t = it.next().type; if (t >= min && t <= max) it.remove(); }
    }

    private static void write(SDL_Event out, Ev e) {
        memSet(out.address(), 0, SDL_Event.SIZEOF);
        int t = e.type;
        if (t == EV_KEY_DOWN || t == EV_KEY_UP) {
            out.key().type(t).timestamp(e.ts).windowID(e.win).scancode(e.i1).key(e.i2).mod(e.mod).raw((short) e.i1)
                .down(e.down).repeat(e.repeat);
        } else if (t == EV_TEXT_INPUT) {
            lastText = memUTF8(e.text);
            out.text().type(t).timestamp(e.ts).windowID(e.win).text(lastText);
        } else if (t == EV_MOUSE_MOTION) {
            out.motion().type(t).timestamp(e.ts).windowID(e.win).state(e.state).x(e.x).y(e.y).xrel(e.xr).yrel(e.yr);
        } else if (t == EV_MOUSE_BUTTON_DOWN || t == EV_MOUSE_BUTTON_UP) {
            out.button().type(t).timestamp(e.ts).windowID(e.win).button((byte) e.i1).down(e.down).clicks((byte) 1).x(e.x).y(e.y);
        } else if (t == EV_MOUSE_WHEEL) {
            out.wheel().type(t).timestamp(e.ts).windowID(e.win).x(e.x).y(e.y).mouse_x(e.xr).mouse_y(e.yr)
                .integer_x((int) e.x).integer_y((int) e.y);
        } else if (t >= EV_WINDOW_FIRST && t <= EV_WINDOW_LAST) {
            out.window().type(t).timestamp(e.ts).windowID(e.win).data1(e.i1).data2(e.i2);
        } else {
            memPutInt(out.address(), t);
            memPutLong(out.address() + 8, e.ts);
        }
    }

    /** Events that carry a windowID at offset 16 (SDL_GetWindowFromEvent). */
    static long windowOf(SDL_Event e) {
        int t = e.type();
        boolean has = (t >= EV_WINDOW_FIRST && t <= EV_WINDOW_LAST) || (t >= EV_KEY_DOWN && t < 0x400 + 0x100);
        return has ? windowFromId(memGetInt(e.address() + SDL_WindowEvent.WINDOWID)) : NULL;
    }

    static synchronized void quitEvent() { Ev e = new Ev(); e.type = EV_QUIT; e.ts = ticksNS(); enqueue(e); }

    // ---- mouse ---------------------------------------------------------------------------------------------------
    static void setRelative(long h, boolean on) {
        if (relative != on) log("relative mouse mode " + (on ? "on" : "off"));
        relative = on;
        KlSdlNative.nSetInputMode(h, GLFW_CURSOR, on ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
    }
    static void warp(long h, float x, float y) {
        cx = x; cy = y;   // no motion event: the next ring position is measured from here
        KlSdlNative.nSetCursorPos(h, x, y);
    }
}
