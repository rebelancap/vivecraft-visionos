/* Darwin wrapper around upstream's GLSL conversion (Minecraft 26.3).
 *
 * Upstream shader_wrapper.c calls optimize_shader(desktop source, type, 460, ESSL version) and passes the
 * result straight to the host glShaderSource. darwin/ltw_darwin_rename_shader.h renames that call to
 * ltw_darwin_optimize_shader (here), which:
 *
 *  1. never returns NULL. On a failed conversion upstream returned NULL and ANGLE's glShaderSource ran
 *     strlen(NULL) (SIGSEGV, logs/klsdl-host-26.3-run5.txt). Now the shader gets a tiny ESSL source whose
 *     #error names the failure, so glCompileShader fails normally and the game logs it per pipeline; the
 *     converter's own log is still printed by upstream ("Shader conversion failed!").
 *
 *  2. fragment output arrays indexed with a non-constant expression. Minecraft 26.3's translucency pipelines
 *     (SPIRV-Cross output, 54 fragment shaders: terrain, entity, item, text, clouds, particle, ...) write
 *     `_frag_output_00[attachmentIndex][i] = ...` in a loop over `out vec4 _frag_output_00[2]`. Desktop GLSL
 *     allows that; ESSL 3.00 does not ("array indexes for fragment outputs must be constant integral
 *     expressions"). Rewrite of the ESSL text upstream produced: the output array is renamed <name>_ltwfo, a
 *     global temporary array takes the old name (so every write stays as it was), and main's closing brace
 *     gets one constant-index copy per element. Upstream lowers every `return` out of main (EmitNoMainReturn),
 *     and discard needs no copy, so the end of main is the only exit. LTW_DARWIN_FRAGOUT_ARRAY=0 disables it.
 *
 *  3. LTW_DARWIN_SHADER_DUMP=<dir>: writes every shader as <dir>/NNNN.<vs|fs>.in.glsl (what the game passed)
 *     and NNNN.<vs|fs>.out.glsl (what ANGLE gets).
 */
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <GLES3/gl3.h>

char *optimize_shader(char *source, GLenum type, int vGLSLVersion, int vTargetGLSLVersion); /* upstream c_wrapper.cpp */

static int env_off(const char *name) {
    const char *e = getenv(name);
    return e && (!strcmp(e, "0") || !strcasecmp(e, "off") || !strcasecmp(e, "no"));
}
static int ident(char c) { return c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }

typedef struct { char *b; size_t len, cap; } sbuf;
static void sb_add(sbuf *s, const char *p, size_t n) {
    if (s->len + n + 1 > s->cap) { s->cap = (s->len + n + 1) * 2; s->b = realloc(s->b, s->cap); }
    memcpy(s->b + s->len, p, n); s->len += n; s->b[s->len] = 0;
}
static void sb_str(sbuf *s, const char *p) { sb_add(s, p, strlen(p)); }

/* end of main: index of the '}' that closes `void main()`'s body, or NULL */
static const char *main_end(const char *src) {
    const char *m = strstr(src, "void main()");
    if (!m) return NULL;
    const char *p = strchr(m, '{');
    if (!p) return NULL;
    int depth = 0;
    for (; *p; p++) {
        if (*p == '{') depth++;
        else if (*p == '}' && --depth == 0) return p;
    }
    return NULL;
}

/* 2. returns a malloc'd rewritten source, or NULL when nothing needs rewriting */
#define FO_MAX 8
static char *fragout_array_rewrite(const char *src) {
    struct { const char *line, *eol, *name; size_t nlen; const char *type; size_t tlen; int n; } fo[FO_MAX];
    int nfo = 0;
    /* global `... out <type> <name>[<N>];` lines (one declaration per line in upstream's printer) */
    for (const char *l = src; *l && nfo < FO_MAX; ) {
        const char *eol = strchr(l, '\n'); if (!eol) eol = l + strlen(l);
        const char *o = l;
        while (o < eol) {
            const char *h = strstr(o, "out ");
            if (!h || h >= eol) { o = NULL; break; }
            if (h == l || !ident(h[-1])) { o = h; break; }
            o = h + 4;
        }
        if (o && o < eol && (o == l || o[-1] == ' ' || o[-1] == ')')) {
            const char *t = o + 4; while (*t == ' ') t++;
            const char *br = memchr(t, '[', (size_t)(eol - t));
            const char *semi = memchr(t, ';', (size_t)(eol - t));
            if (br && semi && br < semi) {
                const char *ne = br; while (ne > t && ne[-1] == ' ') ne--;
                const char *nb = ne; while (nb > t && ident(nb[-1])) nb--;
                int n = atoi(br + 1);
                if (nb > t && ne > nb && n > 0 && n <= 8) {
                    const char *te = nb; while (te > t && te[-1] == ' ') te--;
                    fo[nfo].line = l; fo[nfo].eol = eol; fo[nfo].name = nb; fo[nfo].nlen = (size_t)(ne - nb);
                    fo[nfo].type = t; fo[nfo].tlen = (size_t)(te - t); fo[nfo].n = n; nfo++;
                }
            }
        }
        l = *eol ? eol + 1 : eol;
    }
    if (!nfo) return NULL;
    const char *end = main_end(src);
    if (!end) return NULL;
    sbuf out = {0};
    const char *p = src;
    for (int k = 0; k < nfo; k++) {
        /* declaration: rename the output, then declare the temporary under the old name */
        sb_add(&out, p, (size_t)(fo[k].name + fo[k].nlen - p));
        sb_str(&out, "_ltwfo");
        sb_add(&out, fo[k].name + fo[k].nlen, (size_t)(fo[k].eol - (fo[k].name + fo[k].nlen)));
        char decl[256];
        snprintf(decl, sizeof decl, "\n%.*s %.*s[%d];", (int)fo[k].tlen, fo[k].type, (int)fo[k].nlen, fo[k].name, fo[k].n);
        sb_str(&out, decl);
        p = fo[k].eol;
    }
    sb_add(&out, p, (size_t)(end - p));
    for (int k = 0; k < nfo; k++)
        for (int i = 0; i < fo[k].n; i++) {
            char cp[256];
            snprintf(cp, sizeof cp, "  %.*s_ltwfo[%d] = %.*s[%d];\n", (int)fo[k].nlen, fo[k].name, i, (int)fo[k].nlen, fo[k].name, i);
            sb_str(&out, cp);
        }
    sb_str(&out, end);
    return out.b;
}

static atomic_int dump_seq;
static void dump(const char *dir, int n, GLenum type, const char *what, const char *src) {
    char path[1024];
    snprintf(path, sizeof path, "%s/%04d.%s.%s.glsl", dir, n, type == GL_VERTEX_SHADER ? "vs" : type == GL_FRAGMENT_SHADER ? "fs" : "xs", what);
    FILE *f = fopen(path, "w");
    if (!f) return;
    fputs(src ? src : "(null)\n", f);
    fclose(f);
}

char *ltw_darwin_optimize_shader(char *source, GLenum type, int vGLSLVersion, int vTargetGLSLVersion) {
    const char *dir = getenv("LTW_DARWIN_SHADER_DUMP");
    int n = dir && *dir ? atomic_fetch_add(&dump_seq, 1) : -1;
    if (n >= 0) dump(dir, n, type, "in", source);
    char *out = optimize_shader(source, type, vGLSLVersion, vTargetGLSLVersion);
    if (!out) {
        static const char fail[] = "#version 300 es\n#error ltw: desktop GLSL -> ESSL conversion failed (see the LTW log)\nvoid main() {}\n";
        out = malloc(sizeof fail);
        memcpy(out, fail, sizeof fail);
        printf("LTW-Darwin: shader conversion failed, shader #%d gets an #error source\n", n);
    }
    if (type == GL_FRAGMENT_SHADER && !env_off("LTW_DARWIN_FRAGOUT_ARRAY")) {
        char *rw = fragout_array_rewrite(out);
        if (rw) { free(out); out = rw; }
    }
    if (n >= 0) dump(dir, n, type, "out", out);
    return out;
}
