// native-image substitution (intermediary names): MinecraftServer.loadStatusIcon() = method_49384 uses
// javax.imageio -> AWT, which cannot load in a macOS native image. Return no icon.
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
@TargetClass(className = "net.minecraft.server.MinecraftServer")
final class Target_MinecraftServer_Int {
  @Substitute
  private java.util.Optional<?> method_49384() { return java.util.Optional.empty(); }
}
