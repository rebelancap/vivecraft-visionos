# Building vivecraft-visionos

Build the visionOS app from this repo plus the downloads below. This is the Minecraft 26.3 build (release 1.0.3 onward). Release 1.0.2 was Minecraft 26.2; see [Building 26.2](#building-262-release-102) at the end. The Minecraft client, the mod jars, and `libminecraft.dylib` are build outputs. They are not in git. GitHub rejects files over 100 MB, and `libminecraft.dylib` is about 211 MB of compiled client.

The layout below is the one this tree builds. Script roots come from the script path. `KLEPTON_TEAM` is an environment variable. This repo does not store a team id.

Set the signing team with the environment. The repo has no team id in it.

```
export ROOT="$(pwd)"          # the clone
export KLEPTON_TEAM="<your Apple Development Team ID>"
```

A simulator build does not need `KLEPTON_TEAM`. `run.sh` sets `CODE_SIGNING_ALLOWED=NO` for the simulator.

The public bundle id is `com.rebelancap.vivecraft-visionos`. It is the `bundle` field on the vivecraft row in `visionos/targets.py`. `bundle_for()` returns that string and does not prefix a login name. `KLEPTON_BUNDLE_ID` still overrides it. SideStore matches this id.

`CFBundleShortVersionString` / `MARKETING_VERSION` defaults to **1.0.3** in `visionos/gen_xcodeproj.py` (`KLEPTON_MARKETING_VERSION` overrides it). `CFBundleVersion` defaults to `1` (`KLEPTON_BUILD_VERSION`). The short version, the GitHub release, and the OTA version are the same string, 1.0.3. The display name is `Vivecraft`. The Xcode product name stays `KleptonVivecraft`.

## Machine

- Apple Silicon Mac.
- Xcode 27 with the visionOS SDK. Deployment target 26.0.
- Python 3 with Pillow. `visionos/mkicon.py` imports it. `run.sh` runs that step with `python3.13` when `python3.13 -c "import PIL"` works, and otherwise with `python3`.
- About 40 GB free. An ANGLE-from-source build needs about 12 GB more.
- CMake, Ninja (the shaderc and SPIRV-Cross builds), Git, curl, make, and the Xcode command-line tools.
- RAM for the native-image step. `build-shared.sh` defaults to `-J-Xmx28g` (`NATIVE_IMAGE_HEAP=28g`), the setting it was written with on a 36 GB Mac. The 26.3 image took 8 min 20 s there, peak RSS 18.32 GB (native-image's own summary). A 26.3 build with a smaller heap is untested. For 26.2, `NATIVE_IMAGE_HEAP=12g NATIVE_IMAGE_THREADS=1` built the image on a 32 GiB M4 Mac mini: about 10.5 minutes, 7.44 GiB peak RSS ([avp-craft CLEAN-BUILD.md](https://github.com/zschwendi/avp-craft/blob/main/docs/CLEAN-BUILD.md)). A 16 GB Mac is untested.
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

The repo contains the retargeted frameworks at `vendor/out/xros` and `vendor/out/xrsim` (about 12 MB each). The native builds in step 5 (ltw, klglfw, klsdl, klegl) also need the ANGLE and Vulkan headers, which the frameworks do not carry. Fetch them from the repo root:

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

Upstream has no OpenXR branch for 26.3. The base is `Multiloader-26.3` at `15a9011f` (mod 1.3.18). Patch 0000 is upstream's `openxr` branch (`13eafae1`) merged onto it and adapted to 26.3, squashed to one commit. That is upstream LGPLv3 code by the Vivecraft authors; `work/vivecraft-openxr-26.3/REPORT.md` lists every conflict and adaptation. Patches 0001–0005 are the port. Patch 0006 lowers the head-locked in-world HUD (it makes `hudPitchOffset` act on that HUD and sets new-profile defaults: HEAD, 2 m, 0.8 scale, −8°).

From `work/vivecraft-openxr-26.3` (`cd "$ROOT/work/vivecraft-openxr-26.3"`):

```
git clone https://github.com/Vivecraft/VivecraftMod VivecraftMod
git -C VivecraftMod checkout -B kl-mobile-26.3 15a9011f
git -C VivecraftMod am "$ROOT/work/vivecraft-openxr-26.3/patches/"*.patch
```

After 0000–0006, `git -C VivecraftMod rev-parse 'HEAD^{tree}'` prints `cb591f18073be44897964a6e646776f9fa81da22`. <!-- TODO(orchestrator): refresh after 0006 amend --> That is a tree hash; the commit ids `git am` makes depend on your name and the time. License: LGPLv3.

```
cd VivecraftMod
printf 'project.build.excludeForge=true\nproject.build.excludeNeoforge=true\n' > local.properties
export JAVA_HOME="$ROOT/work/native-image/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home"
export GRADLE_USER_HOME="$ROOT/work/vivecraft-openxr-26.3/gradle-home"
./gradlew --no-daemon :fabric:build -x test -Pfabric_api_version=0.165.2+26.3
mkdir -p build/canonical
rm -f build/canonical/vivecraft-26.3-1.3.18-fabric.jar
python3 -I ../../vivecraft-openxr-26.2/canonicalize-vivecraft-jar.py \
  build/libs/vivecraft-26.3-1.3.18-fabric.jar \
  build/canonical/vivecraft-26.3-1.3.18-fabric.jar
```

`GRADLE_USER_HOME` is optional. It keeps the Minecraft download, the decompile and the Fabric deps (about 800 MB) in the gitignored `gradle-home/` instead of `~/.gradle`. The cold build takes about 1 min 45 s; a warm one about 10 s.

Gradle 9.3.0 (the wrapper) resolves `fabric_loom=1.15-SNAPSHOT` to Loom 1.15.5, with fabric-loader 0.19.5. Add `-Pfabric_loom=1.15.5` if a newer 1.15 snapshot appears. Fabric API 0.165.2+26.3 is the port's pin; upstream's `gradle.properties` says 0.160.4+26.3.

The raw jar in `build/libs/` is not byte-reproducible: Loom writes changing ZIP metadata for the nested jars even when every file inside is identical. `canonicalize-vivecraft-jar.py` (the 26.2 directory's copy, used by relative path) rewrites it as a sorted, uncompressed ZIP with fixed metadata and the same entries and bytes. It refuses to overwrite its output, hence the `rm -f`.

Expected sha256 of `build/canonical/vivecraft-26.3-1.3.18-fabric.jar` (patches 0000–0006, 19,769,483 bytes): <!-- TODO(orchestrator): refresh after 0006 amend -->

```
bc2e03e81a1f555efd7389c5e5ed5bb6eb85e235f0e7f131eb6ca5509d0320f3
```

Two independent builds gave that hash; their raw Loom jars differed.

Static checks on the built jar (no game run):

```
python3 -I ../check-touch-map.py build/canonical/vivecraft-26.3-1.3.18-fabric.jar "$JAVA_HOME/bin/javap"
```

It prints `[touch-map] OK: 31 bindings == QuestCraft Touch layout (30) + key.push_to_talk -> …` and `[direct-post] OK: …`. It is the 26.2 script with the 26.3 `GpuTextureView` package (`com.mojang.renderpearl.api.textures`).

`work/native-image/scripts/m3a/mods-26.3.json` sets `local` to `work/vivecraft-openxr-26.3/VivecraftMod/build/canonical/vivecraft-26.3-1.3.18-fabric.jar` (relative to the repo root) and `sha256` to the hash above. The jar is a build output. Do not commit it. The 1.0.3 image is baked from this jar, so it has patch 0006 (see UPSTREAM.md).

## 4. Client, libraries, assets, mods

From `work/native-image`:

```
python3 -I scripts/m3a/fetch-inst.py 26.3
```

This downloads the Minecraft 26.3 client, vanilla and Fabric libraries (Fabric Loader 0.19.5), LWJGL 3.4.3 jars, asset index 34, and the mods named in `mods-26.3.json`: Fabric API 0.165.2+26.3, Sodium 0.9.2+mc26.3, Lithium 0.26.3+mc26.3, Simple Voice Chat 2.6.27+26.3 (a beta on Modrinth), Cloth Config 26.3.159, Entity Culling 1.11.3, FerriteCore 9.0.0, ImmediatelyFast 1.17.1+26.3, Krypton 0.3.2, More Culling 1.9.1, BadOptimizations 2.4.1, Better Biome Blend 1.4.1, and the Vivecraft jar from step 3. It checks hashes. It does not check that you own the game. Objects are APFS-cloned from an older instance's asset dir when one is on disk (`inst-26.2`, …), so only new hashes download.

If the Vivecraft jar is not built yet, `fetch-inst.py` installs everything else and prints a `WARNING: Vivecraft.jar NOT installed` line. Build it and run the command again.

Lithium 0.26.3 turns its `mixin.ai.pathing` mixins on by default (0.25.3 had them off). `config/lithium.properties` has no override, so they are active in this image.

`fetch-inst.py`, `inst.sh`, and `jdk.sh` take the repo root from their own path. `MCV` defaults to 26.3. Unset `MCV` selects the JDK 25 tree above. `MCV=1.*` selects GraalVM 21.0.12.

## 5. Natives

From the repo root (`cd "$ROOT"`). `work/natives/scripts/lwjgl-ver.sh` is the pin table these scripts share: `MCV` 26.3 (the default) selects LWJGL 3.4.3, FreeType 2.14.3 and libffi 3.8.0; `MCV=26.2` selects 3.4.1 / 2.14.1 / 3.5.2; `LWJGL_VER` overrides. The "341" in the script names is historical. Outputs go to `work/natives/build/lwjgl-3.4.3/<platform>/`.

In this order:

```
work/natives/scripts/build-openal.sh
work/natives/scripts/fetch-lwjgl341.sh
work/native-image/scripts/t2/build-lwjgl341-natives.sh
work/natives/scripts/build-shaderc-spvc.sh macosx
work/native-image/scripts/t2/build-lwjgl341-natives-xros.sh
work/native-image/scripts/t2/build-klglfw341.sh
work/native-image/scripts/t2/build-klsdl.sh
```

`build-openal.sh` builds OpenAL Soft 1.25.2 for `xros` and `xrsimulator` into `work/natives/openal/<sdk>/libopenal.dylib`. It downloads the tag archive and checks sha256 `fb27e583…a6877894` if it is not already in `work/natives/downloads/openal/`. It must run before `build-lwjgl341-natives-xros.sh`, which copies that dylib. It sets `ALSOFT_RTKIT=OFF`: with RTKit on, CMake finds a Homebrew D-Bus on the host and the visionOS build then fails in `core/rtkit.cpp`.

`fetch-lwjgl341.sh` clones LWJGL 3.4.3 (tag `3.4.3`) and checks the commit `30fac9b95f99cda97312232be25ba55297bf9951`. It fetches the Maven Central jars (the 3.4.1 module list plus `lwjgl-sdl`, `lwjgl-shaderc`, `lwjgl-spvc`, each with its `natives-macos-arm64` jar, and the `:unsafe` core), and the two source tarballs the native builds read, each checked against the pin table:

| Tarball | Path | sha256 |
| --- | --- | --- |
| https://github.com/freetype/freetype/archive/refs/tags/VER-2-14-3.tar.gz | `work/natives/downloads/freetype-VER-2-14-3.tar.gz` | `dc49de6b01a266eef4876a4dd34d9842c475d3e28ff2eff63bd2fb760ab56261` |
| https://github.com/libffi/libffi/releases/download/v3.8.0/libffi-3.8.0.tar.gz | `work/native-image/dl/libffi/libffi-3.8.0.tar.gz` | `7da3e2d9a171eb0a038f592ecad3ff2bb2550f3496d87b3b29ad0cf4430c0db4` |

It also checks that LWJGL 3.4.3's `libfreetype.dylib.git` names FreeType commit `0a0221a1347e2f1e07c395263540026e9a0aa7c7`. libffi 3.5.2 no longer links: LWJGL 3.4.3 bundles libffi 3.8.0's `ffi.h` and calls `ffi_call_plan_*`, which is new in 3.8.0.

`build-lwjgl341-natives.sh` with no argument builds the macOS libraries the host JVM bake uses, and copies the stock macOS `libopenal`, `libjemalloc`, `libfreetype` and `libSDL3` (host only) from the Maven natives jars. The visionOS builder calls `build-freetype.sh` itself.

`build-shaderc-spvc.sh`: Minecraft 26.3 compiles every shader GLSL → SPIR-V with shaderc and, on OpenGL, SPIR-V → GLSL with SPIRV-Cross, even on the GL backend. LWJGL ships those two libraries only for desktop. The script fetches shaderc `2c8cae778eec0283b44acbe7ed1a386865d78799` with its DEPS pins glslang `168d452a4f460d24b588fed08477a81c44ee27a1`, SPIRV-Tools `b707790a898e44038547df54580022fc1cf89c3d` and SPIRV-Headers `29981f65241605e08b0ede4cfeb999fe3b723c6a`, and SPIRV-Cross `6c09849fe88c48eaed08413aa022aaa136a3a057` (exact-commit shallow fetches into `work/natives/downloads/`; it checks HEAD and that the stock 3.4.3 jars name the same shaderc and SPIRV-Cross commits). It cross-builds `libshaderc.dylib` and `libspirv-cross.dylib` for `xrsimulator` and `xros`. With `macosx` it copies the stock Maven dylibs for the host. `build-lwjgl341-natives-xros.sh` runs it for the two visionOS SDKs after the core natives.

`build-klglfw341.sh` still has to run on 26.3: `build-t4.sh` checks for its jar and the `READY-<platform>` files it writes. 26.3 references no GLFW member (`scripts/t2/glfwrefs-26.3.txt` is empty), so its coverage gate is trivial.

`build-klsdl.sh` builds **klsdl**, the SDL3 stand-in. Minecraft 26.3 replaced GLFW with SDL3 (`lwjgl-sdl`), so klsdl takes klglfw's place on 26.3. Output: `klsdl/lwjgl-sdl-klsdl-3.4.3.jar`, which keeps the stock jar's `SDL_*` struct and callback classes and replaces every function class with `work/native-image/scripts/t4-src/java-sdl/org/lwjgl/sdl/*.java`, and `<platform>/libklsdl.dylib` from `scripts/t4-src/native/klsdl.c` (which includes `klglfw.c` and exports the same `klglfw_*` C API the shell calls). Its coverage gate fails the build unless every `org/lwjgl/sdl` member the 26.3 classpath references resolves in the jar. The reference list is regenerated from `inst-26.3` when step 4 has run, else read from the committed `scripts/t2/sdlrefs-26.3.txt`. It needs the ANGLE headers from step 2.

Order matters. `build-lwjgl341-natives.sh` deletes and recreates `work/natives/build/lwjgl-<ver>/<platform>/` before it builds. Run the shaderc `macosx` copy, `build-klglfw341.sh` and `build-klsdl.sh` after the core natives script for that platform, or their dylibs are wiped.

`JOBS=<n>` caps parallel compiler jobs in the native helpers (LWJGL/libffi, FreeType, shaderc/SPIRV-Cross, Simple Voice Chat, the LTW fallback in `t8b/build-natives.sh`). The default is every core. Use `JOBS=2` or `JOBS=1` on a small Mac. It does not touch Gradle or native-image.

PojavLauncherTeam LTW, LGPL-3.0. The Makefile compiles this checkout in place and does not edit it. `work/natives/ltw/darwin/` and the Makefile are the patch set, and they are in git.

```
mkdir -p work/src
git clone https://github.com/PojavLauncherTeam/ltw work/src/PojavLauncherTeam_ltw
git -C work/src/PojavLauncherTeam_ltw checkout 7703f1c29a1fd7f151484c38fb7563bb79ca8ccb
make -C work/natives/ltw SDK=xros ANGLE_INC="$ROOT/vendor/include"
make -C work/natives/ltw SDK=xrsimulator ANGLE_INC="$ROOT/vendor/include"
```

`work/natives/ltw/Makefile` defaults `VK_INC` to `$(ROOT)/vendor/third_party/vulkan-headers/src/include`. Those headers exist after step 2. They are header-only. An already-built `libltw` does not need them to link the app. The default is the repo root, not a home directory.

The Darwin patch set carries the fixes 26.3's SPIRV-Cross output (desktop GLSL 330) needs:

- `darwin/ltw_darwin_glslconv_limits.h`, force-included into upstream `GlslConvert.cpp`: the per-stage uniform-block limit 4 → 12 (ES 3.0's minimum; 26.3's terrain shader has 5 vertex blocks).
- `darwin/ltw_darwin_shader.c`: a failed conversion gives ANGLE a small ESSL source with an `#error` naming the failure instead of NULL (which crashed in `glShaderSource`), and fragment output arrays indexed with a non-constant expression (the translucency pipelines, 54 fragment shaders) are rewritten to a temporary array plus constant-index copies at the end of `main`.
- Section 7c of `darwin/ltw_darwin_gl33.c`: `glEnablei`/`glDisablei`/`glIsEnabledi`/`glColorMaski`/`glBlendFunc*i`/`glBlendEquation*i` go to `EXT`/`OES_draw_buffers_indexed` (core `*i` needs ES 3.2); the seamless-cubemap and program-point-size `glEnable`s are no-ops.

Debug knobs, all off by default: `LTW_DARWIN_SHADER_DUMP=<dir>` writes every shader before and after conversion; `LTW_DARWIN_FRAGOUT_ARRAY=0` disables the fragment-output rewrite; `LTW_DARWIN_GLDEBUG=1` prints KHR_debug messages.

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

From `work/native-image`, with `MCV=26.3` on every step (it is also the default):

```
MCV=26.3 scripts/mkcp.sh
MCV=26.3 scripts/mkcp-fabric.sh
```

Mojang's 26.3 json names the plain LWJGL core (`lwjgl-3.4.3.jar`, which goes through FFM on Java 25). `mkcp.sh` swaps in `lwjgl-3.4.3-unsafe.jar` (JNI, the contract 26.2 shipped) from the jars `fetch-lwjgl341.sh` downloaded, and stops if it is missing.

`mkcp-fabric.sh` copies config, `options.txt` and `saves/world` from an older instance when one exists: `inst-1.21.11`, else `inst-26.2` (one-way DFU upgrade of the copy only). A clean clone has none, and it skips them.

The per-version sources for 26.3 are `scripts/t4-src/subst-26.3/`, `scripts/subst-fabric-src-26.3/` and `scripts/spikef-src-26.3/`. `inst.sh` exports `SUBSTV=26.3` when `subst-26.3` exists.

Build the bake agent (default package, `Premain-Class: BakeAgent`). `BakeAgent.java` uses ASM, which is on the Fabric classpath that `mkcp-fabric.sh` just wrote:

```
export JAVA_HOME="$ROOT/work/native-image/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home"
BA=$(mktemp -d)
mkdir -p bakeagent
"$JAVA_HOME/bin/javac" -cp "$(cat inst-26.3/fabric.cp)" -d "$BA" scripts/bake-src/BakeAgent.java
"$JAVA_HOME/bin/jar" cfm bakeagent/bakeagent.jar scripts/bake-src/MANIFEST.MF -C "$BA" .
```

There is no wrapper script. Those lines are the build.

The Knot run uses the stock SDL3 window and Apple OpenGL from the macOS natives jars, not klsdl. The bake loads a singleplayer world named `world`. If `mkcp-fabric.sh` did not copy one, make one first. Start Knot without the agent (the `3600` is `runfabric.sh`'s kill timer in seconds; it only has to outlast you):

```
MCV=26.3 scripts/runfabric.sh 3600 -Dvivecraft.openxr.mobile=true
```

Choose **Singleplayer**, **Create New World**, name it `world`, and wait until the terrain has loaded. Then **Save and Quit to Title** and quit the game. Check that `inst-26.3/fgamedir/saves/world/level.dat` exists: Minecraft names the folder after the world, and picks another name if `world` is taken. Use a throwaway world. The bake does not need your real one.

Now run Knot with the agent. This is the bake that produced the image. It needs a display. `-Dvivecraft.openxr.mobile=true` selects the mobile OpenGL ES path.

```
MCV=26.3 GAMEARGS="--quickPlaySingleplayer world" \
  scripts/runfabric.sh 600 \
  -javaagent:"$PWD/bakeagent/bakeagent.jar=$PWD/inst-26.3/bake" \
  -Dbake.fullAt=100 \
  -Dvivecraft.openxr.mobile=true
```

`-Dbake.fullAt=100` starts the full pass 100 seconds after JVM start. The world must be loaded by then; raise it on a slow Mac. The pass is done when the terminal prints `[BAKE] defined-at-runtime=…` and `inst-26.3/bake/summary.txt` exists. Then quit the game before the `600`-second timer kills it. The bake output does not depend on a clean Save and Quit: the 26.3 image's bake was ended with SIGTERM after the summary. Set the master volume to 0 in `options.txt` first if you want a silent run.

The 26.3 bake summary:

```
[BAKE] defined-at-runtime=15540 fullpass-transformed=35246 already=15494 skipped-mixin-classes=1054 null=0 failed=46 other-loader-classes=0
```

The full pass reports 46 failed classes (`failed=46`, names in `inst-26.3/bake/fullpass-failures.txt`). Under the pinned mod set and configs these are expected. They are original mixin classes that Knot will not load as plain classes. 26.2 had 49, audited from a clean build by zschwendi/avp-craft ([docs/KNOT-BAKE.md](https://github.com/zschwendi/avp-craft/blob/main/docs/KNOT-BAKE.md)): 16 interface mixin templates, 25 anonymous/local classes inside mixin packages, 5 Lithium mixin-package helper classes, 2 Indigo accessors (Indigo is off because Sodium is the renderer), and 1 Lithium pathing accessor. 45 of the 46 26.3 names are on that list. The differences:

| 26.2 name | 26.3 | Why |
| --- | --- | --- |
| `lithium/mixin/ai/pathing/PathfindingContextAccessor` | gone | Lithium 0.26.3 turned `mixin.ai.pathing` on by default. The accessor is now an applied mixin, counted under `skipped-mixin-classes`. |
| `lithium/mixin/world/explosions/entity_raycast/ServerExplosionMixin$1` | gone | The class is not in the Lithium 0.26.3 jar. |
| `lithium/mixin/world/raycast/BlockGetterMixin$1` | gone | Same. |
| `vivecraft/mixin/client_vr/renderer/ItemInHandRendererVRMixin$1` | `vivecraft/mixin/client_vr/renderer/FirstPersonHandsAndItemsRendererVRMixin$1` | Rename only: 26.3 renamed `ItemInHandRenderer`, Vivecraft 1.3.18 renamed the mixin. Same kind (anonymous class in a mixin package). |

No new failure category. A different list, a changed mod set, Indigo on, or a Lithium override in `config/lithium.properties` means a new bake that needs its own check. The count alone proves nothing.

Prune classes the bake copied unchanged from library jars. Keep classes a mixin actually changed.

```
python3 -I scripts/prune_overlay.py inst-26.3/bake/classes \
  inst-26.3/vanilla/lib/*.jar inst-26.3/fabric/lib/*.jar
```

Then delete these five LWJGL 3.4.3 multi-release classes if they are still in the overlay. They are the `META-INF/versions/11` variants Knot picked, not transformed classes, and would shadow the natives built in step 5: `org/lwjgl/VersionImpl`, `org/lwjgl/system/CheckIntrinsics`, `org/lwjgl/system/StackWalkUtil`, `org/lwjgl/system/Pointer`, `org/lwjgl/system/Pointer$Default`.

Leave `org/lwjgl/system/MemoryBackendUnsafe` and `MemoryBackendUnsafeLegacy25`. They exist only under `META-INF/versions/25/` of `lwjgl-3.4.3-unsafe.jar` (no base copy for `prune_overlay.py` to match) and are byte-identical to that entry, which is what a JDK 25 lookup selects. `org/lwjgl/openxr/*` and `org/lwjgl/openvr/*` stay too: they come from Vivecraft's nested jars, which are not on the library lists.

The bake that produced this image recorded `prune_overlay.py (31444 identical lib copies removed, 17 kept)` plus those five removed by hand in `inst-26.3/bake/PRUNE-NOTE.txt` (a build product, not committed). The 26.2 list of five was different (`MemoryUtilTunables` and `MultiReleaseTextDecoding` instead of the two `Pointer` classes).

`scripts/prune_overlay.py` is the deleter. `scripts/prune-overlay.sh` is the old one. Do not use it.

```
MCV=26.3 scripts/mkcp-baked.sh
MCV=26.3 scripts/m3a/build-launcher.sh
python3 -I scripts/m3a/pin-gl-options.py inst-26.3/fgamedir/options.txt
MCV=26.3 bash scripts/t4/build-t4.sh
```

`build-launcher.sh` calls `mkcp-baked.sh` again. `pin-gl-options.py` writes `preferredGraphicsBackend:"opengl"`. Run it after the Knot bake, because that run rewrites `options.txt`. `build-t4.sh` writes `inst-26.3/t4.cp` and `inst-26.3/t4-ni.cp`. On 26.3 it:

- replaces `lwjgl-sdl-3.4.3.jar` with an APFS clone of `klsdl/lwjgl-sdl-klsdl-3.4.3.jar` in `inst-26.3/klsdl/`, and drops every `*-natives-*` jar. 26.3 has no `lwjgl-glfw`, so klglfw is not on `t4.cp` (it prints a note).
- writes `inst-26.3/jarfix/` copies of any classpath jar with a `package-info` class older than class-file version 49, without those entries. JOML 1.10.9 ships three at version 46 (`org/joml`, `org/joml/experimental`, `org/joml/sampling`); HotSpot rejects `-` in a class name before 49 and native-image's package registration loads them (`ClassFormatError: Illegal class name org/joml/package-info`). They are annotation-only.

The committed reachability JSON for this classpath:

- `inst-26.3/config/t23-trim/reachability-metadata.json`
- `inst-26.3/config/t23-extra/reflect-config.json`
- `inst-26.3/config/t23-extra/resource-config.json`

No agent trace of 26.3 exists. `inst-26.3/config/t23-trim/SOURCE.txt` records where each file came from: `reachability-metadata.json` is the 26.2 trace with 11 types renamed to their 26.3 names (13 entries that name no 26.3 class stay; native-image ignores them), `resource-config.json` is 26.2's, and `reflect-config.json` is the output of `MCV=26.3 python3 -I scripts/t8/gen-reflect-extra.py`. Entries in `scripts/t8/reflect-extra-manual.json` can carry `sinceMcv` / `untilMcv` (inclusive) to bind them to the versions whose classes they name (26.3: authlib 10 moved `KeyData`; LWJGL 3.4.3 memory backends; Vulkan feature structs; the Better Biome Blend entrypoint). The generator drops those keys from its output.

Re-run `gen-reflect-extra.py` only when the classpath changes, and commit the new JSON with the image that used it. It writes `inst-26.3/out/cpindex.json` the first time. Check every substitution target against that index before a native-image run:

```
MCV=26.3 python3 -I scripts/m3a/check-subst.py inst-26.3/out/cpindex.json
```

It ends `0 problem(s)` on this tree.

Those three JSON files are in this tree. `*.cp` files are generated and contain absolute paths. `.gitignore` excludes them. Do not commit them.

Optional host check of the port's window path before the image: `MCV=26.3 KLSDL=1 scripts/runfabric.sh <seconds>` runs Knot on klsdl → libltw → ANGLE instead of stock SDL3. It needs a macOS ANGLE build (`ANGLE=<dir with libEGL.dylib and libGLESv2.dylib>`, default `$ROOT/vendor/out/Debug`), which this tree does not include, and the macOS `libltw` (`LTW=`). Its header lists the `KLSDL_*` knobs.

## 7. Native image

From `work/native-image`. `-O1` is the default when `CPFILE` points at an `inst-*` classpath. `-O2` does not link at this size.

`build-shared.sh` reads `NATIVE_IMAGE_HEAP` (default `28g`; a positive number followed by `m` or `g`) and `NATIVE_IMAGE_THREADS` (default unset, which leaves native-image's own `--parallelism`). It checks both, and checks that every `CONFIG_DIRS` entry is a directory, before it starts. It refuses to start when `$T8OUT/ni` or `$T8OUT/tmp` is not empty, so it never deletes an earlier attempt: remove them yourself, or pick a new `T8OUT` and `LOGTAG` (the log is `logs/t8-build-$LOGTAG.txt`). `relink-xros.sh` replaces `$T8OUT/<sdk>/libminecraft.dylib` only after clang reports a successful link.

```
MCV=26.3 \
  CPFILE="$PWD/inst-26.3/t4-ni.cp" \
  CONFIG_DIRS="$PWD/inst-26.3/config/t23-trim,$PWD/inst-26.3/config/t23-extra" \
  T8OUT="$PWD/out/t8-26.3" \
  scripts/t8/build-shared.sh

MCV=26.3 T8OUT="$PWD/out/t8-26.3" scripts/t8/relink-xros.sh xros
MCV=26.3 T8OUT="$PWD/out/t8-26.3" scripts/t8/relink-xros.sh xrsimulator
```

With the defaults on the 36 GB Mac the 26.3 image took 8 min 20 s, peak RSS 18.32 GB. `relink-xros.sh` runs `work/xros-probe/scripts/patch_buildver.py`. The device dylib is `out/t8-26.3/xros/libminecraft.dylib` (about 211 MB). shaderc and SPIRV-Cross are not linked into it: LWJGL loads `libshaderc` and `libspirv-cross` by file name through `-Dorg.lwjgl.librarypath`, and the app ships them beside the image.

`scripts/t8/host-run.sh` is an optional host smoke test of the dylib before you build the app: `MCV=26.3 T8OUT="$PWD/out/t8-26.3" scripts/t8/relink-xros.sh macosx`, then `MCV=26.3 T8OUT="$PWD/out/t8-26.3" scripts/t8/host-run.sh <seconds>`. It runs through `scripts/t4/run.sh`, with natives from `work/natives/build/lwjgl-3.4.3/macosx` (libklsdl, libshaderc, libspirv-cross and the rest) and, like the `KLSDL=1` check above, a macOS ANGLE that this tree does not include.

`build-shared.sh` and `relink-xros.sh` set their root from the script path (`work/native-image`).

## 8. The app

From `visionos/`:

```
MCV=26.3 KLEPTON_TARGET=vivecraft KL_ANGLE_PREBUILT=1 KL_NO_LAUNCH=1 \
  KLEPTON_TEAM="$KLEPTON_TEAM" \
  ./run.sh device
```

`run.sh`, `stage_native.sh`, `mknative.sh`, `bundle_payload.sh` and `targets.py` default `MCV` to 26.3. The launch template in `visionos/native/` is the 1.21.5 text. `stage_native.sh` rewrites version, asset index, game jar, and game-dir paths when `MCV` is not 1.21.5.

For `MCV=26.3`, `targets.py` takes the LWJGL natives from `work/natives/build/lwjgl-3.4.3/{sdk}/`, replaces `libklglfw` with `libklsdl`, and adds `libshaderc` and `libspirv-cross` to `liblwjgl_tinyfd` and `libfreetype`. `mknative.sh` names the script that builds any library it cannot find. Check the list without building:

```
python3 -I targets.py vivecraft | grep KLT_LIBS
```

`visionos/native/vivecraft-launch.txt` passes `-Dklsdl.libpath=@FW@/libklsdl.dylib`, and `-Dspike.shots=` (empty). Without that, the baked SpikeFabric wrapper saves a screenshot 25 seconds after launch (releases up to 1.0.2 did).

A device build defaults to Release (`KL_XCODE_CONFIG=Debug` selects Debug). `KL_BUNDLE_PAYLOAD` defaults on for a vivecraft device build and off for the simulator. Set `KL_BUNDLE_PAYLOAD=1` when the simulator app should carry its own payload. `KL_NO_INSTALL=1` writes a local IPA and does not look up or install on a paired headset.

`make xros` compiles `runtime/xr/kl_openvr.c`. When `vendor-moltenvk/out/include/vulkan/vulkan.h` is present (`make mvk` installs the headers and the MoltenVK frameworks), the OpenVR eye copy is included. Without that header the same file builds and the eye copy is omitted. Vivecraft's guest is OpenGL ES through ANGLE. `visionos/mkmvk.sh` embeds MoltenVK when the frameworks are there, and continues when they are not.

The first install that does not bundle the payload omits `KL_SKIP_STAGE`, so `stage_native.sh` fills the container (jars and assets, on the order of 0.6 GB). A payload build (`KL_BUNDLE_PAYLOAD=1`) runs `stage_native.sh --local` during `xcodebuild` and copies the scrubbed tree into the app. Later container installs can set `KL_SKIP_STAGE=1`.

A public device IPA:

```
MCV=26.3 KLEPTON_TARGET=vivecraft KL_ANGLE_PREBUILT=1 KL_BUNDLE_PAYLOAD=1 \
  KL_NO_INSTALL=1 KL_NO_LAUNCH=1 KL_SKIP_STAGE=1 \
  KLEPTON_TEAM="$KLEPTON_TEAM" \
  ./run.sh device
```

`visionos/scrub_paths.py` runs on the built app before the IPA is zipped, and before the simulator's ad-hoc sign. It rewrites the build machine's home prefix inside Mach-O copies when that prefix is 13 bytes, which keeps file offsets valid. The app is signed again after that rewrite. The private build inputs are not modified.

A simulator build is the same command without `device`, on the Apple Vision Pro simulator, visionOS 27.0. Set `KL_XR_STAGE_DROP=1.6` for that run. Set `KL_BUNDLE_PAYLOAD=1` if the simulator app should materialize from the bundle. Also set `KL_MSA_SECRETS=/nonexistent`: otherwise `stage_native.sh` copies the Microsoft refresh token from this Mac's Klepton userdata (`~/Library/Application Support/Klepton/userdata/…/msa/secrets.json`) into the simulator container when one is there.

Profiles are per Minecraft version (`inst-<v>/profiles` in the app container). On the first launch of a 26.3 build, an instance with no `profiles/` gets an APFS copy of the newest older instance's profiles (`inst-26.2/profiles`), so worlds, options, configs and servers carry over. It copies and never moves; the 26.2 data stays. Minecraft upgrades a copied world on first open, one way.

`run.sh` generates the Xcode project. Do not commit the generated project. It would capture the team id and the bundle id of the machine that wrote it.

The signed IPA is what a release publishes. Install it with SideStore. `KL_NO_LAUNCH=1` builds and does not launch.

## Building 26.2 (release 1.0.2)

Release 1.0.2 is Minecraft 26.2, Vivecraft 1.3.15 (`openxr` at `13eafae1` + `work/vivecraft-openxr-26.2/patches/` 0001–0006), LWJGL 3.4.1, libffi 3.5.2, FreeType 2.14.1, GLFW through klglfw, no shaderc/SPIRV-Cross. Its recipe is BUILDING.md at tag `1.0.2`:

```
git show 1.0.2:BUILDING.md
```

This tree still carries the 26.2 rows (`MCV=26.2` selects `inst-26.2`, LWJGL 3.4.1 and klglfw; `work/vivecraft-openxr-26.2/`, `mods-26.2.json` and `inst-26.2/config/` are unchanged), but a 26.2 build from it has not been run since the 26.3 port. Use the tag.

## Acknowledgements

[zschwendi/avp-craft](https://github.com/zschwendi/avp-craft) rebuilt this tree from a clean clone and found what was missing: the launcher sources, the OpenAL build, the world the bake needs, the BakeAgent classpath, and the unreproducible Vivecraft jar. The fixes in steps 2, 3, 5, 6 and 7 come from that work: the canonical jar and patch 0006, the header fetch, the RTKit switch, the optional instance copies, the native-image heap and thread settings, and the Knot bake audit.
