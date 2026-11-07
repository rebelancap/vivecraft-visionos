#!/bin/bash
# runfabric.sh <seconds> [jvm args]; GAMEARGS env for extra game args. Runs Fabric (Knot) on GraalVM JVM.
# MCV=<version> picks the instance (scripts/inst.sh). The bake = this run with -javaagent:$W/bakeagent/bakeagent.jar=$I/bake
# KLSDL=1 (26.3+): run on klsdl, the SDL3 stand-in, instead of stock SDL3 + Apple GL -- the port's window/GL/input
#   path, headless: lwjgl-sdl-<v>.jar -> work/natives/build/lwjgl-<v>/klsdl/lwjgl-sdl-klsdl-<v>.jar
#   (scripts/t2/build-klsdl.sh), every *-natives-* jar dropped (natives from work/natives/build/lwjgl-<v>/macosx via
#   org.lwjgl.librarypath) except stock shaderc + spvc: 26.3 compiles every shader GLSL -> SPIR-V (shaderc) and its GL
#   backend cross-compiles SPIR-V -> GLSL (spvc), and work/natives builds neither yet, GL = libltw (LTW=, default work/natives/ltw/build/macosx/libltw.dylib) over a host ANGLE
#   (ANGLE=<dir with libEGL.dylib + libGLESv2.dylib>, default $ROOT/vendor/out/Debug; the public tree has none, see
#   BUILDING.md section 2). Screenshots etc.: the KLSDL_* / KLGLFW_* env (scripts/t4-src/native/klsdl.c), e.g.
#   KLSDL_SHOT_DIR=<dir> KLSDL_SHOT_AT=20,40. The game window is the 854x480 pbuffer unless KLSDL_SIZE=WxH.
. "$(dirname "$0")/inst.sh"
T=$1; shift
CP="$(cat $I/fabric.cp)"; KLARGS=()
if [ "${KLSDL:-0}" = 1 ]; then
  . "$ROOT/work/natives/scripts/lwjgl-ver.sh"
  KJ=$LWJGL_OUT/klsdl/lwjgl-sdl-klsdl-$LWJGL_VER.jar
  LTW=${LTW:-$ROOT/work/natives/ltw/build/macosx/libltw.dylib}
  ANGLE=${ANGLE:-$ROOT/vendor/out/Debug}
  for f in $KJ $LWJGL_OUT/macosx/libklsdl.dylib $LTW $ANGLE/libEGL.dylib; do [ -f $f ] || { echo "runfabric: KLSDL=1 needs $f"; exit 1; }; done
  CP=$(tr ':' '\n' < $I/fabric.cp | awk '!/-natives-/ || /\/lwjgl-(shaderc|spvc)-[0-9.]+-natives-/' | sed "s#^.*/lwjgl-sdl-${LWJGL_VER//./\\.}\.jar\$#$KJ#" | paste -sd: -)
  case ":$CP:" in *":$KJ:"*) ;; *) echo "runfabric: no lwjgl-sdl-$LWJGL_VER.jar on $I/fabric.cp to replace"; exit 1;; esac
  # host-only mod (scripts/klsdl-hostfix-src, added with -Dfabric.addMods, never in the instance or the bake): the JVM
  # twin of the image's MacosUtil substitution -- 26.3 asks AppKit for the Window menu real SDL3 builds and klsdl has not
  HF=$W/out/klsdl-hostfix; HS=$W/scripts/klsdl-hostfix-src
  if [ ! -f $HF/klsdl-hostfix.jar ] || [ -n "$(find $HS -newer $HF/klsdl-hostfix.jar -type f)" ]; then
    rm -rf $HF; mkdir -p $HF/classes
    $G/bin/javac -nowarn --release 21 -proc:none -d $HF/classes -cp "$(cat $I/fabric.cp)" $HS/kl/hostfix/*.java
    cp $HS/fabric.mod.json $HS/klsdl_hostfix.mixins.json $HF/classes/
    ( cd $HF/classes && zip -qr -X ../klsdl-hostfix.jar . )
  fi
  export LTW_EGL_LIBRARY=$ANGLE/libEGL.dylib KLSDL_FPS=${KLSDL_FPS:-1}
  KLARGS=(-Djava.awt.headless=true -Dorg.lwjgl.librarypath=$LWJGL_OUT/macosx -Dorg.lwjgl.opengl.libname=$LTW
          -Dorg.lwjgl.system.allocator=system -Dorg.lwjgl.util.NoHashChecks=true -Dfabric.addMods=$HF/klsdl-hostfix.jar)
  echo "runfabric: klsdl ($KJ), GL $LTW, ANGLE $ANGLE"
fi
cd $I/fgamedir
$G/bin/java -XstartOnFirstThread -Xmx3G "${KLARGS[@]}" "$@" -cp "$CP" ${MAIN:-net.fabricmc.loader.impl.launch.knot.KnotClient} \
  --accessToken 0 --username spike --uuid 00000000-0000-0000-0000-000000000000 --version $MCV \
  --gameDir $I/fgamedir --assetsDir $ASSETS --assetIndex $AIDX $GAMEARGS &
P=$!
( sleep $T; kill $P 2>/dev/null; sleep 5; kill -9 $P 2>/dev/null ) &
wait $P; echo "EXIT $?"
