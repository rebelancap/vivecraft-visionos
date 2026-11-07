package org.lwjgl.sdl;

import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

/** klsdl: GLES only (libltw over ANGLE / kl_egl). Vulkan is unavailable, so 26.3 stays on its OpenGL backend. */
public class SDLVulkan {
    protected SDLVulkan() { throw new UnsupportedOperationException(); }

    private static final String NO = "klsdl: Vulkan is not supported (GLES only)";

    public static boolean SDL_Vulkan_LoadLibrary(CharSequence path) { return KlSdl.fail(NO); }
    public static void SDL_Vulkan_UnloadLibrary() { }
    public static long SDL_Vulkan_GetVkGetInstanceProcAddr() { KlSdl.fail(NO); return 0L; }
    public static PointerBuffer SDL_Vulkan_GetInstanceExtensions() { KlSdl.fail(NO); return null; }
    public static boolean SDL_Vulkan_GetPresentationSupport(VkInstance instance, VkPhysicalDevice physicalDevice, int queueFamilyIndex) { return false; }
    public static boolean SDL_Vulkan_CreateSurface(long window, VkInstance instance, VkAllocationCallbacks allocator, LongBuffer surface) {
        return KlSdl.fail(NO);
    }
}
