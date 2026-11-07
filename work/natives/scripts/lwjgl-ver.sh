# lwjgl-ver.sh — sourced (bash). THE LWJGL pin table for the 26.x natives scripts: fetch-lwjgl341.sh,
# work/native-image/scripts/t2/{build-lwjgl341-natives,build-klglfw341,build-freetype}.sh. The "341" in those file names
# is historical; they build whichever LWJGL_VER this file selects.
#   LWJGL_VER set        -> that version (must be in the table below)
#   else from MCV        -> 26.2 = 3.4.1, 26.3 = 3.4.3 (MCV unset = 26.2, as inst.sh/jdk.sh)
# Exports: LWJGL_VER LWJGL_COMMIT (the tag's commit, checked after clone) FT_TAG FT_COMMIT FT_SHA256 (the FreeType
# release the stock LWJGL libfreetype.dylib.git of that version names; its tag archive sha256), FFI_VER FFI_SHA256 (the
# static libffi release tarball in work/native-image/dl/libffi/ matching LWJGL's bundled ffi.h) and
# LWJGL_OUT=work/natives/build/lwjgl-<ver>, LWJGL_SRC=work/natives/downloads/lwjgl3-<ver>, LWJGL_MV=.../lwjgl-<ver>-maven.
# Provenance: work/natives/PROVENANCE.md.
if [ -z "${LWJGL_VER:-}" ]; then
  case ${MCV:-26.2} in
    26.2) LWJGL_VER=3.4.1 ;;
    26.3) LWJGL_VER=3.4.3 ;;
    *) echo "lwjgl-ver.sh: no LWJGL pin for MCV=${MCV:-} (set LWJGL_VER=3.4.1|3.4.3)" >&2; exit 2 ;;
  esac
fi
case $LWJGL_VER in
  3.4.1) LWJGL_COMMIT=b800ccffab14396fc529ddb6c931b7c5c5226763
         FT_TAG=VER-2-14-1 FT_COMMIT=526ec5c47b9ebccc4754c85ac0c0cdf7c85a5e9b
         FT_SHA256=44bd69d1f0750603410cfd4f26f1c5523c5a3a087a2dfa8639b757dc7c6677be
         FFI_VER=3.5.2 FFI_SHA256=f3a3082a23b37c293a4fcd1053147b371f2ff91fa7ea1b2a52e335676bac82dc ;;
  3.4.3) LWJGL_COMMIT=30fac9b95f99cda97312232be25ba55297bf9951
         FT_TAG=VER-2-14-3 FT_COMMIT=0a0221a1347e2f1e07c395263540026e9a0aa7c7
         FT_SHA256=dc49de6b01a266eef4876a4dd34d9842c475d3e28ff2eff63bd2fb760ab56261
         # 3.4.3's bundled ffi.h is libffi 3.8.0 and LibFFI.c calls ffi_call_plan_* (new in 3.8.0): 3.5.2 cannot link.
         FFI_VER=3.8.0 FFI_SHA256=7da3e2d9a171eb0a038f592ecad3ff2bb2550f3496d87b3b29ad0cf4430c0db4 ;;
  *) echo "lwjgl-ver.sh: LWJGL_VER=$LWJGL_VER is not pinned" >&2; exit 2 ;;
esac
_LV_N="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # work/natives
LWJGL_OUT=$_LV_N/build/lwjgl-$LWJGL_VER
LWJGL_SRC=$_LV_N/downloads/lwjgl3-$LWJGL_VER
LWJGL_MV=$_LV_N/downloads/lwjgl-$LWJGL_VER-maven
unset _LV_N
