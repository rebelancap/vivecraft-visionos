package kl.hostfix;

import com.mojang.blaze3d.platform.MacosUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The JVM twin of the native image's Target_T3 MacosUtil substitution (scripts/t4-src/subst-26.3/Target_T3.java).
 * 26.3's Window constructor calls MacosUtil.disableCloseWindowMenuItem(), which asks AppKit for NSApp.windowsMenu --
 * a menu real SDL3 creates on macOS and klsdl (headless, no AppKit) does not, so the call dies on a null proxy.
 */
@Mixin(value = MacosUtil.class, remap = false)
abstract class MacosUtilMixin {
    @Inject(method = "disableCloseWindowMenuItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void klsdl$noAppKitMenu(CallbackInfo ci) { ci.cancel(); }
}
