Minecraft: Java Edition 26.3 + Vivecraft VR, running natively on Apple Vision Pro, built on [Klepton](https://github.com/shinyquagsire23/Klepton) by shinyquagsire23.

## What's new for players
- **Minecraft 26.3** with **Vivecraft 1.3.18**. The mods move to their 26.3 builds: Sodium 0.9.2, Lithium 0.26.3, Simple Voice Chat 2.6.27, Fabric API 0.165.2, and the rest (see the README).
- **Your worlds come with you.** The first launch copies each account's 26.2 worlds, settings and servers into 26.3. The 26.2 copies are left untouched.
- **Lower HUD.** The head-locked HUD now sits 8° below eye level. Profiles that got the 1.0.2.x dev builds' −15° are moved to −8°; a pitch you set yourself is left alone (Vivecraft Settings → VR Settings → HUD to change it).
- **PS VR2 Sense controllers work in windowed mode.** The pair acts as one gamepad: sticks, Cross/Circle/Triangle/Square, triggers, grips (L1/R1), stick clicks and Options.
- **No more stray screenshot.** Earlier builds saved a screenshot about 25 seconds after every launch. That is gone.

## Known notes
- **World upgrade is one-way.** Minecraft upgrades a 26.2 world when 26.3 first opens it, and an upgraded world cannot go back to 26.2. The app upgrades the copy and keeps the 26.2 original, but back up any world you care about anyway, as Minecraft itself advises.
- **Lithium's pathfinding optimizations are on.** Lithium 0.26.3 enables them by default; 26.2 had them off. If mobs path oddly, report it.
- **Sodium 0.9.2** is still the renderer, under the Polyform Shield license (text in the app and in the repo).
- **Windowed mode** has had less testing on 26.3 than Immersive VR. If it runs slowly, lower the render distance.

## What's new for builders
The source tree now builds Minecraft 26.3 by default (`MCV=26.3`). 26.3 replaced GLFW with SDL3 and compiles shaders through SPIR-V, so the build adds klsdl (an SDL3 stand-in on Klepton's GLFW layer) and visionOS builds of shaderc and SPIRV-Cross, plus LWJGL 3.4.3, libffi 3.8.0 and FreeType 2.14.3. Vivecraft is upstream `Multiloader-26.3` plus the `openxr` branch merged as patch 0000, then our patches 0001–0006. This is the first release whose native image includes the HUD patch (0006). See BUILDING.md and UPSTREAM.md. The 26.2 recipe stays at tag `1.0.2`.

## Install
- **SideStore:** add the All ports source `https://raw.githubusercontent.com/rebelancap/all-ports/main/apps-visionos.json` and install **Vivecraft**.
- **Direct:** sideload `vivecraft-visionos-1.0.3.ipa` below.

The IPA is self-contained (game data included); no Mac is needed after install.

## You must own Minecraft: Java Edition
The first launch on a device requires signing in once with a Microsoft account that owns Minecraft: Java Edition. After that the device stays unlocked, and the Offline profile and other accounts work too. The IPA contains the Minecraft client compiled ahead of time (visionOS has no JIT), which is why this gate exists. Not affiliated with Mojang or Microsoft.

## Controls
VR controllers (PS VR2 Sense) are recommended; a hand-tracking gesture map is in the README. Windowed mode uses a gamepad or the Sense pair.

## Credits
Klepton, Vivecraft, QuestCraft (OpenXR fork and bindings), Fabric, Sodium, Lithium and the other bundled mods, ANGLE, LWJGL, shaderc and SPIRV-Cross, GraalVM. Licenses ship inside the app; see THIRD-PARTY.md.

`vivecraft-visionos-1.0.3.ipa`: 639,148,375 bytes, sha256 `ece6db2e0e3cf0c04e542730a3af2279e2ad4bae463967c06c9f081cf40c5f9b`
