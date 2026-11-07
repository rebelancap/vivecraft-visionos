# Vivecraft OpenXR (mobile GLES) for Minecraft 26.3 (2026-11-06)

Result: **the jar builds on 26.3.** `vivecraft-26.3-1.3.18-fabric.jar`, canonical form 19,769,483 bytes,
sha256 `bc2e03e81a1f555efd7389c5e5ed5bb6eb85e235f0e7f131eb6ca5509d0320f3`, reproduced from `patches/` in a fresh
worktree. Pins, commands and hashes are in `PROVENANCE.md`.

Host-only, compile-time work. Nothing was launched (no dev client, no simulator, no device). Everything that can only
be proven at runtime is in §7.

## 1. Why a new base and a patch 0000

Upstream has not moved the `openxr` branch past 26.2 (`13eafae1`). The 26.3 work lives on `Multiloader-26.3`
(`15a9011f`, mod 1.3.18). The new base is `Multiloader-26.3` @ `15a9011f`, and **patch 0000** carries the OpenXR
work: `git merge 13eafae1` into `15a9011f`, conflicts resolved, openxr-only code adapted to 26.3, squashed into one
commit. It is upstream LGPLv3 code by the Vivecraft authors; the commit message says so and lists every adaptation.
The recipe stays "check out an upstream commit, `git am` our patches".

The merge base of openxr and Multiloader-26.3 is `dee18e33`. Since then openxr changed 82 files (+5733 −1251), and
Multiloader-26.3 changed far more (`f7952912` "update to 26.3" alone touches 127 files). 37 files changed on both
sides; git's 3-way merge left 10 files in conflict (§3) and merged the rest.

## 2. Patches (`patches/`, apply in order on `15a9011f`)

| Patch | Status on 26.3 |
|---|---|
| `0000-openxr-on-26.3.patch` | New. The squashed openxr port (§3, §4). |
| `0001-openxr-mobile-OpenGL-ES-path-port-of-QuestCraft-Devi.patch` | One import conflict in `DeviceCompat.java` (0000's SDL imports next to 0001's `JNI` import; both kept). The body applied unchanged. |
| `0002-openxr-guard-input-lookups-on-profiles-without-a-bin.patch` | Clean. |
| `0003-touch-jump-qc-default-ptt-left-stick.patch` | Clean. |
| `0004-touch-map-qc-layout.patch` | Clean. |
| `0005-openxr-direct-post-VR-post-pass-renders-straight-int.patch` | Two conflicts in `OpenXRStereoRenderer.java` (imports, and the gamma-correction block inside `endFrame` that 0005 re-indents): took 0005's structure, then applied 0000's `bindTexture`→`setUniform` to it. All `com.mojang.blaze3d.*` names in 0005's new files were renamed to `com.mojang.renderpearl.*`. **`FrameBufferCacheVRMixin` was retargeted** (§5). |
| `0006-lower-head-locked-in-world-HUD.patch` | Clean (GuiHandler and VRSettings changed in 26.3, but not near 0006's hunks). |

Subjects and authors of 0001–0006 are unchanged; the file names are the 26.2 ones.

## 3. Merge conflicts (openxr into 26.3) and how each was resolved

| File | Conflict | Resolution |
|---|---|---|
| `render/helpers/graphics/OpenGLHelper.java`, `VulkanHelper.java` | modify/delete: openxr moved them to `graphics/opengl/` and `graphics/vulkan/` (and grew them); 26.3 edited the old files (imports, and `RenderSystem.getDevice()` is now a `FrontendGpuDevice` whose `.backend` is the GL/Vulkan device). | Deleted the old files; ported 26.3's change into the moved `opengl/OpenGLHelper.getGlDevice()` and `vulkan/VulkanHelper.getVulkanDevice()` (`instanceof FrontendGpuDevice gpuDevice && gpuDevice.backend instanceof …`). |
| `render/helpers/graphics/GraphicsHelper.java` | imports | Union: 26.3's renderpearl/FrontendGpuDevice imports + openxr's new `opengl.OpenGLHelper`, `vulkan.VulkanHelper`, `RenderTarget`, `Nullable`, `Set`. 26.3's `getHelper()` body kept. |
| `provider/VRRenderer.java` | imports | openxr's `GpuFormat`/`PrimitiveTopology` imports under their 26.3 names; duplicate removed. |
| `provider/control/InputAction.java` (was `openvr_lwjgl/VRInputAction.java`) | rename + imports | git followed the rename and merged 26.3's body changes (debug-modifier press/unpress, `SDLScancode.SDL_SCANCODE_UNKNOWN`, `InputConstants.Type.KEYBOARD`). Imports: kept `SDLScancode`, dropped the now-unused `Vector3f*`, **re-added `MCVR`** (the class moved out of `provider`, and 26.3's new debug-modifier code calls `MCVR.get()`). |
| `provider/openvr_lwjgl/MCOpenVR.java` | 26.3 edited `processInputAction` (GLFW→SDL left mouse button); openxr moved that code into `MCVR`. | Took openxr's deletion; applied the same `SDLMouse.SDL_BUTTON_LEFT` change in `MCVR.processInputAction` (the only difference in the deleted block). 26.3's other MCOpenVR changes (`getStringDeviceProperty`, LWJGL 3.4.3 check, controller-transform guard) merged automatically. |
| `gui/keyboard/KeyboardKeys.java` | imports | 26.3's (`org.jetbrains…Nullable`, `SDLKeycode`, `SDLScancode`); dropped openxr's `javax.annotation.Nullable` swap. |
| `mixin/client_vr/blaze3d/vulkan/VulkanBackendVRMixin.java` | imports | Union. See also §4 (injection point rewritten). |
| `mixin/client_vr/blaze3d/vulkan/VulkanInstanceVRMixin.java` | openxr added the `vivecraft$apiVersion` field next to 26.3's renamed `VulkanDebug.create` target | Took openxr's side with the 26.3 target string. |
| `vivecraft.accesswidener` | GL/Vulkan section | 26.3's `renderpearl` `GlDevice`/`FrontendGpuDevice.backend` entries under openxr's comment; openxr's Vulkan entry renamed (§4). |

Auto-merged and checked by compiling: `build.gradle`, `fabric/build.gradle`, `common/build.gradle`,
`gradle.properties`, `vivecraft.mixins.json` (adds `VulkanQueueVRMixin`), `MCVR`, `VRSettings`,
`MinecraftVRMixin`, `VRPassHelper`, `ShaderHelper`, `VREffectsHelper`, `VRShaders`, `NullVR*`, `OpenVRStereoRenderer`,
`IrisHelper`, the lang files, and the rest of the 37.

## 4. Compile fixes / 26.3 adaptations inside 0000

1. **`com.mojang.blaze3d.*` → `com.mojang.renderpearl.*`.** 26.3 moved most of blaze3d (opengl, vulkan, systems,
   textures, buffers, GpuFormat, PrimitiveTopology, …) into `com.mojang.renderpearl.{api,backend,frontend}`. A class
   map was built by matching the 26.2 and 26.3 client jars by simple name (108 moved classes, all unambiguous; 10
   classes are gone, none used by openxr). It was applied to every source file, mixin target string and the access
   widener. Only openxr-only code still had old names: `OpenXRStereoRenderer`, `VRRenderer`, `GraphicsHelper`,
   `graphics/{opengl,vulkan}/*`, `VulkanBackendVRMixin`, `VulkanInstanceVRMixin`, `VulkanQueueVRMixin`.
2. `VulkanCommandEncoder.textureInitCommandBuffer()` is gone; 26.3 has the package-private
   `objectInitCommandBuffer()`. It returns a separate init command buffer that is allocated on first use, where 26.2's
   method returned the current command buffer. `VulkanRawTexture` uses it, and the access-widener entry is updated.
3. `RenderPass.bindTexture(name, view, sampler)` → `setUniform(name, view, sampler)` (OpenXR gamma pass).
4. **`VulkanBackendVRMixin.vivecraft$preferVRGpuCapture`** wrapped `isDeviceSuitable(VkPhysicalDevice)Z`, which
   26.3's `findPhysicalDevice` no longer calls. It now wraps `checkDeviceSuitability(VkPhysicalDevice, Set, Set)`
   (returns `BackendCreationException`, null = suitable). Same intent. Found by the mixin-target check, not by javac.
5. **`DeviceCompat` desktop OpenGL binding**: 26.3 replaced GLFW with SDL3 and LWJGL's `glfw` module is no longer on
   the classpath. Win32: `HWND` from `SDL_GetWindowProperties` + `SDL_PROP_WINDOW_WIN32_HWND_POINTER`, `HGLRC` from
   `wglGetCurrentContext`. X11 (`SDL_GetCurrentVideoDriver() == "x11"`): display from
   `SDL_PROP_WINDOW_X11_DISPLAY_POINTER`, drawable and context from `glXGetCurrentDrawable/Context`. This is not our
   path (the mobile path builds its binding from VLoader's EGL handles), but it has to compile.
6. `lwjgl_openxr_version` 3.4.1 → **3.4.3**, the LWJGL Minecraft 26.3 ships (openxr had matched 26.2's 3.4.1;
   `MCOpenVR`'s own check is now 3.4.3). 3.4.3 still has no `KHROpenGLESEnable`/Android structs (only
   `KHRLoaderInit`), so 0001's hand-written structs are still needed.

After 0000–0006, `:fabric:build` compiles with no errors. The only notable warning in the build log is Loom's usual
"Failed to parse fabric.mod.json" (templated file).

## 5. Mixin-target checks (static, against the 26.3 client jar)

Mixin targets are only validated when the class loads, so a static javap-based check was run from a scratch script. For
each mixin class it confirms that the target class exists, that every `method =` exists, and that every
INVOKE/FIELD/NEW `target =` is referenced in the target class's bytecode. Classpath:
`minecraft-merged-deobf-26.3.jar` plus the LWJGL, Fabric and Mixin jars from `gradle-home`.

Every mixin added or changed by openxr or by 0001–0006 passes:

| Mixin | Target (26.3) | Checked |
|---|---|---|
| `MinecraftVRMixin` | `net.minecraft.client.Minecraft` | 15 methods + all INVOKE targets (openxr and 0005 only change handler bodies) |
| `VulkanBackendVRMixin` | `renderpearl.backend.vulkan.VulkanBackend` | `createDevice(GpuDebugOptions)`, `findPhysicalDevice` → `checkDeviceSuitability(...)` (**rewritten**, §4.4), `new VulkanPhysicalDevice(VkPhysicalDevice)` (still in `findPhysicalDevice`, offset 317) |
| `VulkanInstanceVRMixin` | `…vulkan.VulkanInstance` | `<init>` → `VulkanDebug.create(IZSet;Set)`, `VkApplicationInfo.apiVersion(I)` |
| `VulkanQueueVRMixin` (new in openxr) | `…vulkan.VulkanQueue` | `<init>(VulkanDevice;II)V` |
| `VulkanDeviceVRMixin`, `VulkanPhysicalDeviceVRMixin`, `VulkanDebugMixin` | `…vulkan.*` | pass |
| `FishingHookVRMixin`, optifine `GlCommandEncoderVRMixin` | unchanged targets | pass |
| **`FrameBufferCacheVRMixin` (0005)** | `…opengl.FrameBufferCache` | **26.3 added `getFbo(dsa, colors, depth, int mipLevel)`; the old 3-arg `getFbo` now just calls it with 0, and the clear paths call the 4-arg form per mip level.** The 26.2 handler targeted `"getFbo"` by name with the 3-arg handler signature, so it would also select the 4-arg overload and fail at load with an invalid handler signature. It now targets the 4-arg descriptor explicitly, takes `int mipLevel`, and substitutes the layer FBO only when `mipLevel == 0` (the swapchain views have one level). Every render pass still goes through it. |

The other `GlTexture`/`GlTextureView` details 0005 depends on are the same in 26.3:
- `SwapchainLayerTexture` subclasses `GlTexture`. Its constructor is now `protected`, so the 26.2 access-widener entry is gone and not needed.
- `GlTexture.close()`/`removeViews()` and `GlTextureView.close()` have the same bytecode shape as in 26.2, so the "close never deletes the runtime's GL name" logic holds.
- The frontend (`FrontendCommandEncoder`) passes texture views through, and `GlCommandEncoder` casts them to `GlTexture`/`GlTextureView`, which ours are.

The same scan over all 151 upstream mixins flags only upstream files that are byte-identical to `15a9011f`. Those are
either wildcard or record-constructor selectors the script does not model (`distanceToSqr*`,
`ModelFeatureRenderer$Submit.<init>`, …) or third-party mod-compat targets that are not on the classpath (Iris,
Sodium, OptiFine, etc.).

## 6. Static checks from the 26.2 directory

- `python3 -I ../vivecraft-openxr-26.2/check-touch-map.py <jar> <javap>`: the Touch-map half passes
  (`[touch-map] OK: 31 bindings == QuestCraft Touch layout (30) + key.push_to_talk -> /user/hand/left/input/thumbstick/click (guarded)`).
  The direct-post half fails only because it hardcodes `com.mojang.blaze3d.textures.GpuTextureView` in two method
  signatures. With those two names replaced by `com.mojang.renderpearl.api.textures.GpuTextureView` (scratch copy,
  `sed -e 's#com\.mojang\.blaze3d\.textures\.GpuTextureView#com.mojang.renderpearl.api.textures.GpuTextureView#g' -e 's#com/mojang/blaze3d/textures/GpuTextureView#com/mojang/renderpearl/api/textures/GpuTextureView#g'`),
  it passes: `[direct-post] OK: …`. The script needs a 26.3 variant or a version switch before it can be the committed
  check for 26.3.
- `tests/hud_pitch_source_contract.py VivecraftMod <tree before 0006>`: `PASS 113 source contract checks`.
- `tests/HudPitchTransformTest.java` compiled against the 26.3 build classes, client jar and Gradle-cache libraries:
  `PASS 106053 … assertions using real JOML and Vec3`.

## 7. RISK — only a bake and a run can prove these

1. **SDL3 instead of GLFW (biggest).** Minecraft 26.3 creates its window, GL context and input through SDL3
   (`org.lwjgl.sdl`). Vivecraft 26.3's input code now uses SDL scancodes, keycodes and mouse buttons
   (`InputSimulator`, `KeyboardKeys`, `MCVR.processInputAction`, `InputAction.pressKey`). Klepton's klglfw
   windowing/input shim is GLFW-shaped; whatever replaces it on 26.3 must deliver the SDL events these paths produce
   and consume. That is outside this jar, but the jar depends on it.
2. **LWJGL 3.4.3 everywhere.** The nested `lwjgl-openxr` is now 3.4.3, so the native-image reachability/JNI config and
   any substitutions keyed on LWJGL OpenXR classes from the 3.4.1 bake need a re-check.
3. **Direct post through the new `getFbo` overload** (`FrameBufferCacheVRMixin`, §5). Verify the post pass still lands
   in the swapchain layer: run `-Dvivecraft.klepton.directpost.selftest=true` once in flat mode, then check the first
   VR frame. kl_glfb's `KL_XR_ARRAY_DIRECT` redirect must still fire. Fallback: `-Dvivecraft.klepton.directpost=false`.
4. **Auto-merged renderer core.** `VRRenderer`, `VRPassHelper`, `ShaderHelper`, `OpenXRStereoRenderer` and
   `VREffectsHelper` combine openxr's split rendering with 26.3's pipeline changes: `RenderSystem.getCompiledPipeline`,
   window/framebuffer size via `vivecraft$getActualFramebufferSize`, the mirror target now has depth, and the Iris and
   VRAM-free fixes. It compiles, but eye rendering, the gamma pass (off with an sRGB swapchain), resize and VR
   teardown are unverified.
5. **`setupRenderConfiguration(true)` and per-tick `vr.handleEvents()`** (openxr's lifecycle changes) now run against
   26.3's `MinecraftVRMixin`; verify VR init, hotswitch and runtime-closed shutdown.
6. **Input actions.** openxr's split `InputAction`/`XRInputAction` now carries 26.3's debug-modifier logic: keys in the
   debug category also press F3. Verify the Touch map (0003/0004) and SVC push-to-talk on device. The per-set action
   count is unchanged from the 26.2 analysis, so `KLXR_ACTION_MAX` ≥1024 still applies.
7. **Desktop-only paths changed without a test**: `DeviceCompat` Win32/X11 GL binding via SDL (§4.5), the Vulkan
   GPU-preference mixin (§4.4) and `objectInitCommandBuffer` (§4.2). They are not used on the headset (GLES mobile
   path, no Vulkan), but they are in the jar.
8. **Fabric API 0.165.2+26.3** is newer than upstream's 0.160.4 pin. The build resolves and links against it, but only
   a load test shows whether it works with the rest of the 26.3 mod set.
9. **Mixins of unchanged upstream files** were not re-validated beyond the scan in §5; upstream 1.3.18 is a released
   build, so they are assumed good.

## 8. Toolchain

GraalVM 25.0.4+7.1 as JAVA_HOME, Gradle 9.3.0 (wrapper), `fabric_loom=1.15-SNAPSHOT` resolved to **Loom 1.15.5**
(no `-Pfabric_loom` override needed), fabric-loader 0.19.5, Fabric API 0.165.2+26.3, Minecraft 26.3. The build takes
about 1 min 45 s cold (Minecraft download + decompile into `gradle-home/`) and about 10 s warm.
