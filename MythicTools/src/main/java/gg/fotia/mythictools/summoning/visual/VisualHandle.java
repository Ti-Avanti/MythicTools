package gg.fotia.mythictools.summoning.visual;

import java.util.List;
import org.bukkit.entity.Player;

public interface VisualHandle extends AutoCloseable {
    VisualHandle NONE = new VisualHandle() {
        public void update(int age, List<Player> viewers) { }
        public void animate(String animation, boolean loop) { }
        public void close() { }
    };
    void update(int age, List<Player> viewers);
    void animate(String animation, boolean loop);
    @Override void close();
}
