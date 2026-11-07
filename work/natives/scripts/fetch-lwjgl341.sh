#!/bin/bash
# fetch-lwjgl341.sh: LWJGL source tag + Maven Central jars + the matching FreeType tag archive into work/natives/downloads
# (see PROVENANCE.md). Version from lwjgl-ver.sh: LWJGL_VER=3.4.1|3.4.3, else by MCV (26.2 -> 3.4.1, 26.3 -> 3.4.3).
# The file name is historical; it fetches whichever version is selected.
set -e
N="$(cd "$(dirname "$0")/.." && pwd)"
. "$N/scripts/lwjgl-ver.sh"
D=$N/downloads; V=$LWJGL_VER; mkdir -p $D
[ -d $LWJGL_SRC/.git ] || git clone -q --depth 1 -b $V https://github.com/LWJGL/lwjgl3 $LWJGL_SRC
test "$(git -C $LWJGL_SRC rev-parse HEAD)" = $LWJGL_COMMIT
M=$LWJGL_MV; mkdir -p $M
R=https://repo1.maven.org/maven2/org/lwjgl
get() { [ -f $M/$2 ] || curl -sfL -o $M/$2 $R/$1/$V/$2 || { rm -f $M/$2; echo "  (absent: $2)"; }; }
get lwjgl lwjgl-$V.jar; get lwjgl lwjgl-$V-unsafe.jar; get lwjgl lwjgl-$V-natives-macos-arm64.jar
for m in opengl stb openal openxr glfw jemalloc tinyfd freetype vulkan sdl; do
  get lwjgl-$m lwjgl-$m-$V.jar; get lwjgl-$m lwjgl-$m-$V-natives-macos-arm64.jar
done
( cd $M && shasum -a 256 *.jar > SHA256SUMS )
ls -la $M
# FreeType tag archive for build-freetype.sh (the release LWJGL's own libfreetype.dylib.git names for this version)
FT=$D/freetype-$FT_TAG.tar.gz
if [ ! -f $FT ]; then
  curl -fsL --retry 3 https://github.com/freetype/freetype/archive/refs/tags/$FT_TAG.tar.gz -o $FT.tmp
  echo "$FT_SHA256  $FT.tmp" | shasum -a 256 -c - >/dev/null && mv $FT.tmp $FT
fi
echo "$FT_SHA256  $FT" | shasum -a 256 -c -
want=$(unzip -p $M/lwjgl-freetype-$V-natives-macos-arm64.jar '*libfreetype.dylib.git' | tr -d '[:space:]')
[ "$want" = $FT_COMMIT ] || { echo "LWJGL $V libfreetype.dylib.git = $want, lwjgl-ver.sh pins $FT_COMMIT"; exit 1; }
# static libffi release tarball for build-lwjgl341-natives.sh (must match LWJGL's bundled ffi.h, lwjgl-ver.sh FFI_VER)
F=$N/../native-image/dl/libffi/libffi-$FFI_VER.tar.gz; mkdir -p "$(dirname $F)"
if [ ! -f $F ]; then
  curl -fsL --retry 3 https://github.com/libffi/libffi/releases/download/v$FFI_VER/libffi-$FFI_VER.tar.gz -o $F.tmp
  echo "$FFI_SHA256  $F.tmp" | shasum -a 256 -c - >/dev/null && mv $F.tmp $F
fi
echo "$FFI_SHA256  $F" | shasum -a 256 -c -
