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

`CFBundleShortVersionString` / `MARKETING_VERSION` defaults to **1.0.0** in `visionos/gen_xcodeproj.py` (`KLEPTON_MARKETING_VERSION` overrides it). `CFBundleVersion` defaults to `1` (`KLEPTON_BUILD_VERSION`). The short version, the GitHub release, and the OTA version are the same string, 1.0.0. The display name is `Vivecraft`. The Xcode product name stays `KleptonVivecraft`.

## Machine

- Apple Silicon Mac.
- Xcode 27 with the visionOS SDK. Deployment target 26.0.
- Python 3 with Pillow. `visionos/mkicon.py` imports it. `run.sh` runs that step with `python3.13` when `python3.13 -c "import PIL"` works, and otherwise with `python3`.
- About 40 GB free. An ANGLE-from-source build needs about 12 GB more.
- Enough RAM for `native-image -J-Xmx28g`. The bake was written for a 36 GB machine. It will not start on a 16 GB Mac.
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

Either the repo already contains the retargeted frameworks at `vendor/out/xros` and `vendor/out/xrsim` (about 12 MB each), or you build them:

```
make angle-fetch
make angle-xros angle-xrsim
```

`angle-fetch` clones https://chromium.googlesource.com/angle/angle.git at `25e721127e1c6c4c6fa0182b5c234b2c88971175`, syncs depot_tools at `6afa997717b2c0e1382e1465bedbe1a6855b9388`, and applies `angle-patches/klepton.patch`. The build is about 12 GB and 30–60 minutes. The app command below uses `KL_ANGLE_PREBUILT=1`, which reads those two framework trees.

This repo commits those two framework trees and the BSD notice at `vendor/out/LICENSE`. `KL_ANGLE_PREBUILT=1` uses them. Run the `make` lines above only when you want to replace them.

## 3. Vivecraft jar

From `work/vivecraft-openxr-26.2`:

```
git clone -b openxr https://github.com/Vivecraft/VivecraftMod VivecraftMod
git -C VivecraftMod checkout -B kl-mobile 13eafae1
git -C VivecraftMod am "$ROOT/work/vivecraft-openxr-26.2/patches/"*.patch
```

The tree after the patches is `8297f0ecc84bb25aecf714c23dae661cf6cdf626`. License: LGPLv3.

```
cd VivecraftMod
printf 'project.build.excludeForge=true\nproject.build.excludeNeoforge=true\n' > local.properties
export JAVA_HOME="$ROOT/work/native-image/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home"
./gradlew --no-daemon :fabric:build -x test -Pfabric_api_version=0.161.0+26.2
```

The jar is `VivecraftMod/build/libs/vivecraft-26.2-1.3.15-fabric.jar`.

Expected sha256 (patches 0001–0005):

```
e241a4c2888bd7158e5177916b6f524b862801c192a08f7d967fc3f21ee5be7d
```

`work/native-image/scripts/m3a/mods-26.2.json` sets `local` to `work/vivecraft-openxr-26.2/VivecraftMod/build/libs/vivecraft-26.2-1.3.15-fabric.jar` (relative to the repo root) and `sha256` to the hash above. The jar is a build output. Do not commit it.

## 4. Client, libraries, assets, mods

From `work/native-image`:

```
python3 -I scripts/m3a/fetch-inst.py 26.2
```

This downloads the Minecraft 26.2 client, vanilla and Fabric libraries, LWJGL 3.4.1 jars, asset index 32, and the Modrinth mods named in `mods-26.2.json`. It checks hashes. It does not check that you own the game.

`fetch-inst.py`, `inst.sh`, and `jdk.sh` take the repo root from their own path. `MCV` defaults to 26.2. Unset `MCV` selects the JDK 25 tree above. `MCV=1.*` selects GraalVM 21.0.12.

## 5. Natives

Still from `work/native-image` unless a path says otherwise.

```
work/natives/scripts/fetch-lwjgl341.sh
scripts/t2/build-lwjgl341-natives.sh
scripts/t2/build-lwjgl341-natives-xros.sh
scripts/t2/build-freetype.sh
scripts/t2/build-klglfw341.sh
```

`fetch-lwjgl341.sh` clones LWJGL 3.4.1 and checks the commit `b800ccffab14396fc529ddb6c931b7c5c5226763`.

PojavLauncherTeam LTW, LGPL-3.0. The Makefile compiles this checkout in place and does not edit it. `work/natives/ltw/darwin/` and the Makefile are the patch set, and they are in git.

```
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
scripts/t8b/build-natives.sh
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

Build the bake agent (default package, `Premain-Class: BakeAgent`):

```
mkdir -p bakeagent /tmp/bakeagent-classes
javac -d /tmp/bakeagent-classes scripts/bake-src/BakeAgent.java
jar cfm bakeagent/bakeagent.jar scripts/bake-src/MANIFEST.MF -C /tmp/bakeagent-classes .
```

There is no wrapper script. Those three lines are the build.

Run Knot with the agent. This is the bake that produced the image. It needs a display and takes a few minutes. `-Dvivecraft.openxr.mobile=true` selects the mobile OpenGL ES path.

```
MCV=26.2 GAMEARGS="--quickPlaySingleplayer world" \
  scripts/runfabric.sh 170 \
  -javaagent:"$PWD/bakeagent/bakeagent.jar=$PWD/inst-26.2/bake" \
  -Dbake.fullAt=100 \
  -Dvivecraft.openxr.mobile=true
```

`inst.sh` names the instance directory from `MCV`. With the default, that argument is `inst-26.2/bake` when the commands run from `work/native-image`.

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
