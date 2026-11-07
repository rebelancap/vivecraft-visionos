// 26.3 copy of subst-26.2/Target_T3.java. 26.3 replaced GLFW with SDL3 (klsdl stands in for it) and moved the
// renderer to com.mojang.renderpearl. T3 closed-world trim (native-image only). Each substitution removes a runtime
// dependency that cannot exist on visionOS:
//   Narrator.getNarrator                         -> Narrator.EMPTY  (NarratorMac = java-objc-bridge -> JNA -> AppKit)
//   MacosUtil.disableCloseWindowMenuItem()       -> no-op  (NEW in 26.3, called from every Window constructor: asks the
//                                                  objc bridge for NSApp.windowsMenu, which only real SDL3 builds;
//                                                  the 26.2 GLFW-era exitNativeFullscreen/clearResizableBit/loadIcon
//                                                  are gone, and the two SDL-hint setters need nothing: klsdl's
//                                                  SDL_SetHint is a no-op. Host JVM twin: scripts/klsdl-hostfix-src)
//   DebugEntrySystemSpecs.getCpuInfo()           -> no OSHI (26.2's GLX._getCpuInfo moved here; GLX is gone)
//   SystemReport lambda$new$6 / lambda$new$7     -> skipped (putHardware / putSoftware(new oshi.SystemInfo()))
//   renderpearl GlDebug.enableDebugCallback      -> null (no GL debug closure; was com.mojang.blaze3d.opengl.GlDebug)
//   NativeModuleLister.listModules               -> empty (Windows-only JNA)
// Dropped from the 26.2 set: TinyFileDialogs (26.3 no longer ships lwjgl-tinyfd; its message boxes are SDL's, which
// klsdl logs and answers with the default button).
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "com.mojang.text2speech.Narrator")
final class Target_Narrator {
  @Substitute public static com.mojang.text2speech.Narrator getNarrator() { return com.mojang.text2speech.Narrator.EMPTY; }
}

@TargetClass(className = "com.mojang.blaze3d.platform.MacosUtil")
final class Target_MacosUtil {
  @Substitute public static void disableCloseWindowMenuItem() { }
}

@TargetClass(className = "net.minecraft.client.gui.components.debug.DebugEntrySystemSpecs")
final class Target_DebugEntrySystemSpecs {
  @Substitute public static String getCpuInfo() {
    return Runtime.getRuntime().availableProcessors() + "x " + System.getProperty("os.arch") + " (native image, no OSHI)";
  }
}

@TargetClass(className = "net.minecraft.SystemReport")
final class Target_SystemReport {
  @Substitute private void lambda$new$6() { }   // putHardware(new oshi.SystemInfo())
  @Substitute private void lambda$new$7() { }   // putSoftware(new oshi.SystemInfo())
}

@TargetClass(className = "com.mojang.renderpearl.backend.opengl.GlDebug")
final class Target_GlDebug {
  @Substitute public static com.mojang.renderpearl.backend.opengl.GlDebug enableDebugCallback(int verbosity, boolean sync, java.util.Set<String> enabledExtensions) {
    return null;   // never register glDebugMessageCallback (no libffi closure)
  }
}

@TargetClass(className = "net.minecraft.util.NativeModuleLister")
final class Target_NativeModuleLister {
  @Substitute public static java.util.List<?> listModules() { return java.util.List.of(); }
}
