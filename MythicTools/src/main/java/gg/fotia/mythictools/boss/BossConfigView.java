package gg.fotia.mythictools.boss;

import java.util.Collection;

/** Boss 运行时所需的只读配置视图。 */
public interface BossConfigView {
    Collection<BossConfig> bosses();

    BossConfig boss(String id);
}
