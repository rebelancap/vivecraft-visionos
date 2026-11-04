import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
public final class BakedCodeSource {
  private static List<Path> cp;
  private static final Map<Path, ZipFile> zips = new HashMap<>();
  public static synchronized Path find(String entry) {
    if (cp == null) {
      cp = new ArrayList<>();
      for (String e : System.getProperty("baked.classpath", "").split(java.io.File.pathSeparator)) {
        if (e.isEmpty() || e.endsWith("/bake/classes") || e.endsWith("/baked/launcher")) continue;
        cp.add(Paths.get(e));
      }
    }
    for (Path p : cp) {
      try {
        if (Files.isDirectory(p)) { if (Files.exists(p.resolve(entry))) return p; continue; }
        ZipFile z = zips.get(p);
        if (z == null) { z = new ZipFile(p.toFile()); zips.put(p, z); }
        if (z.getEntry(entry) != null) return p;
      } catch (Exception ignored) { }
    }
    return null;
  }
}
