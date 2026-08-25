package gg.fotia.mythictools.integration;

import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** MythicMobs 运行时能力端口，便于生命周期逻辑独立验证。 */
public interface MythicMobGateway {
    Optional<ActiveMob> spawn(String mobId, Location location, double level);

    Optional<String> mobId(Entity entity);

    Optional<ActiveMob> activeMob(UUID entityId);

    /** 同时确认 MythicMobs 索引和 Bukkit 实体仍然可用。 */
    boolean isLoadedAndActive(UUID entityId);

    boolean exists(String mobId);

    List<String> mobIds();
}
