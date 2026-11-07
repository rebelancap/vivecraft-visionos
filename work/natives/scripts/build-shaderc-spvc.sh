#!/bin/bash
# build-shaderc-spvc.sh [xrsimulator|xros|macosx|macosx-src ...]   (default: xrsimulator xros)
#   -> work/natives/build/lwjgl-<ver>/<sdk>/{libshaderc,libspirv-cross}.dylib
# Minecraft 26.3 compiles every shader GLSL -> SPIR-V with shaderc (lwjgl-shaderc) and, on its GL backend, SPIR-V ->
# GLSL with SPIRV-Cross (lwjgl-spvc). Neither LWJGL module has a JNI library (both call through liblwjgl's JNI.call*,
# like lwjgl-freetype), so the only natives are the two third-party dylibs, found by FILE name through
# -Dorg.lwjgl.librarypath (Platform.mapLibraryNameBundled("shaderc"/"spirv-cross")).
# LWJGL does not build them in its own tree: LWJGL-CI/shaderc and LWJGL-CI/SPIRV-Cross (upstream + one CI commit)
# build shaderc_shared / spirv-cross-c-shared on GitHub Actions and the stock natives jar names the upstream commit in
# lib*.dylib.git. This script clones those upstream commits (pins below, from LWJGL <ver>'s macos-arm64 natives jars)
# and builds them the same way (LWJGL-CI .github/workflows/lwjgl.yml SHADERC_PARAMS / SPVC_PARAMS), cross-compiled for
# visionOS. shaderc's DEPS: only glslang, SPIRV-Tools, SPIRV-Headers are fetched (shallow, at the DEPS pins) — the
# rest of DEPS (googletest, effcee, re2, abseil) only builds tests, which SHADERC_SKIP_TESTS turns off.
#   macosx      copies LWJGL's stock Maven macos-arm64 dylibs into build/lwjgl-<ver>/macosx/ (the host JVM set)
#   macosx-src  builds the same sources for macOS into build/lwjgl-<ver>/obj-macosx-src/out/ (recipe smoke test only)
# Provenance: work/natives/PROVENANCE.md (LWJGL 3.4.3 section). JOBS caps parallel compiler jobs.
set -euo pipefail
PLATS="${*:-xrsimulator xros}"
JOBS=${JOBS:-$(sysctl -n hw.ncpu)}
case "$JOBS" in ''|*[!0-9]*|0) echo "JOBS must be a positive integer" >&2; exit 2;; esac
N="$(cd "$(dirname "$0")/.." && pwd)"
. "$N/scripts/lwjgl-ver.sh"
V=$LWJGL_VER
case $V in
  3.4.3) SHADERC_COMMIT=2c8cae778eec0283b44acbe7ed1a386865d78799   # google/shaderc "Finalize Shaderc v2026.3"
         SPVC_COMMIT=6c09849fe88c48eaed08413aa022aaa136a3a057      # KhronosGroup/SPIRV-Cross main, 2026-07-06
         # shaderc DEPS at $SHADERC_COMMIT
         DEP_glslang="KhronosGroup/glslang 168d452a4f460d24b588fed08477a81c44ee27a1"
         DEP_spirv_tools="KhronosGroup/SPIRV-Tools b707790a898e44038547df54580022fc1cf89c3d"
         DEP_spirv_headers="KhronosGroup/SPIRV-Headers 29981f65241605e08b0ede4cfeb999fe3b723c6a" ;;
  *) echo "build-shaderc-spvc.sh: no shaderc/SPIRV-Cross pin for LWJGL $V (only 26.3 / 3.4.3 loads them)" >&2; exit 2 ;;
esac
D=$N/downloads; MV=$LWJGL_MV
SHADERC_SRC=$D/shaderc-$SHADERC_COMMIT; SPVC_SRC=$D/SPIRV-Cross-$SPVC_COMMIT
for dep in cmake ninja git xcrun python3 dyld_info; do command -v $dep >/dev/null || { echo "missing $dep" >&2; exit 1; }; done

# exact-commit shallow fetch (GitHub serves any reachable commit by sha)
fetch() {  # fetch <dir> <owner/repo> <commit>
  if [ ! -d "$1/.git" ]; then
    rm -rf "$1"; mkdir -p "$1"; git -C "$1" init -q
    git -C "$1" fetch -q --depth 1 https://github.com/$2 $3
    git -C "$1" checkout -q FETCH_HEAD
  fi
  test "$(git -C "$1" rev-parse HEAD)" = $3 || { echo "$1: HEAD is not $3" >&2; exit 1; }
}
fetch $SHADERC_SRC google/shaderc $SHADERC_COMMIT
fetch $SHADERC_SRC/third_party/glslang       $DEP_glslang
fetch $SHADERC_SRC/third_party/spirv-tools   $DEP_spirv_tools
fetch $SHADERC_SRC/third_party/spirv-headers $DEP_spirv_headers
fetch $SPVC_SRC KhronosGroup/SPIRV-Cross $SPVC_COMMIT
# the stock jars must name the same upstream commits (else lwjgl-ver.sh moved and these pins did not)
for p in "shaderc libshaderc.dylib $SHADERC_COMMIT" "spvc libspirv-cross.dylib $SPVC_COMMIT"; do
  set -- $p; J=$MV/lwjgl-$1-$V-natives-macos-arm64.jar
  [ -f $J ] || { echo "$J missing (work/natives/scripts/fetch-lwjgl341.sh)" >&2; exit 1; }
  want=$(unzip -p $J "*$2.git" | tr -d '[:space:]')
  [ "$want" = $3 ] || { echo "LWJGL $V $2.git = $want, this script pins $3" >&2; exit 1; }
done

# every function LWJGL's Shaderc / Spvc resolve must be exported (a missing one throws at class init)
jsyms() { grep -o "apiGetFunctionAddress($1, \"[A-Za-z_0-9]*\")" "$2" | sed 's/.*, "\(.*\)")$/_\1/' | sort -u; }
REQ_SHADERC=$(jsyms SHADERC $LWJGL_SRC/modules/lwjgl/shaderc/src/generated/java/org/lwjgl/util/shaderc/Shaderc.java)
REQ_SPVC=$(jsyms SPVC $LWJGL_SRC/modules/lwjgl/spvc/src/generated/java/org/lwjgl/util/spvc/Spvc.java)
exports() { dyld_info -exports "$1" | awk '$2~/^_/{print $2}' | sort -u; }

check() {  # check <platform> <dylib> <required-list> <prefix>
  local plat=$(xcrun vtool -show-build "$2" | awk '/platform/{print $2}') miss
  miss=$(comm -23 <(printf '%s\n' "$3") <(exports "$2") | tr '\n' ' ')
  local news=$(exports "$2" | grep -cE '^__Zn[wa]m|^__Zd[la]Pv' || true)
  echo "== $1 $(basename $2): $plat $(stat -f%z "$2") B, $(exports "$2" | grep -c "^_$4") _$4* exports, LWJGL-required missing: ${miss:-none}, operator new/delete exports: $news"
  [ -z "$miss" ] && [ "$news" = 0 ]
}

for P in $PLATS; do
  case $P in
    xros|xrsimulator) SYSN="-DCMAKE_SYSTEM_NAME=visionOS -DCMAKE_OSX_SYSROOT=$(xcrun --sdk $P --show-sdk-path) -DCMAKE_OSX_DEPLOYMENT_TARGET=26.0"
                      O=$LWJGL_OUT/$P ;;
    macosx-src)       SYSN="-DCMAKE_OSX_SYSROOT=$(xcrun --sdk macosx --show-sdk-path) -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0"
                      O=$LWJGL_OUT/obj-macosx-src/out ;;
    macosx)           O=$LWJGL_OUT/macosx; mkdir -p $O
                      unzip -qjo $MV/lwjgl-shaderc-$V-natives-macos-arm64.jar 'macos/arm64/org/lwjgl/shaderc/libshaderc.dylib' -d $O
                      unzip -qjo $MV/lwjgl-spvc-$V-natives-macos-arm64.jar 'macos/arm64/org/lwjgl/spvc/libspirv-cross.dylib' -d $O
                      echo "== macosx: stock LWJGL $V libshaderc.dylib + libspirv-cross.dylib -> $O"; continue ;;
    *) echo "usage: $0 [xrsimulator|xros|macosx|macosx-src ...]" >&2; exit 2 ;;
  esac
  B=$LWJGL_OUT/obj-$P/shaderc-spvc; mkdir -p $B $O
  COMMON="-G Ninja $SYSN -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_STANDARD=17"
  t0=$(date +%s)
  # shaderc (LWJGL-CI: -DSHADERC_SKIP_INSTALL=ON -DSHADERC_SKIP_TESTS=ON; target shaderc_shared). Executables and
  # examples off (nothing to run them on), -Werror off (a newer clang must not fail a pinned tree). SHADERC_SKIP_INSTALL
  # is left OFF (nothing is installed either way): shaderc's third_party/CMakeLists.txt sets GLSLANG_ENABLE_INSTALL to
  # the literal string "$<NOT:ON>", which if() reads as true, while SPIRV-Tools does skip its install -> CMake 4 stops
  # with "glslang-targets requires SPIRV-Tools-opt that is not in any export set".
  rm -rf $B/shaderc
  cmake -S $SHADERC_SRC -B $B/shaderc $COMMON \
    -DSHADERC_SKIP_TESTS=ON -DSHADERC_SKIP_EXAMPLES=ON -DSHADERC_SKIP_EXECUTABLES=ON \
    -DSHADERC_SKIP_COPYRIGHT_CHECK=ON -DSHADERC_ENABLE_WERROR_COMPILE=OFF \
    -DSPIRV_SKIP_EXECUTABLES=ON -DSPIRV_SKIP_TESTS=ON -DSPIRV_WERROR=OFF \
    -DENABLE_GLSLANG_BINARIES=OFF -DGLSLANG_TESTS=OFF \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-install_name,@rpath/libshaderc.dylib" > $B/shaderc-cmake.log 2>&1 \
    || { tail -30 $B/shaderc-cmake.log; exit 1; }
  ninja -C $B/shaderc -j"$JOBS" shaderc_shared > $B/shaderc-build.log 2>&1 || { tail -30 $B/shaderc-build.log; exit 1; }
  t1=$(date +%s)
  # SPIRV-Cross (LWJGL-CI SPVC_PARAMS, minus -DSPIRV_CROSS_WERROR=ON for the same reason)
  rm -rf $B/spvc
  cmake -S $SPVC_SRC -B $B/spvc $COMMON \
    -DSPIRV_CROSS_STATIC=OFF -DSPIRV_CROSS_SHARED=ON -DSPIRV_CROSS_CLI=OFF -DSPIRV_CROSS_ENABLE_TESTS=OFF \
    -DSPIRV_CROSS_SKIP_INSTALL=ON -DSPIRV_CROSS_FORCE_PIC=ON \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-install_name,@rpath/libspirv-cross.dylib" > $B/spvc-cmake.log 2>&1 \
    || { tail -30 $B/spvc-cmake.log; exit 1; }
  ninja -C $B/spvc -j"$JOBS" spirv-cross-c-shared > $B/spvc-build.log 2>&1 || { tail -30 $B/spvc-build.log; exit 1; }
  t2=$(date +%s)
  cp -L $B/shaderc/libshaderc/libshaderc_shared.dylib $O/libshaderc.dylib
  cp -L $B/spvc/libspirv-cross-c-shared.dylib $O/libspirv-cross.dylib
  for L in libshaderc libspirv-cross; do
    chmod u+w $O/$L.dylib
    install_name_tool -id @rpath/$L.dylib $O/$L.dylib 2>/dev/null
    strip -x $O/$L.dylib
    [ $P = xros ] || codesign -f -s - $O/$L.dylib 2>/dev/null
  done
  echo "== $P: shaderc $((t1-t0)) s, spirv-cross $((t2-t1)) s (JOBS=$JOBS)"
  check $P $O/libshaderc.dylib "$REQ_SHADERC" shaderc
  check $P $O/libspirv-cross.dylib "$REQ_SPVC" spvc
done
