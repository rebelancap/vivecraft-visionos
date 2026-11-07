/*
 * klsdl.c -- libklsdl: the native half of Klepton's SDL3 stand-in (Minecraft 26.3+, which replaced GLFW with SDL3).
 *
 * Same architecture as klglfw, and most of it IS klglfw: this file #includes klglfw.c (EGL through libltw, the
 * ANGLE/kl_egl hosts, the event ring, the KLGLFW_SCRIPT test input, screenshots, fps log, VR pacing) and adds the
 * pieces where SDL's model differs from GLFW's. The Java half is org.lwjgl.sdl.* (scripts/t4-src/java-sdl: SDLVideo,
 * SDLEvents, SDLKeyboard, ... + KlSdl, KlSdlNative), built by scripts/t2/build-klsdl.sh into a jar that replaces
 * lwjgl-sdl-<ver>.jar. The C side never calls into Java; every native is a plain JNI downcall.
 *
 * ---- Where SDL differs from GLFW ------------------------------------------------------------------------------------
 *  Windows and GL contexts are separate objects. Minecraft 26.3 (renderpearl GlDevice) makes ONE context on a hidden
 *  utility window, creates/destroys a hidden test window, then binds that same context to the game window
 *  (SDL_GL_MakeCurrent(window, context)) and presents with SDL_GL_SwapWindow. So:
 *    window  = a klglfw_window holding only an EGLSurface (its .context is whatever context was last bound to it,
 *              so klglfw_egl_handle / nGetEGLHandle keep answering the pair kl_openxr needs);
 *    context = a klsdl_context holding an ES 3.0 EGLContext created through ltw (one per SDL_GL_CreateContext).
 *  Hidden windows (SDL_WINDOW_HIDDEN) are pbuffers of their requested size, also inside the app: only the shown game
 *  window becomes kl_egl's window surface (creating that flips kl_present to the flat picture) and only it takes
 *  KLGLFW_SIZE. For the shown window the native side queues focus + mouse-enter (as klglfw does) and, when the surface
 *  is not the size the game asked for, a window-size event, so Minecraft's Window learns the real size at once.
 *
 *  Input stays GLFW-coded in the ring: the app shell (kl_app.c -> klglfw_push_event, exported from here exactly as
 *  from libklglfw) and the test script keep speaking GLFW key codes / buttons / cursor modes. KlSdl.java translates
 *  each record to SDL scancodes, keycodes, SDL_Keymod and SDL3 event types when it pumps the ring. Relative mouse
 *  mode is recorded as GLFW_CURSOR_DISABLED (nSetInputMode) so klglfw_get_cursor_mode answers the shell unchanged.
 *
 * ---- JNI surface (class org.lwjgl.sdl.KlSdlNative) ------------------------------------------------------------------
 *  nInit(glLib)                    klglfw nInit after copying every KLSDL_<X> env var onto KLGLFW_<X> (the KLGLFW_*
 *                                  names the app shell sets keep working; KLSDL_* wins when both are set).
 *  nCreateWindow(w, h, hidden)     EGL surface only (pbuffer; shown window in the app: kl_egl window surface).
 *  nDestroyWindow(win)             releases the thread's binding if this window is current, destroys the surface.
 *  nGetWindowSize(win)             (w << 32) | h of the surface.
 *  nCreateContext(win,maj,min,sh)  ES 3.0 context through ltw (sh = klsdl_context to share with, 0 = none) and makes it
 *                                  current on win, as SDL_GL_CreateContext does. A 3.3+ request sets
 *                                  LTW_REPORT_GL_VERSION=3.3 (same rule as klglfw).
 *  nDestroyContext(ctx)            releases it if current on this thread, eglDestroyContext.
 *  nMakeCurrent(win, ctx)          eglMakeCurrent(win surface, ctx); ctx 0 releases; win 0 + ctx = surfaceless.
 *  nSwapWindow(win)                klglfw nSwapBuffers (frame counter, KLGLFW_SHOT_* screenshots, swap / VR pacing).
 *  everything else                 the klglfw native of the same name (ring copy, wait, cursor, time, monitor, EGL
 *                                  handles, errors).
 *
 * Dependencies: as klglfw.c (libc, pthread, dl, mach time, EGL headers). No AppKit/UIKit; compiles for macosx, xros
 * and xrsimulator.
 */
#define KL_TAG "klsdl"
#include "klglfw.c"

#define SJNI(ret, name) JNIEXPORT ret JNICALL Java_org_lwjgl_sdl_KlSdlNative_##name
#define GJNI(name) Java_org_lwjgl_glfw_KlGlfwNative_##name

typedef struct klsdl_context {
    uint32_t magic;
    EGLContext context;
} klsdl_context;
#define CTX_MAGIC 0x6b6c7363u /* 'klsc' */

static __thread klsdl_context *tls_ctx;

static klsdl_context *C(jlong h) {
    klsdl_context *c = (klsdl_context *)(intptr_t)h;
    return c && c->magic == CTX_MAGIC ? c : NULL;
}

/* KLSDL_<X> -> KLGLFW_<X> for every knob klglfw.c reads (header of klglfw.c / klsdl.c). */
static void env_alias(void) {
    static const char *const k[] = { "SIZE", "MONITOR", "HOST", "VR", "VR_MAXFPS", "SHOT_DIR", "SHOT_AT", "SHOT_EVERY",
                                     "FPS", "LOG", "SCRIPT", "EVENTS", "GL_LIBRARY", "DEFAULT_DISPLAY" };
    for (size_t i = 0; i < sizeof k / sizeof *k; i++) {
        char a[64], b[64];
        snprintf(a, sizeof a, "KLSDL_%s", k[i]);
        snprintf(b, sizeof b, "KLGLFW_%s", k[i]);
        const char *v = getenv(a);
        if (v) setenv(b, v, 1);
    }
}

SJNI(jboolean, nInit)(JNIEnv *env, jclass cls, jstring gl) {
    if (!S.inited) env_alias();
    return GJNI(nInit)(env, cls, gl);
}

SJNI(jlong, nCreateWindow)(JNIEnv *env, jclass cls, jint width, jint height, jboolean hidden) {
    (void)env; (void)cls;
    if (!S.inited) { seterr("not initialised"); return 0; }
    int w = width > 0 ? width : 854, h = height > 0 ? height : 480;
    if (!hidden) parse_wxh(getenv("KLGLFW_SIZE"), &w, &h);
    const EGLint sattr[] = { EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE };
    int as_window = S.klepton && !hidden;
    EGLSurface surf = as_window ? p_eglCreateWindowSurface(S.dpy, S.cfg, (EGLNativeWindowType)0, NULL)
                                : p_eglCreatePbufferSurface(S.dpy, S.cfg, sattr);
    if (surf == EGL_NO_SURFACE) {
        seterr("eglCreate%sSurface %dx%d: 0x%x", as_window ? "Window" : "Pbuffer", w, h, p_eglGetError());
        return 0;
    }
    klglfw_window *win = calloc(1, sizeof *win);
    win->magic = WIN_MAGIC; win->width = w; win->height = h; win->surface = surf; win->context = EGL_NO_CONTEXT;
    pthread_mutex_lock(&S.mu);
    win->next = S.windows; S.windows = win;
    if (!hidden) {
        int64_t id = (int64_t)(intptr_t)win;
        push_locked(id, KLGLFW_EV_FOCUS, 1, 0, 0, 0, 0, 0);
        push_locked(id, KLGLFW_EV_CURSOR_ENTER, 1, 0, 0, 0, 0, 0);
        if (w != width || h != height) push_locked(id, KLGLFW_EV_WINDOW_SIZE, w, h, 0, 0, 0, 0);
    }
    pthread_mutex_unlock(&S.mu);
    LOG("window %p: %dx%d%s %s (asked %dx%d)", (void *)win, w, h, hidden ? " hidden" : "",
        as_window ? "kl_egl window surface" : "pbuffer", width, height);
    return (jlong)(intptr_t)win;
}

SJNI(void, nDestroyWindow)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls;
    klglfw_window *w = W(h);
    if (!w) return;
    if (tls_current == w) { p_eglMakeCurrent(S.dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT); tls_current = NULL; tls_ctx = NULL; }
    p_eglDestroySurface(S.dpy, w->surface);
    pthread_mutex_lock(&S.mu);
    for (klglfw_window **p = &S.windows; *p; p = &(*p)->next) if (*p == w) { *p = w->next; break; }
    pthread_mutex_unlock(&S.mu);
    w->magic = 0;
    free(w);
}

SJNI(jlong, nGetWindowSize)(JNIEnv *env, jclass cls, jlong h) { return GJNI(nGetFramebufferSize)(env, cls, h); }

static int make_current(klglfw_window *w, klsdl_context *c) {
    EGLBoolean ok = c ? p_eglMakeCurrent(S.dpy, w ? w->surface : EGL_NO_SURFACE, w ? w->surface : EGL_NO_SURFACE, c->context)
                      : p_eglMakeCurrent(S.dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (!ok) { seterr("eglMakeCurrent(window %p, context %p): 0x%x", (void *)w, (void *)c, p_eglGetError()); return 0; }
    tls_current = c ? w : NULL;
    tls_ctx = c;
    if (w && c) w->context = c->context;
    static int once;
    if (w && c && !once && p_glGetString) {
        once = 1;
        LOG("current: GL_VERSION \"%s\", GL_RENDERER \"%s\"", p_glGetString(GL_VERSION_), p_glGetString(GL_RENDERER_));
    }
    return 1;
}

SJNI(jlong, nCreateContext)(JNIEnv *env, jclass cls, jlong win, jint maj, jint min, jlong share) {
    (void)env; (void)cls;
    if (!S.inited) { seterr("not initialised"); return 0; }
    klglfw_window *w = W(win);
    if (!w) { seterr("SDL_GL_CreateContext: no such window %p", (void *)(intptr_t)win); return 0; }
    /* see klglfw nCreateWindow: libltw only learns the desktop GL version the game wants from this variable */
    if ((maj > 3 || (maj == 3 && min >= 3)) && !getenv("LTW_REPORT_GL_VERSION")) {
        setenv("LTW_REPORT_GL_VERSION", "3.3", 0);
        LOG("asked GL %d.%d: LTW_REPORT_GL_VERSION=3.3 (ltw reports \"3.3 OpenLTW\", GLSL 3.30)", maj, min);
    }
    klsdl_context *sh = C(share);
    const EGLint xattr[] = { EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 0, EGL_NONE };
    EGLContext ctx = p_eglCreateContext(S.dpy, S.cfg, sh ? sh->context : EGL_NO_CONTEXT, xattr);
    if (ctx == EGL_NO_CONTEXT) { seterr("eglCreateContext ES 3.0: 0x%x", p_eglGetError()); return 0; }
    klsdl_context *c = calloc(1, sizeof *c);
    c->magic = CTX_MAGIC; c->context = ctx;
    LOG("context %p: ES 3.0 %p on window %p (asked GL %d.%d)%s", (void *)c, ctx, (void *)w, maj, min, sh ? ", shared" : "");
    if (!make_current(w, c)) { p_eglDestroyContext(S.dpy, ctx); c->magic = 0; free(c); return 0; }
    return (jlong)(intptr_t)c;
}

SJNI(jboolean, nDestroyContext)(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls;
    klsdl_context *c = C(h);
    if (!c) { seterr("SDL_GL_DestroyContext: no such context %p", (void *)(intptr_t)h); return JNI_FALSE; }
    if (tls_ctx == c) make_current(NULL, NULL);
    pthread_mutex_lock(&S.mu);
    for (klglfw_window *w = S.windows; w; w = w->next) if (w->context == c->context) w->context = EGL_NO_CONTEXT;
    pthread_mutex_unlock(&S.mu);
    p_eglDestroyContext(S.dpy, c->context);
    c->magic = 0;
    free(c);
    return JNI_TRUE;
}

SJNI(jboolean, nMakeCurrent)(JNIEnv *env, jclass cls, jlong win, jlong ctx) {
    (void)env; (void)cls;
    klglfw_window *w = W(win);
    klsdl_context *c = C(ctx);
    if (win && !w) { seterr("SDL_GL_MakeCurrent: no such window %p", (void *)(intptr_t)win); return JNI_FALSE; }
    if (ctx && !c) { seterr("SDL_GL_MakeCurrent: no such context %p", (void *)(intptr_t)ctx); return JNI_FALSE; }
    return make_current(w, c) ? JNI_TRUE : JNI_FALSE;
}

SJNI(void, nSwapWindow)(JNIEnv *env, jclass cls, jlong win) { GJNI(nSwapBuffers)(env, cls, win); }

/* ---- the rest is klglfw's, unchanged ------------------------------------------------------------------------------ */
SJNI(jstring, nLastError)(JNIEnv *env, jclass cls) { return GJNI(nLastError)(env, cls); }
SJNI(jstring, nDescribe)(JNIEnv *env, jclass cls) { return GJNI(nDescribe)(env, cls); }
SJNI(void, nTerminate)(JNIEnv *env, jclass cls) { GJNI(nTerminate)(env, cls); }
SJNI(jlong, nGetMonitorSize)(JNIEnv *env, jclass cls) { return GJNI(nGetMonitorSize)(env, cls); }
SJNI(void, nSwapInterval)(JNIEnv *env, jclass cls, jint i) { GJNI(nSwapInterval)(env, cls, i); }
SJNI(jlong, nGetProcAddress)(JNIEnv *env, jclass cls, jstring name) { return GJNI(nGetProcAddress)(env, cls, name); }
SJNI(jint, nPollEvents)(JNIEnv *env, jclass cls, jlong buf, jint cap) { return GJNI(nPollEvents)(env, cls, buf, cap); }
SJNI(void, nWaitEvents)(JNIEnv *env, jclass cls, jdouble timeout) { GJNI(nWaitEvents)(env, cls, timeout); }
SJNI(void, nPostEmptyEvent)(JNIEnv *env, jclass cls) { GJNI(nPostEmptyEvent)(env, cls); }
SJNI(void, nSetCursorPos)(JNIEnv *env, jclass cls, jlong h, jdouble x, jdouble y) { GJNI(nSetCursorPos)(env, cls, h, x, y); }
SJNI(void, nSetInputMode)(JNIEnv *env, jclass cls, jlong h, jint mode, jint value) { GJNI(nSetInputMode)(env, cls, h, mode, value); }
SJNI(jlong, nGetTimerValue)(JNIEnv *env, jclass cls) { return GJNI(nGetTimerValue)(env, cls); }
SJNI(jlong, nGetEGLHandle)(JNIEnv *env, jclass cls, jlong h, jint which) { return GJNI(nGetEGLHandle)(env, cls, h, which); }
