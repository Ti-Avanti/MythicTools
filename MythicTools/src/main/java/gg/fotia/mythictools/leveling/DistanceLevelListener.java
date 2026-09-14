package gg.fotia.mythictools.leveling;

import io.lumine.mythic.bukkit.events.MythicMobPreSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** 在 MM 创建 ActiveMob 之前设置初始等级，移动和后续技能不触发重复加级。 */
public final class DistanceLevelListener implements Listener {
    private final LevelingRepository repository;

    public DistanceLevelListener(LevelingRepository repository) {
        this.repository = repository;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(MythicMobPreSpawnEvent event) {
        if (!Bukkit.isPrimaryThread()) {
            return;
        }
        repository.resolve(event.getMobType().getInternalName(), event.getLocation(), event.getMobLevel())
                .ifPresent(result -> event.setMobLevel(result.level()));
    }
}
