// 26.3 copy of subst-26.2/Target_Vulkan.java (renderpearl names: com.mojang.renderpearl.api.device.GpuBackend /
// BackendCreationException, backend.opengl.GlBackend, backend.vulkan.VulkanBackend). Vulkan kept out of the image's
// runtime paths (native-image only). Klepton is GL-only (klsdl: SDL_Vulkan_LoadLibrary fails; libltw = GLES over
// ANGLE), and the staged options pin preferredGraphicsBackend:"opengl" (scripts/m3a/pin-gl-options.py). Belt and braces
// for a reset options.txt:
//   PreferredGraphicsApi.getBackendsToTry()  -> [GlBackend] only
//   VulkanBackend.checkBackendAvailable()    -> null (crash report / telemetry detail only; no loader probe)
//   Vivecraft GraphicsHelper.getHelper()     -> OpenGLHelper (keeps VulkanHelper/VK10 code from being constructed)
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "net.minecraft.client.PreferredGraphicsApi")
final class Target_PreferredGraphicsApi {
  @Substitute public com.mojang.renderpearl.api.device.GpuBackend[] getBackendsToTry() {
    return new com.mojang.renderpearl.api.device.GpuBackend[] { new com.mojang.renderpearl.backend.opengl.GlBackend() };
  }
}

@TargetClass(className = "com.mojang.renderpearl.backend.vulkan.VulkanBackend")
final class Target_VulkanBackend {
  @Substitute public static com.mojang.renderpearl.api.device.BackendCreationException checkBackendAvailable() { return null; }
}

@TargetClass(className = "org.vivecraft.client_vr.render.helpers.graphics.GraphicsHelper")
final class Target_VivecraftGraphicsHelper {
  @Substitute private static org.vivecraft.client_vr.render.helpers.graphics.GraphicsHelper getHelper() {
    return new org.vivecraft.client_vr.render.helpers.graphics.opengl.OpenGLHelper();
  }
}
