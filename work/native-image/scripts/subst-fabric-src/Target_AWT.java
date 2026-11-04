// native-image substitutions: mods (Vivecraft VRSettings) touch java.awt.Color etc. as plain data classes;
// their static init loads libawt, which a macOS native image cannot load (needs libjvm/libjava). Force headless
// and make Toolkit.loadLibraries a no-op. Pure-Java AWT data classes (Color, geom) then work.
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
@TargetClass(className = "java.awt.Toolkit")
final class Target_java_awt_Toolkit {
  @Substitute static void loadLibraries() { }
  @Substitute private static void initIDs() { }
}
@TargetClass(className = "java.awt.GraphicsEnvironment")
final class Target_java_awt_GraphicsEnvironment {
  @Substitute public static boolean isHeadless() { return true; }
  @Substitute static String getHeadlessMessage() { return "headless (native image)"; }
}
