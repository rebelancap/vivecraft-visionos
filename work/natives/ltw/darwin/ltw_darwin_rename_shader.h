/* Force-included into upstream shader_wrapper.c ONLY (Makefile target-specific flag), M3 stage A.
 * Renames ltw's glShaderSource so darwin/ltw_darwin_gl.c (section 6) can own the exported name, rewrite
 * texture-buffer samplers in the desktop GLSL source, and then delegate here. Inside shader_wrapper.c the
 * macro also renames the es3_functions member of the same name (consistently, struct layout unchanged). */
#ifndef LTW_DARWIN_RENAME_SHADER_H
#define LTW_DARWIN_RENAME_SHADER_H
#define glShaderSource ltw_upstream_glShaderSource
/* Minecraft 26.3: upstream's GLSL conversion goes through darwin/ltw_darwin_shader.c, which never returns NULL
 * (a failed conversion used to reach ANGLE's glShaderSource as a NULL string: SIGSEGV in strlen) and carries
 * the source rewrites for SPIRV-Cross output. The c_wrapper.h declaration is renamed with it. */
#define optimize_shader ltw_darwin_optimize_shader
#endif
