# work/natives — download provenance

Downloads live in `downloads/` (never edited); build trees in `build/` (disposable, 55 MB); outputs in
`libffi/`, `openal/`, `openxr-shim/`. Scripts in `scripts/`. Everything regenerates from the scripts.

| What | URL | sha256 | Fetched | Used by |
|---|---|---|---|---|
| libffi 3.4.8 release tarball | https://github.com/libffi/libffi/releases/download/v3.4.8/libffi-3.4.8.tar.gz | `bc9842a18898bfacb0ed1252c4febcc7e78fa139fd27fdc7a3e30d9d9356119b` | 2026-10-12 | `scripts/build-libffi.sh` -> `libffi/{xros,xrsimulator}/lib/libffi.a` |
| OpenAL Soft 1.25.2 (git tag archive) | https://github.com/kcat/openal-soft/archive/refs/tags/1.25.2.tar.gz | `fb27e5839aa11f0e5b9d33756965291fad5d6909ab928ea1f796f4a1a6877894` | 2026-10-12 | `scripts/build-openal.sh` -> `openal/{xros,xrsimulator}/libopenal.dylib` |
| ANGLE `include/` at `25e721127e1c6c4c6fa0182b5c234b2c88971175` and Vulkan-Headers `include/` at `11d6898377797e07dbd543aaaa367e4465074597` (the Vulkan-Headers pin in that ANGLE commit's `DEPS`), gitiles tar archives | `https://chromium.googlesource.com/angle/angle/+archive/<commit>/include.tar.gz`, `https://chromium.googlesource.com/external/github.com/KhronosGroup/Vulkan-Headers/+archive/<commit>/include.tar.gz` | content hash over the sorted (path, size, sha256) inventory, in the script (gitiles archive bytes change per request) | 2026-11-04 | `scripts/fetch-graphics-headers.py` -> `vendor/include`, `vendor/third_party/vulkan-headers/src/include` |
| Simple Voice Chat natives (opus4j/rnnoise4j/speex4j/lame4j + codecs via FetchContent) | see `svc/PROVENANCE.md` (git clones at pinned commits) | n/a (git) | 2026-10-12 | `scripts/build-svc.sh` -> `svc/{xros,xrsimulator,macosx}/lib*.dylib` |

No other downloads besides the SVC clones (`downloads/svc`). `openxr-shim/` is original source; its jni.h comes from the local GraalVM JDK
(`work/native-image/graalvm/graalvm-jdk-21.0.12+7.1`).

Safe to delete: `build/` always; `downloads/` if the URLs above still resolve (re-fetch with curl, check sha256).

## LWJGL 3.4.1 for Minecraft 26.2 (M3 stage C natives, 2026-10-12; recorded BEFORE fetching)
Script: `scripts/build-lwjgl341-natives.sh` (macosx) / `scripts/build-lwjgl341-natives-xros.sh` (xros, xrsimulator) ->
`build/lwjgl-3.4.1/<platform>/`. Replaces the QuestCraftPlusPlus fork (f1ec2b7) **for 26.2 only**; 1.21.5/1.21.11 keep f1ec2b7.

| What | URL | sha / sha256 | Fetched | Used by |
|---|---|---|---|---|
| LWJGL 3.4.1 source, git tag `3.4.1` (annotated tag object c46b8379…, commit **b800ccffab14396fc529ddb6c931b7c5c5226763**) shallow clone to `downloads/lwjgl3-3.4.1/` | `git clone --depth 1 -b 3.4.1 https://github.com/LWJGL/lwjgl3 downloads/lwjgl3-3.4.1` | commit b800ccff | 2026-10-12 | native C sources (core/opengl/stb/jemalloc/tinyfd) |
| LWJGL 3.4.1 Maven Central jars (`downloads/lwjgl-3.4.1-maven/`): `lwjgl`, `lwjgl:unsafe`, `lwjgl-opengl`, `lwjgl-stb`, `lwjgl-openal`, `lwjgl-openxr`, `lwjgl-glfw`, `lwjgl-jemalloc`, `lwjgl-tinyfd`, and `natives-macos-arm64` of each for export comparison | `https://repo1.maven.org/maven2/org/lwjgl/<m>/3.4.1/<m>-3.4.1[-unsafe\|-natives-macos-arm64].jar` | see `downloads/lwjgl-3.4.1-maven/SHA256SUMS` | 2026-10-12 | Java API for javac/javap, export-set diff, the `:unsafe` core jar the bake puts on the classpath |
| libffi 3.5.2 (reused, already fetched: `work/native-image/dl/libffi/libffi-3.5.2.tar.gz`) | https://github.com/libffi/libffi/releases/download/v3.5.2/libffi-3.5.2.tar.gz | f3a3082a23b37c293a4fcd1053147b371f2ff91fa7ea1b2a52e335676bac82dc | (2026-10-12, T2) | static libffi linked into liblwjgl |
| FreeType 2.14.1 source (git tag `VER-2-14-1` = commit **526ec5c47b9ebccc4754c85ac0c0cdf7c85a5e9b**, the exact commit LWJGL 3.4.1's own `libfreetype.dylib.git` names), tag archive to `downloads/freetype-VER-2-14-1.tar.gz`. Needed because Minecraft 26.2 `NativeLibrariesBootstrap.loadLibraries` loads freetype eagerly and a failure is a fatal crash report | https://github.com/freetype/freetype/archive/refs/tags/VER-2-14-1.tar.gz | see `downloads/SHA256SUMS-freetype` | 2026-10-12 | `work/native-image/scripts/t2/build-freetype.sh` -> `build/lwjgl-3.4.1/{xros,xrsimulator}/libfreetype.dylib` |
| Mojang 26.2 version json (`downloads/mojang-26.2/26.2.json`, reading only: library list) | https://piston-meta.mojang.com/v1/packages/d367f3dfbc0b3e14688df2311359deb609b234e3/26.2.json | (json) | 2026-10-12 | which LWJGL modules 26.2 loads |

## LWJGL 3.4.3 for Minecraft 26.3 (2026-11-06)
Pins live in `scripts/lwjgl-ver.sh` (LWJGL_VER, else MCV: 26.2 -> 3.4.1, 26.3 -> 3.4.3). Same scripts as 3.4.1 (`fetch-lwjgl341.sh`,
`t2/build-lwjgl341-natives{,-xros}.sh`, `t2/build-freetype.sh`, `t2/build-klglfw341.sh`; the "341" is historical) ->
`build/lwjgl-3.4.3/<platform>/`. 3.4.1 rows above stay valid for 26.2.

| What | URL | sha / sha256 | Fetched | Used by |
|---|---|---|---|---|
| LWJGL 3.4.3 source, git tag `3.4.3` (annotated tag object e33a7d99…, commit **30fac9b95f99cda97312232be25ba55297bf9951**) shallow clone to `downloads/lwjgl3-3.4.3/` | `git clone --depth 1 -b 3.4.3 https://github.com/LWJGL/lwjgl3 downloads/lwjgl3-3.4.3` | commit 30fac9b9 | 2026-11-06 | native C sources (core/opengl/stb/tinyfd), FreeType symbol list |
| LWJGL 3.4.3 Maven Central jars (`downloads/lwjgl-3.4.3-maven/`): the 3.4.1 module list plus `lwjgl-sdl`, and `natives-macos-arm64` of each (none exists for openxr) | `https://repo1.maven.org/maven2/org/lwjgl/<m>/3.4.3/<m>-3.4.3[-unsafe\|-natives-macos-arm64].jar` | see `downloads/lwjgl-3.4.3-maven/SHA256SUMS` (`lwjgl-3.4.3.jar`, `-opengl`, `-sdl` sha1 equal to Mojang's 26.3 json) | 2026-11-06 | javac/javap, export-set diff, `:unsafe` core (mkcp.sh swaps it in for 26.3), host macOS jemalloc/openal/freetype/SDL3 dylibs |
| libffi **3.8.0** release tarball -> `work/native-image/dl/libffi/libffi-3.8.0.tar.gz`. LWJGL 3.4.3 bundles libffi 3.8.0's `ffi.h` and its `LibFFI.c` calls `ffi_call_plan_*` (new in 3.8.0), so 3.5.2 no longer links | https://github.com/libffi/libffi/releases/download/v3.8.0/libffi-3.8.0.tar.gz | 7da3e2d9a171eb0a038f592ecad3ff2bb2550f3496d87b3b29ad0cf4430c0db4 | 2026-11-06 | static libffi in `build/lwjgl-3.4.3/libffi-<platform>/` (configure still yields FFI_EXEC_TRAMPOLINE_TABLE 1) |
| FreeType **2.14.3** source (tag `VER-2-14-3` = commit **0a0221a1347e2f1e07c395263540026e9a0aa7c7**, the commit LWJGL 3.4.3's `libfreetype.dylib.git` names), tag archive to `downloads/freetype-VER-2-14-3.tar.gz` (sha256 stable across two fetches) | https://github.com/freetype/freetype/archive/refs/tags/VER-2-14-3.tar.gz | dc49de6b01a266eef4876a4dd34d9842c475d3e28ff2eff63bd2fb760ab56261 (also in `downloads/SHA256SUMS-freetype`) | 2026-11-06 | `build-freetype.sh` -> `build/lwjgl-3.4.3/{xros,xrsimulator}/libfreetype.dylib` |
| Mojang 26.3 version json: read from `work/native-image/inst-26.3/files/versions/26.3/26.3.json` (fetch-inst.py) | Mojang version manifest | (json) | 2026-11-06 | LWJGL module list: 26.3 drops lwjgl-glfw and lwjgl-tinyfd, adds lwjgl-sdl (SDL3, libSDL3.dylib.git = 147a8ee32dbf9ac02f3794964490687b6bbda1bc), and names the plain `lwjgl` core instead of `:unsafe` |

OpenAL Soft 1.25.2 and libffi 3.5.2 / FreeType 2.14.1 tarballs in this tree were copied from the private tree's `downloads/` and
sha256-checked against the rows above (no re-download).
