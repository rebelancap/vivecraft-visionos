/* Force-included into upstream glsl_optimizer src/code/GlslConvert.cpp ONLY (Makefile target-specific flag),
 * Minecraft 26.3.
 * GlslConvert::InitContext gives the desktop-GLSL front end (every source ltw converts is parsed as GLSL 4.60)
 * MaxUniformBlocks = 4 for the vertex, geometry and fragment stages. That is ltw's own constant, not the
 * backend's: ANGLE's ES 3.0 context guarantees GL_MAX_{VERTEX,FRAGMENT}_UNIFORM_BLOCKS >= 12 (ANGLE-Metal
 * reports 12+). 26.2's hand-written shaders stayed under 4; 26.3's SPIRV-Cross output of core/terrain declares
 * 5 vertex blocks, so the intrastage link failed ("Too many vertex uniform blocks (5/4)") and ltw handed ANGLE
 * a NULL source. Fix: raise the three limits to 12, the ES 3.0 minimum, so a shader ltw accepts is still one
 * ANGLE must accept. Optimize() calls glcpp_preprocess(..., ctx) right after InitContext and before parsing and
 * linking, so a function-like macro on that call adjusts ctx; the declaration (glsl_parser_extras.h) is
 * included (guarded) BEFORE the macro exists. */
#ifndef LTW_DARWIN_GLSLCONV_LIMITS_H
#define LTW_DARWIN_GLSLCONV_LIMITS_H
#include "compiler/glsl/glsl_parser_extras.h"
#include "mesa/main/mtypes.h"
#define LTW_DARWIN_MAX_UNIFORM_BLOCKS 12
static inline struct gl_context *ltw_darwin_glslconv_limits(struct gl_context *c) {
    c->Const.Program[MESA_SHADER_VERTEX].MaxUniformBlocks = LTW_DARWIN_MAX_UNIFORM_BLOCKS;
    c->Const.Program[MESA_SHADER_GEOMETRY].MaxUniformBlocks = LTW_DARWIN_MAX_UNIFORM_BLOCKS;
    c->Const.Program[MESA_SHADER_FRAGMENT].MaxUniformBlocks = LTW_DARWIN_MAX_UNIFORM_BLOCKS;
    return c;
}
#define glcpp_preprocess(a, b, c, d, e, ctx) glcpp_preprocess(a, b, c, d, e, ltw_darwin_glslconv_limits(ctx))
#endif
