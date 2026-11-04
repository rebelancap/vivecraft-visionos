// native-image substitutions for Fabric Loader's code-source discovery. In a native image, class/resource
// URLs are "resource:" URLs and ProtectionDomain code sources are null, so Fabric can't find which jar a
// class came from (LoaderLibrary, LibClassifier, LOADER_CODE_SOURCE). Map an entry name back to the first
// on-disk jar/dir of -Dbaked.classpath that contains it (skipping the bake overlay + launcher dirs).
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.net.URL;
import java.nio.file.*;

@TargetClass(className = "net.fabricmc.loader.impl.util.UrlUtil")
final class Target_UrlUtil {
  @Substitute
  public static Path getCodeSource(URL url, String localPath) {
    if (url != null && !"resource".equals(url.getProtocol())) {
      try {
        if ("jar".equals(url.getProtocol())) {
          String s = url.toString(); int i = s.indexOf("!/");
          return Paths.get(new URL(s.substring(4, i)).toURI());
        }
        String p = url.toURI().getPath();
        if (p.endsWith(localPath)) return Paths.get(p.substring(0, p.length() - localPath.length()));
      } catch (Exception e) { throw new RuntimeException(e); }
    }
    return BakedCodeSource.find(localPath);
  }
  @Substitute
  public static Path getCodeSource(Class<?> cls) {
    return BakedCodeSource.find(cls.getName().replace('.', '/') + ".class");
  }
}
