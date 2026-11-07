// 26.3: identical to spikef-src-26.2/SpikeFabric.java (Minecraft.getInstance/getFps, Screenshot.grab(Minecraft, boolean) unchanged).
// 26.2 copy of scripts/spikef-src/SpikeFabric.java (Mojang names: 26.x is unobfuscated). FPS log every second +
// Screenshot.grab at -Dspike.shots=<sec,...>; then hands off to BakedLauncher.
// 26.2: Minecraft.getMainRenderTarget() is gone; Screenshot.grab(Minecraft, false) grabs the main target itself.
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
        Minecraft mc;
        try { mc = Minecraft.getInstance(); } catch (Throwable e) { continue; }
        if (mc == null) continue;
        System.out.println("[SPIKE] t=" + sec + "s fps=" + mc.getFps());
        java.util.Iterator<Long> it = todo.iterator();
        if (it.hasNext() && sec >= it.next()) {
          it.remove();
          final long s = sec;
          final Minecraft m = mc;
          mc.execute(() -> { Screenshot.grab(m, false); System.out.println("[SPIKE] screenshot@" + s + "s"); });
        }
      }
    }, "spike-monitor");
    t.setDaemon(true);
    t.start();
    net.fabricmc.loader.impl.launch.BakedLauncher.main(args);
  }
}
