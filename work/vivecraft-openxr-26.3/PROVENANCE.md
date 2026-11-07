# work/vivecraft-openxr-26.3 — provenance (2026-11-06)

Vivecraft OpenXR (mobile/GLES path) for Minecraft 26.3. Everything below is re-fetchable; the clone, the
Gradle cache and the build outputs are safe to delete. Only `PROVENANCE.md`, `REPORT.md` and `patches/` are
meant to be committed. The jar normalizer is the 26.2 one, used by relative path:
`../vivecraft-openxr-26.2/canonicalize-vivecraft-jar.py`.

| Item | Source | Pin | Local path | Re-fetch |
|---|---|---|---|---|
| Vivecraft upstream, branch `Multiloader-26.3` (the base) | https://github.com/Vivecraft/VivecraftMod | `15a9011fd5eb7816d44e168256814fc4b5c24c04` ("bump to 1.3.18", mod 1.3.18) | `VivecraftMod/` (branch `kl-mobile-26.3`) | see "Rebuild" |
| Vivecraft upstream, branch `openxr` (merged in, squashed into patch 0000) | same repo | `13eafae17a987521cc270e81cd774a4746ad9299` ("update to 26.2 add vulkan support split rendering and swapchain…") | same clone | `git fetch origin openxr` |
| Merge base of the two | same repo | `dee18e33c50582bb0b1d1399970b2e81bacf2710` | same clone | — |
| Oracle GraalVM for JDK 25 (macOS aarch64), JAVA_HOME for Gradle | see `../vivecraft-openxr-26.2/PROVENANCE.md` and UPSTREAM.md §GraalVM | `graalvm-jdk-25.0.4+7.1` | `../native-image/graalvm/` (a symlink to the private tree's toolchain dir on this machine; gitignored locally via `.git/info/exclude`) | as in 26.2 |
| Gradle 9.3.0 wrapper + Loom 1.15.5 + Minecraft 26.3 + Fabric deps | services.gradle.org / maven.fabricmc.net / Mojang / Maven Central | per `VivecraftMod/gradle/wrapper` + `gradle.properties`; Loom `1.15-SNAPSHOT` resolved to **1.15.5**; fabric-loader **0.19.5** | `gradle-home/` (GRADLE_USER_HOME pinned here, ~800 MB) | rerun the build |
| Fabric API | maven.fabricmc.net `net.fabricmc.fabric-api:fabric-api` | **0.165.2+26.3** (the port's mods-26.3 pin; upstream gradle.properties says 0.160.4+26.3) | `gradle-home/` | `-Pfabric_api_version=0.165.2+26.3` |
| LWJGL OpenXR (nested in the jar) | Maven Central `org.lwjgl:lwjgl-openxr` | **3.4.3** (patch 0000 bumps `lwjgl_openxr_version` from openxr's 3.4.1 to the LWJGL that Minecraft 26.3 ships) | `gradle-home/` | rerun the build |

## Rebuild

```
cd work/vivecraft-openxr-26.3
git clone https://github.com/Vivecraft/VivecraftMod VivecraftMod
git -C VivecraftMod checkout -B kl-mobile-26.3 15a9011f
git -C VivecraftMod am "$PWD/patches/"*.patch          # 0000..0006
git -C VivecraftMod rev-parse 'HEAD^{tree}'             # cb591f18073be44897964a6e646776f9fa81da22
cd VivecraftMod
printf 'project.build.excludeForge=true\nproject.build.excludeNeoforge=true\n' > local.properties
export JAVA_HOME="$ROOT/work/native-image/graalvm/graalvm-jdk-25.0.4+7.1/Contents/Home"
export GRADLE_USER_HOME="$ROOT/work/vivecraft-openxr-26.3/gradle-home"   # optional; keeps ~/.gradle clean
./gradlew --no-daemon :fabric:build -x test -Pfabric_api_version=0.165.2+26.3
mkdir -p build/canonical
rm -f build/canonical/vivecraft-26.3-1.3.18-fabric.jar
python3 -I ../../vivecraft-openxr-26.2/canonicalize-vivecraft-jar.py \
  build/libs/vivecraft-26.3-1.3.18-fabric.jar \
  build/canonical/vivecraft-26.3-1.3.18-fabric.jar
```

## Hashes

- Git trees: after 0000 `3c35acf95a067826a2c0fb2a2193ee06920cd0ee`; after 0000–0005
  `3699b3f2a362c30c9050a9780a7bde264a883104`; **after 0000–0006 `cb591f18073be44897964a6e646776f9fa81da22`**.
  Commit ids depend on who applies the patches and when; only the tree hashes are stable.
- Canonical jar `VivecraftMod/build/canonical/vivecraft-26.3-1.3.18-fabric.jar` (0000–0006): **19,769,483 bytes,
  sha256 `bc2e03e81a1f555efd7389c5e5ed5bb6eb85e235f0e7f131eb6ca5509d0320f3`**. Two independent builds (the working
  branch, and a fresh worktree at `15a9011f` + `git am patches/*.patch` with an empty `build/`) gave this same hash;
  their raw Loom jars differed (`fc8ac36e…` and `47968e7f…`, 11,228,105 bytes), as expected.
- Built 2026-11-06 with GraalVM 25.0.4+7.1 as JAVA_HOME, Gradle 9.3.0, Loom 1.15.5, fabric-loader 0.19.5,
  Fabric API 0.165.2+26.3.

Cleanup: `rm -rf VivecraftMod gradle-home *.log` loses nothing that `patches/` and this file cannot rebuild.
