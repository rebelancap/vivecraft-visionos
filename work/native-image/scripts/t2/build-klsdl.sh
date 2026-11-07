#!/bin/bash
# build-klsdl.sh [macosx|xrsimulator|xros ...]  (default: all three)
# klsdl, the SDL3 stand-in for Minecraft 26.3+ (which replaced GLFW with SDL3 / lwjgl-sdl), for stock LWJGL <ver>
# (work/natives/scripts/lwjgl-ver.sh: LWJGL_VER, else from MCV; MCV defaults to 26.3 here). Same architecture as klglfw
# (build-klglfw341.sh). Outputs (work/natives/build/lwjgl-<ver>/):
#   klsdl/lwjgl-sdl-klsdl-<ver>.jar  replaces lwjgl-sdl-<ver>.jar: the stock struct + callback classes (SDL_*: plain
#                                    MemoryUtil views, no native dependency) WITHOUT any stock function class (SDL,
#                                    SDL<Module> and their inner classes: they bind libSDL3 symbols at class init)
#                                    + the stand-in, scripts/t4-src/java-sdl/org/lwjgl/sdl/*.java (SDL, SDLVideo,
#                                    SDLEvents, SDLKeyboard, SDLMouse, ... + KlSdl, KlSdlKeys, KlSdlNative)
#   <platform>/libklsdl.dylib        scripts/t4-src/native/klsdl.c (#includes klglfw.c; GraalVM 25 jni.h, ANGLE headers)
# Coverage gate: sdlrefs-<mcv>.txt = every org/lwjgl/sdl member the <mcv> classpath (client + all mods, nested jars
# included) references, regenerated from work/native-image/inst-<mcv> when it exists (refs-cp.py: the mkcp layout or
# fetch-inst.py's files/); glfwcover.py with KLCOVER_PKG=org/lwjgl/sdl/ then fails the build unless every one resolves
# in the jar and no class inside the jar points at a missing org/lwjgl/sdl member.
set -e
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
W="$ROOT/work/native-image"
N="$ROOT/work/natives"
G=${G:-$W/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home}
MCV=${MCV:-26.3}
. "$N/scripts/lwjgl-ver.sh"
V=$LWJGL_VER
MV=$LWJGL_MV
S=$W/scripts/t4-src
T2=$W/scripts/t2
OUT=$LWJGL_OUT
KJ=lwjgl-sdl-klsdl-$V.jar
B=$OUT/obj-klsdl; K=$OUT/klsdl
ANGLE_INC=${ANGLE_INC:-$ROOT/vendor/include}
[ -f $MV/lwjgl-sdl-$V.jar ] || { echo "no $MV/lwjgl-sdl-$V.jar (work/natives/scripts/fetch-lwjgl341.sh)"; exit 1; }
[ -f $ANGLE_INC/EGL/egl.h ] || { echo "no $ANGLE_INC/EGL/egl.h (python3 -I work/natives/scripts/fetch-graphics-headers.py)"; exit 1; }
rm -rf $B; mkdir -p $B/classes $K

# --- Java stand-in -------------------------------------------------------------------------------------------------
unzip -qo $MV/lwjgl-sdl-$V.jar 'org/lwjgl/sdl/*' -d $B/classes
find $B/classes/org/lwjgl/sdl -name 'SDL*.class' ! -name 'SDL_*' -delete
$G/bin/javac -nowarn --release 21 -d $B/classes \
  -cp "$MV/lwjgl-$V-unsafe.jar:$MV/lwjgl-opengl-$V.jar:$MV/lwjgl-vulkan-$V.jar:$B/classes" \
  $S/java-sdl/org/lwjgl/sdl/*.java
rm -f $K/$KJ
( cd $B/classes && zip -qr -X $K/$KJ org )
echo "jar: $K/$KJ ($(unzip -l $K/$KJ | tail -1 | awk '{print $2}') entries)"

# --- coverage against the $MCV classpath --------------------------------------------------------------------------
I=$W/inst-$MCV
REFS=$T2/sdlrefs-$MCV.txt
if { [ -f $I/vanilla/client-$MCV.jar ] && [ -d $I/fgamedir/mods ]; } || { [ -f $I/files/versions/$MCV/client.jar ] && [ -d $I/files/mods ]; }; then
  python3 -I $T2/refs-cp.py "$I" "$B/refs" "$MCV"
  python3 -I $W/scripts/t4/glfwrefs.py $B/refs/refs.cp '^org/lwjgl/sdl/' | sed "s#$B/refs/nested/##; s#$I/##" > $REFS
  echo "$(basename $REFS) regenerated from $I ($(wc -l < $REFS | tr -d ' ') refs)"
fi
[ -f $REFS ] || { echo "no $REFS and no inst-$MCV to regenerate it from (run scripts/m3a/fetch-inst.py $MCV)"; exit 1; }
KLCOVER_PKG=org/lwjgl/sdl/ python3 -I $T2/glfwcover.py $REFS $K/$KJ \
  $MV/lwjgl-$V-unsafe.jar $MV/lwjgl-opengl-$V.jar $MV/lwjgl-vulkan-$V.jar

# --- libklsdl per platform ------------------------------------------------------------------------------------------
CF="-O2 -g -Wall -Wextra -Wno-unused-parameter -fvisibility=hidden -I$G/include -I$G/include/darwin -I$ANGLE_INC"
for P in ${*:-macosx xrsimulator xros}; do
  case $P in macosx) t=arm64-apple-macos11;; xros) t=arm64-apple-xros26.0;; xrsimulator) t=arm64-apple-xros26.0-simulator;;
    *) echo "bad platform $P"; exit 2;; esac
  mkdir -p $OUT/$P
  xcrun --sdk $P clang -dynamiclib $CF -target $t -install_name @rpath/libklsdl.dylib -o $OUT/$P/libklsdl.dylib $S/native/klsdl.c
  rm -rf $OUT/$P/libklsdl.dylib.dSYM
  if [ $P = xrsimulator ]; then codesign -f -s - $OUT/$P/libklsdl.dylib 2>/dev/null; fi
  echo "libklsdl $P: $(xcrun vtool -show-build $OUT/$P/libklsdl.dylib | awk '/platform/{print $2}') $(stat -f%z $OUT/$P/libklsdl.dylib) B"
done
