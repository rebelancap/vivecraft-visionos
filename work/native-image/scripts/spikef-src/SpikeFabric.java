// Spike harness for the baked Fabric build (intermediary names): FPS log every second + Screenshot.grab
// at -Dspike.shots=<sec,...>; then hands off to BakedLauncher.
// class_310=Minecraft (method_1551 getInstance, method_47599 getFps, method_1522 getMainRenderTarget,
// field_1697 gameDirectory), class_318=Screenshot (method_1659 grab(File,RenderTarget,Consumer))
import java.util.function.Consumer;
import net.minecraft.class_310;
import net.minecraft.class_318;
public class SpikeFabric {
  public static void main(String[] args) throws Exception {
    String shots = System.getProperty("spike.shots", "25");
    Thread t = new Thread(() -> {
      long t0 = System.currentTimeMillis();
      java.util.Set<Long> todo = new java.util.TreeSet<>();
      for (String s : shots.split(",")) if (!s.isEmpty()) todo.add(Long.parseLong(s.trim()));
      while (true) {
        try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
        long sec = (System.currentTimeMillis() - t0) / 1000;
        class_310 mc;
        try { mc = class_310.method_1551(); } catch (Throwable e) { continue; }
        if (mc == null) continue;
        System.out.println("[SPIKE] t=" + sec + "s fps=" + mc.method_47599());
        java.util.Iterator<Long> it = todo.iterator();
        if (it.hasNext() && sec >= it.next()) {
          it.remove();
          final long s = sec;
          final class_310 m = mc;
          mc.execute(() -> class_318.method_1659(m.field_1697, m.method_1522(), (Consumer) (msg -> System.out.println("[SPIKE] screenshot@" + s + "s: " + msg))));
        }
      }
    }, "spike-monitor");
    t.setDaemon(true);
    t.start();
    net.fabricmc.loader.impl.launch.BakedLauncher.main(args);
  }
}
