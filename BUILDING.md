# Building vivecraft-visionos

Build the visionOS app from this repo plus the downloads below. The Minecraft client, the mod jars, and `libminecraft.dylib` are build outputs. They are not in git. GitHub rejects files over 100 MB, and `libminecraft.dylib` is about 202 MB of compiled client.

The layout below is the one this tree builds. Script roots come from the script path. `KLEPTON_TEAM` is an environment variable. This repo does not store a team id.

Set the signing team with the environment. The repo has no team id in it.

```
export ROOT="$(pwd)"          # the clone
export KLEPTON_TEAM="<your Apple Development Team ID>"
```

A simulator build does not need `KLEPTON_TEAM`. `run.sh` sets `CODE_SIGNING_ALLOWED=NO` for the simulator.

The public bundle id is `com.rebelancap.vivecraft-visionos`. It is the `bundle` field on the vivecraft row in `visionos/targets.py`. `bundle_for()` returns that string and does not prefix a login name. `KLEPTON_BUNDLE_ID` still overrides it. SideStore matches this id.

`CFBundleShortVersionString` / `MARKETING_VERSION` defaults to **1.0.2** in `visionos/gen_xcodeproj.py` (`KLEPTON_MARKETING_VERSION` overrides it). `CFBundleVersion` defaults to `1` (`KLEPTON_BUILD_VERSION`). The short version, the GitHub release, and the OTA version are the same string, 1.0.2. The display name is `Vivecraft`. The Xcode product name stays `KleptonVivecraft`.

## Machine

- Apple Silicon Mac.
- Xcode 27 with the visionOS SDK. Deployment target 26.0.
- Python 3 with Pillow. `visionos/mkicon.py` imports it. `run.sh` runs that step with `python3.13` when `python3.13 -c "import PIL"` works, and otherwise with `python3`.
- About 40 GB free. An ANGLE-from-source build needs about 12 GB more.
- CMake, Git, curl, make, and the Xcode command-line tools.
- RAM for the native-image step. `build-shared.sh` defaults to `-J-Xmx28g` (`NATIVE_IMAGE_HEAP=28g`), the setting it was written with on a 36 GB Mac. `NATIVE_IMAGE_HEAP=12g NATIVE_IMAGE_THREADS=1` built the same 26.2 image on a 32 GiB M4 Mac mini: about 10.5 minutes, 7.44 GiB peak RSS ([avp-craft CLEAN-BUILD.md](https://github.com/zschwendi/avp-craft/blob/main/docs/CLEAN-BUILD.md)). A 16 GB Mac is untested.
- A Microsoft account that owns Minecraft: Java Edition, used when you **launch**. The download step does not check ownership.

The simulator build targets the **Apple Vision Pro** simulator on visionOS 27.0. `KL_XR_STAGE_DROP=1.6` is the simulator head offset.

## 1. GraalVM

Install Oracle GraalVM JDK 25.0.4 into:

```
work/native-image/graalvm/graalvm-jdk-25.0.4+7.1
```

Download:

```
https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_macos-aarch64_bin.tar.gz
```

Confirm `Contents/Home/release` reads `25.0.4`. The JDK is under the [GraalVM Free Terms and Conditions](https://www.oracle.com/downloads/licenses/graal-free-license.html). Do not commit it.

The tarball that produced this image is 353,673,229 bytes, sha256 `0b79e23c133facbad2f7aa55a3b76d17bd59d2fa15e2735bb63391ace223fd13`. Oracle serves that file as `latest` (Last-Modified Thu, 16 Jul 2026 13:18:02 GMT). Check the sha256 before you trust a new download. `Contents/Home/release` reads `JAVA_VERSION=25.0.4` and `JAVA_RUNTIME_VERSION=25.0.4+7-LTS-jvmci-b01`.

Point `JAVA_HOME` at `.../graalvm-jdk-25.0.4+7.1/Contents/Home` for the Vivecraft Gradle build.

## 2. ANGLE

The repo contains the retargeted frameworks at `vendor/out/xros` and `vendor/out/xrsim` (about 12 MB each). The native builds in step 5 also need the ANGLE and Vulkan headers, which the frameworks do not carry. Fetch them from the repo root:

```
python3 -I work/natives/scripts/fetch-graphics-headers.py
```

It downloads `include/` of ANGLE `25e72112` and of Vulkan-Headers `11d68983` (the pin in that ANGLE commit's `DEPS`), checks a content hash of each, and fills `vendor/include` and `vendor/third_party/vulkan-headers/src/include`. It does not overwrite a header that differs. Skip it if you run `make angle-fetch`, which checks out the same headers.

To build the frameworks yourself instead:

```
make angle-fetch
make angle-xros angle-xrsim
```

`angle-fetch` clones https://chromium.googlesource.com/angle/angle.git at `25e721127e1c6c4c6fa0182b5c234b2c88971175`, syncs depot_tools at `6afa997717b2c0e1382e1465bedbe1a6855b9388`, and applies `angle-patches/klepton.patch`. The build is about 12 GB and 30–60 minutes. The app command below uses `KL_ANGLE_PREBUILT=1`, which reads those two framework trees.

This repo commits those two framework trees and the BSD notice at `vendor/out/LICENSE`. `KL_ANGLE_PREBUILT=1` uses them. Run the `make` lines above only when you want to replace them.

## 3. Vivecraft jar

From `work/vivecraft-openxr-26.2` (`cd "$ROOT/work/vivecraft-openxr-26.2"`):

```
git clone -b openxr https://github.com/Vivecraft/VivecraftMod VivecraftMod
git -C VivecraftMod checkout -B kl-mobile 13eafae1
git -C VivecraftMod am "$ROOT/work/vivecraft-openxr-26.2/patches/"*.patch
```

Patches 0001–0005 are the port. Patch 0006 lowers the head-locked in-world HUD by 15 degrees (it makes `hudPitchOffset` act on that HUD and sets new-profile defaults: HEAD, 2 m, 0.8 scale, −15°). After all six, `git -C VivecraftMod rev-parse 'HEAD^{tree}'` prints `bc542a09e32f6f9b74f54ddb4a37373d2b32c902`. That is a tree hash; the commit ids `git am` makes depend on your name and the time. (The `8297f0ec…` in patch 0005's header is the private commit id, not a tree.) License: LGPLv3.

```
cd VivecraftMod
printf 'project.build.excludeForge=true\nproject.build.excludeNeoforge=true\n' > local.properties
export JAVA_HOME="$ROOT/work/native-image/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home"
./gradlew --no-daemon :fabric:build -x test -Pfabric_api_version=0.161.0+26.2
mkdir -p build/canonical
rm -f build/canonical/vivecraft-26.2-1.3.15-fabric.jar
python3 -I ../canonicalize-vivecraft-jar.py \
  build/libs/vivecraft-26.2-1.3.15-fabric.jar \
  build/canonical/vivecraft-26.2-1.3.15-fabric.jar
```

Gradle 9.3.0 (the wrapper) resolves Loom 1.15.5. Add `-Pfabric_loom=1.15.5` if a newer 1.15 snapshot appears.

The raw jar in `build/libs/` is not byte-reproducible: Loom writes changing ZIP metadata for the nested jars even when every file inside is identical. `canonicalize-vivecraft-jar.py` rewrites it as a sorted, uncompressed ZIP with fixed metadata and the same entries and bytes. It refuses to overwrite its output, hence the `rm -f`.

Expected sha256 of `build/canonical/vivecraft-26.2-1.3.15-fabric.jar` (patches 0001–0006, 19,032,717 bytes):

```
f317964c49f8e1dace17dcc4ea4a8d4f9d29c7e5a2888c61b73259f4355deac4
```

The same hash came out of GraalVM 25.0.4 (above) and OpenJDK 25.0.4.1 as `JAVA_HOME`.

The `libminecraft.dylib` in releases 1.0.1 and 1.0.2 was baked from patches 0001–0005, so it does not have the lower HUD. Patch 0006 needs a new Knot bake and native image (steps 6 and 7). See UPSTREAM.md.

`work/native-image/scripts/m3a/mods-26.2.json` sets `local` to `work/vivecraft-openxr-26.2/VivecraftMod/build/canonical/vivecraft-26.2-1.3.15-fabric.jar` (relative to the repo root) and `sha256` to the hash above. The jar is a build output. Do not commit it.

## 4. Client, libraries, assets, mods

From `work/native-image`:

```
python3 -I scripts/m3a/fetch-inst.py 26.2
```

This downloads the Minecraft 26.2 client, vanilla and Fabric libraries, LWJGL 3.4.1 jars, asset index 32, and the Modrinth mods named in `mods-26.2.json`. It checks hashes. It does not check that you own the game.

`fetch-inst.py`, `inst.sh`, and `jdk.sh` take the repo root from their own path. `MCV` defaults to 26.2. Unset `MCV` selects the JDK 25 tree above. `MCV=1.*` selects GraalVM 21.0.12.

## 5. Natives

From the repo root (`cd "$ROOT"`). Fetch the two source tarballs the LWJGL and FreeType builds read, and check them:

```
mkdir -p work/native-image/dl/libffi work/natives/downloads
curl -fL https://github.com/libffi/libffi/releases/download/v3.5.2/libffi-3.5.2.tar.gz \
  -o work/native-image/dl/libffi/libffi-3.5.2.tar.gz
echo "f3a3082a23b37c293a4fcd1053147b371f2ff91fa7ea1b2a52e335676bac82dc  work/native-image/dl/libffi/libffi-3.5.2.tar.gz" | shasum -a 256 -c -
curl -fL https://github.com/freetype/freetype/archive/refs/tags/VER-2-14-1.tar.gz \
  -o work/natives/downloads/freetype-VER-2-14-1.tar.gz
echo "44bd69d1f0750603410cfd4f26f1c5523c5a3a087a2dfa8639b757dc7c6677be  work/natives/downloads/freetype-VER-2-14-1.tar.gz" | shasum -a 256 -c -
```

Then, in this order:

```
work/natives/scripts/build-openal.sh
work/natives/scripts/fetch-lwjgl341.sh
work/native-image/scripts/t2/build-lwjgl341-natives.sh
work/native-image/scripts/t2/build-lwjgl341-natives-xros.sh
work/native-image/scripts/t2/build-klglfw341.sh
```

`build-openal.sh` builds OpenAL Soft 1.25.2 for `xros` and `xrsimulator` into `work/natives/openal/<sdk>/libopenal.dylib`. It downloads the tag archive and checks sha256 `fb27e583…a6877894` if it is not already in `work/natives/downloads/openal/`. It must run before `build-lwjgl341-natives-xros.sh`, which copies that dylib. It sets `ALSOFT_RTKIT=OFF`: with RTKit on, CMake finds a Homebrew D-Bus on the host and the visionOS build then fails in `core/rtkit.cpp`.

`fetch-lwjgl341.sh` clones LWJGL 3.4.1 and checks the commit `b800ccffab14396fc529ddb6c931b7c5c5226763`. `build-lwjgl341-natives.sh` with no argument builds the macOS libraries the host JVM bake uses. The visionOS builder calls `build-freetype.sh` itself.

`JOBS=<n>` caps parallel compiler jobs in the native helpers (LWJGL/libffi, FreeType, Simple Voice Chat, the LTW fallback in `t8b/build-natives.sh`). The default is every core. Use `JOBS=2` or `JOBS=1` on a small Mac. It does not touch Gradle or native-image.

PojavLauncherTeam LTW, LGPL-3.0. The Makefile compiles this checkout in place and does not edit it. `work/natives/ltw/darwin/` and the Makefile are the patch set, and they are in git.

```
mkdir -p work/src
git clone https://github.com/PojavLauncherTeam/ltw work/src/PojavLauncherTeam_ltw
git -C work/src/PojavLauncherTeam_ltw checkout 7703f1c29a1fd7f151484c38fb7563bb79ca8ccb
make -C work/natives/ltw SDK=xros ANGLE_INC="$ROOT/vendor/include"
make -C work/natives/ltw SDK=xrsimulator ANGLE_INC="$ROOT/vendor/include"
```

`work/natives/ltw/Makefile` defaults `VK_INC` to `$(ROOT)/vendor/third_party/vulkan-headers/src/include`. Those headers exist after `make angle-fetch`. They are header-only. An already-built `libltw` does not need them to link the app. The default is the repo root, not a home directory.

Simple Voice Chat codecs, and the OpenXR loader shims:

```
work/natives/scripts/build-svc.sh
work/natives/scripts/build-openxr-shim.sh
mkdir -p work/native-image/lwjgl-fork-t4/natives
work/native-image/scripts/t8b/build-natives.sh
```

`build-svc.sh` clones the henkelmax JNI repos and checks out the pins in `work/natives/svc/PROVENANCE.md` (opus4j `362db14553d39c4dc080e4edd50630b17dbf9682`, rnnoise4j `5f2097d504fc67f31d0d8900e9df0a5bdf4ec9ae`, speex4j `9428cb14b8ec373fcd36f5b5792899611042ece9`, lame4j `b09599989878cbc7349075eca0b3aa4f105e5957`).

`build-natives.sh` builds `libklegl`.

`build-openxr-shim.sh` includes `jni.h` from `work/native-image/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home`. `$G` defaults to that tree.

`ANGLE_INC` in `scripts/t2/` and `scripts/t8b/build-natives.sh` defaults to `$ROOT/vendor/include`.

## 6. Classpath and Knot bake

From `work/native-image`, with `MCV=26.2` on every step:

```
MCV=26.2 scripts/mkcp.sh
MCV=26.2 scripts/mkcp-fabric.sh
```

`mkcp-fabric.sh` copies config, `options.txt` and a world from an older instance when one exists. A clean clone has none, and it skips them.

Build the bake agent (default package, `Premain-Class: BakeAgent`). `BakeAgent.java` uses ASM, which is on the Fabric classpath that `mkcp-fabric.sh` just wrote:

```
export JAVA_HOME="$ROOT/work/native-image/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home"
BA=$(mktemp -d)
mkdir -p bakeagent
"$JAVA_HOME/bin/javac" -cp "$(cat inst-26.2/fabric.cp)" -d "$BA" scripts/bake-src/BakeAgent.java
"$JAVA_HOME/bin/jar" cfm bakeagent/bakeagent.jar scripts/bake-src/MANIFEST.MF -C "$BA" .
```

There is no wrapper script. Those lines are the build.

The bake loads a singleplayer world named `world`. A clean clone has none, so make one first. Start Knot without the agent (the `3600` is `runfabric.sh`'s kill timer in seconds; it only has to outlast you):

```
MCV=26.2 scripts/runfabric.sh 3600 -Dvivecraft.openxr.mobile=true
```

Choose **Singleplayer**, **Create New World**, name it `world`, and wait until the terrain has loaded. Then **Save and Quit to Title** and quit the game. Check that `inst-26.2/fgamedir/saves/world/level.dat` exists: Minecraft names the folder after the world, and picks another name if `world` is taken. Use a throwaway world. The bake does not need your real one.

Now run Knot with the agent. This is the bake that produced the image. It needs a display. `-Dvivecraft.openxr.mobile=true` selects the mobile OpenGL ES path.

```
MCV=26.2 GAMEARGS="--quickPlaySingleplayer world" \
  scripts/runfabric.sh 600 \
  -javaagent:"$PWD/bakeagent/bakeagent.jar=$PWD/inst-26.2/bake" \
  -Dbake.fullAt=100 \
  -Dvivecraft.openxr.mobile=true
```

`inst.sh` names the instance directory from `MCV`. With the default, that argument is `inst-26.2/bake` when the commands run from `work/native-image`.

`-Dbake.fullAt=100` starts the full pass 100 seconds after JVM start. The world must be loaded by then; raise it on a slow Mac. The pass is done when the terminal prints `[BAKE] defined-at-runtime=…` and `inst-26.2/bake/summary.txt` exists. Then **Save and Quit** and quit the game before the `600`-second timer kills it. The image was built with a 170-second timer, which is tight on a cold first run.

The full pass reports 49 failed classes (`failed=49` in the summary, names in `inst-26.2/bake/fullpass-failures.txt`). Under the pinned mod set and configs these are expected. They are original mixin classes that Knot will not load as plain classes: 16 interface mixin templates, 25 anonymous/local classes inside mixin packages, 5 Lithium mixin-package helper classes, 2 Indigo accessors (Indigo is off because Sodium is the renderer), and 1 Lithium pathing accessor (`mixin.ai.pathing=false` by default). None of them is referenced by a transformed class under its own name. zschwendi/avp-craft audited every one from a clean build: [docs/KNOT-BAKE.md](https://github.com/zschwendi/avp-craft/blob/main/docs/KNOT-BAKE.md). The bake behind the shipped image (patches 0001–0005) failed on the same 49 class names. The count alone proves nothing. A different list, a changed mod set, Indigo on, or a Lithium override in `config/lithium.properties` means a new bake that needs its own check.

Prune classes the bake copied unchanged from library jars. Keep classes a mixin actually changed.

```
python3 -I scripts/prune_overlay.py inst-26.2/bake/classes \
  inst-26.2/vanilla/lib/*.jar inst-26.2/fabric/lib/*.jar
```

Then delete these five LWJGL 3.4.1 multi-release classes if they are still in the overlay. They would shadow the natives built in step 5: `org/lwjgl/VersionImpl`, `org/lwjgl/system/CheckIntrinsics`, `org/lwjgl/system/MemoryUtilTunables`, `org/lwjgl/system/MultiReleaseTextDecoding`, `org/lwjgl/system/StackWalkUtil`.

The bake that produced this image recorded this in `inst-26.2/bake/PRUNE-NOTE.txt` (a build product, not committed):

```
prune_overlay.py (30752 identical lib copies removed, 17 kept) + the 5 LWJGL 3.4.1 multi-release copies removed by hand
(org/lwjgl/VersionImpl, org/lwjgl/system/CheckIntrinsics, org/lwjgl/system/MemoryUtilTunables, org/lwjgl/system/MultiReleaseTextDecoding, org/lwjgl/system/StackWalkUtil): they are
the JDK-25 META-INF/versions variants Knot picked, not transformed classes, and would shadow the natives agent's LWJGL.
```

`scripts/prune_overlay.py` is the deleter. `scripts/prune-overlay.sh` is the old one. Do not use it.

```
MCV=26.2 scripts/mkcp-baked.sh
MCV=26.2 scripts/m3a/build-launcher.sh
python3 -I scripts/m3a/pin-gl-options.py inst-26.2/fgamedir/options.txt
MCV=26.2 bash scripts/t4/build-t4.sh
```

`build-launcher.sh` calls `mkcp-baked.sh` again. `pin-gl-options.py` writes `preferredGraphicsBackend:"opengl"`. Run it after the Knot bake, because that run rewrites `options.txt`. `build-t4.sh` writes `inst-26.2/t4.cp` and `inst-26.2/t4-ni.cp`.

The committed reachability JSON is the trace for this classpath:

- `inst-26.2/config/t23-trim/reachability-metadata.json`
- `inst-26.2/config/t23-extra/reflect-config.json`
- `inst-26.2/config/t23-extra/resource-config.json`

Re-run `scripts/t8/gen-reflect-extra.py` only when the classpath changes, and commit the new JSON with the image that used it.

Those three JSON files are in this tree. `*.cp` files are generated and contain absolute paths. `.gitignore` excludes them. Do not commit them.

## 7. Native image

From `work/native-image`. `-O1` is the default when `CPFILE` points at an `inst-*` classpath. `-O2` does not link at this size.

`build-shared.sh` reads `NATIVE_IMAGE_HEAP` (default `28g`; a positive number followed by `m` or `g`) and `NATIVE_IMAGE_THREADS` (default unset, which leaves native-image's own `--parallelism`). It checks both, and checks that every `CONFIG_DIRS` entry is a directory, before it starts. On a 32 GiB Mac add `NATIVE_IMAGE_HEAP=12g NATIVE_IMAGE_THREADS=1`. It refuses to start when `$T8OUT/ni` or `$T8OUT/tmp` is not empty, so it never deletes an earlier attempt: remove them yourself, or pick a new `T8OUT` and `LOGTAG` (the log is `logs/t8-build-$LOGTAG.txt`). `relink-xros.sh` replaces `$T8OUT/<sdk>/libminecraft.dylib` only after clang reports a successful link.

```
MCV=26.2 \
  CPFILE="$PWD/inst-26.2/t4-ni.cp" \
  CONFIG_DIRS="$PWD/inst-26.2/config/t23-trim,$PWD/inst-26.2/config/t23-extra" \
  T8OUT="$PWD/out/t8-26.2" \
  scripts/t8/build-shared.sh

MCV=26.2 T8OUT="$PWD/out/t8-26.2" scripts/t8/relink-xros.sh xros
MCV=26.2 T8OUT="$PWD/out/t8-26.2" scripts/t8/relink-xros.sh xrsimulator
```

`relink-xros.sh` runs `work/xros-probe/scripts/patch_buildver.py`. The device dylib is `out/t8-26.2/xros/libminecraft.dylib`.

`scripts/t8/host-run.sh` is an optional host smoke test of the dylib before you build the app.

`build-shared.sh` and `relink-xros.sh` set their root from the script path (`work/native-image`). `relink-xros.sh` calls `work/xros-probe/scripts/patch_buildver.py`.

## 8. The app

From `visionos/`:

```
MCV=26.2 KLEPTON_TARGET=vivecraft KL_ANGLE_PREBUILT=1 KL_NO_LAUNCH=1 \
  KLEPTON_TEAM="$KLEPTON_TEAM" \
  ./run.sh device
```

`run.sh`, `stage_native.sh`, and `mknative.sh` default `MCV` to 26.2. The launch template in `visionos/native/` is the 1.21.5 text. `stage_native.sh` rewrites version, asset index, game jar, and game-dir paths when `MCV` is not 1.21.5.

A device build defaults to Release (`KL_XCODE_CONFIG=Debug` selects Debug). `KL_BUNDLE_PAYLOAD` defaults on for a vivecraft device build and off for the simulator. Set `KL_BUNDLE_PAYLOAD=1` when the simulator app should carry its own payload. `KL_NO_INSTALL=1` writes a local IPA and does not look up or install on a paired headset.

`make xros` compiles `runtime/xr/kl_openvr.c`. When `vendor-moltenvk/out/include/vulkan/vulkan.h` is present (`make mvk` installs the headers and the MoltenVK frameworks), the OpenVR eye copy is included. Without that header the same file builds and the eye copy is omitted. Vivecraft's guest is OpenGL ES through ANGLE. `visionos/mkmvk.sh` embeds MoltenVK when the frameworks are there, and continues when they are not.

The first install that does not bundle the payload omits `KL_SKIP_STAGE`, so `stage_native.sh` fills the container (jars and assets, on the order of 0.6 GB). A payload build (`KL_BUNDLE_PAYLOAD=1`) runs `stage_native.sh --local` during `xcodebuild` and copies the scrubbed tree into the app. Later container installs can set `KL_SKIP_STAGE=1`.

A public device IPA:

```
MCV=26.2 KLEPTON_TARGET=vivecraft KL_ANGLE_PREBUILT=1 KL_BUNDLE_PAYLOAD=1 \
  KL_NO_INSTALL=1 KL_NO_LAUNCH=1 KL_SKIP_STAGE=1 \
  KLEPTON_TEAM="$KLEPTON_TEAM" \
  ./run.sh device
```

`visionos/scrub_paths.py` runs on the built app before the IPA is zipped, and before the simulator's ad-hoc sign. It rewrites the build machine's home prefix inside Mach-O copies when that prefix is 13 bytes, which keeps file offsets valid. The app is signed again after that rewrite. The private build inputs are not modified.

A simulator build is the same command without `device`, on the Apple Vision Pro simulator, visionOS 27.0. Set `KL_XR_STAGE_DROP=1.6` for that run. Set `KL_BUNDLE_PAYLOAD=1` if the simulator app should materialize from the bundle.

`run.sh` generates the Xcode project. Do not commit the generated project. It would capture the team id and the bundle id of the machine that wrote it.

The signed IPA is what a release publishes. Install it with SideStore. `KL_NO_LAUNCH=1` builds and does not launch.

## Acknowledgements

[zschwendi/avp-craft](https://github.com/zschwendi/avp-craft) rebuilt this tree from a clean clone and found what was missing: the launcher sources, the OpenAL build, the world the bake needs, the BakeAgent classpath, and the unreproducible Vivecraft jar. The fixes in steps 2, 3, 5, 6 and 7 come from that work: the canonical jar and patch 0006, the header fetch, the RTKit switch, the optional instance copies, the native-image heap and thread settings, and the Knot bake audit.
