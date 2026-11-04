#!/bin/bash
# T7: OpenAL Soft 1.25.2 as a SHARED libopenal.dylib for visionOS device (xros) and simulator (xrsimulator).
# LIBTYPE=SHARED, CoreAudio backend only. Output is copied by work/native-image/scripts/t2/build-lwjgl341-natives.sh
# (visionOS platforms), so run this first. The tarball is fetched and sha256-checked if it is not already present.
# Usage: build-openal.sh [xros|xrsimulator ...]   (default: both)
set -euo pipefail
N="$(cd "$(dirname "$0")/.." && pwd)"
TARBALL="$N/downloads/openal/openal-soft-1.25.2.tar.gz"
SRC="$N/build/openal-src/openal-soft-1.25.2"
URL=https://codeload.github.com/kcat/openal-soft/tar.gz/refs/tags/1.25.2
SHA=fb27e5839aa11f0e5b9d33756965291fad5d6909ab928ea1f796f4a1a6877894
MIN=26.0
for dep in cmake curl xcrun shasum install_name_tool; do command -v "$dep" >/dev/null || { echo "missing $dep" >&2; exit 1; }; done
if [ ! -f "$TARBALL" ]; then
  mkdir -p "$(dirname "$TARBALL")"
  curl -fL --retry 3 "$URL" -o "$TARBALL.tmp"
  printf '%s  %s\n' "$SHA" "$TARBALL.tmp" | shasum -a 256 -c -
  mv "$TARBALL.tmp" "$TARBALL"
fi
printf '%s  %s\n' "$SHA" "$TARBALL" | shasum -a 256 -c -
mkdir -p "$N/build"
[ -d "$SRC" ] || { mkdir -p "$N/build/openal-src"; tar -xzf "$TARBALL" -C "$N/build/openal-src"; }
# D-060: openal-soft's aligned operator new/delete must never be exported from the dylib (it would replace
# libc++'s for every C++ image in the process). D-067's MAC_OS_X_VERSION_MIN_REQUIRED=101300 keeps the shim
# out of the build entirely; the unexport list is belt and braces in case a future release changes the guard.
UNEXP=""
for s in __ZnwmSt11align_val_t __ZnamSt11align_val_t __ZdlPvSt11align_val_t __ZdaPvSt11align_val_t \
         __ZnwmSt11align_val_tRKSt9nothrow_t __ZnamSt11align_val_tRKSt9nothrow_t \
         __ZdlPvSt11align_val_tRKSt9nothrow_t __ZdaPvSt11align_val_tRKSt9nothrow_t \
         __ZdlPvmSt11align_val_t __ZdaPvmSt11align_val_t; do UNEXP="$UNEXP -Wl,-unexported_symbol,$s"; done
# ALSOFT_RTKIT=OFF: RTKit is a Linux D-Bus service. Left on, CMake finds a host (Homebrew) D-Bus and then fails on
# core/rtkit.cpp (no gettid) when cross-compiling for visionOS. Reported by zschwendi/avp-craft.
for SDK in "${@:-xros xrsimulator}"; do
  for S in $SDK; do
    B="$N/build/openal-$S"; OUT="$N/openal/$S"
    rm -rf "$B"; mkdir -p "$OUT"
    cmake -S "$SRC" -B "$B" -GXcode \
      -DCMAKE_SYSTEM_NAME=visionOS -DCMAKE_OSX_SYSROOT="$S" -DCMAKE_OSX_ARCHITECTURES=arm64 \
      -DCMAKE_OSX_DEPLOYMENT_TARGET=$MIN \
      -DCMAKE_XCODE_ATTRIBUTE_CODE_SIGNING_ALLOWED=NO \
      -DLIBTYPE=SHARED \
      -DALSOFT_REQUIRE_COREAUDIO=ON \
      -DALSOFT_RTKIT=OFF \
      -DALSOFT_UTILS=OFF -DALSOFT_EXAMPLES=OFF -DALSOFT_TESTS=OFF \
      -DALSOFT_INSTALL_EXAMPLES=OFF -DALSOFT_INSTALL_UTILS=OFF \
      -DHAVE_WFUNCTION_EFFECTS=FALSE \
      -DCMAKE_CXX_FLAGS="-DMAC_OS_X_VERSION_MIN_REQUIRED=101300" \
      -DCMAKE_SHARED_LINKER_FLAGS="$UNEXP -Wl,-install_name,@rpath/libopenal.dylib" \
      -DCMAKE_INSTALL_PREFIX="$B/prefix" > "$N/build/openal-$S-cmake.log" 2>&1 \
      || { tail -30 "$N/build/openal-$S-cmake.log"; exit 1; }
    cmake --build "$B" --config Release --target install > "$N/build/openal-$S-build.log" 2>&1 \
      || { tail -30 "$N/build/openal-$S-build.log"; exit 1; }
    cp -L "$B/prefix/lib/libopenal.dylib" "$OUT/libopenal.dylib"
    install_name_tool -id @rpath/libopenal.dylib "$OUT/libopenal.dylib" 2>/dev/null || true
    rm -rf "$N/openal/include"; cp -R "$B/prefix/include" "$N/openal/include"
    echo "OK $S -> $OUT/libopenal.dylib"
  done
done
