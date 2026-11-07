#!/bin/bash
# build-lwjgl341-natives-xros.sh: the visionOS halves (xrsimulator, then xros) of build-lwjgl341-natives.sh.
# LWJGL 3.4.3 (MCV=26.3) also needs shaderc + SPIRV-Cross (lwjgl-shaderc / lwjgl-spvc; 26.3 compiles every shader
# GLSL -> SPIR-V -> GLSL): work/natives/scripts/build-shaderc-spvc.sh builds those for the same two SDKs.
set -e
H="$(cd "$(dirname "$0")" && pwd)"
N="$H/../../../natives"
. "$N/scripts/lwjgl-ver.sh"
LWJGL_VER=$LWJGL_VER "$H/build-lwjgl341-natives.sh" xrsimulator xros
[ $LWJGL_VER = 3.4.1 ] || LWJGL_VER=$LWJGL_VER "$N/scripts/build-shaderc-spvc.sh" xrsimulator xros
