package gg.fotia.mythictools.leveling;

import io.lumine.mythic.bukkit.events.MythicMobPreSpawnEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** 在 MM 对应版本的生成阶段设置初始等级，避免两个事件重复叠加。 */
public final class DistanceLevelListener implements Listener {
    private final LevelingRepository repository;
    private final boolean legacySpawnLevelEvent;

    public DistanceLevelListener(LevelingRepository repository, boolean legacySpawnLevelEvent) {
        this.repository = repository;
        this.legacySpawnLevelEvent = legacySpawnLevelEvent;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(MythicMobPreSpawnEvent event) {
        if (legacySpawnLevelEvent || !Bukkit.isPrimaryThread()) {
            return;
        }
        repository.resolve(event.getMobType().getInternalName(), event.getLocation(), event.getMobLevel())
                .ifPresent(result -> event.setMobLevel(result.level()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLegacySpawn(MythicMobSpawnEvent event) {
        if (!legacySpawnLevelEvent || !Bukkit.isPrimaryThread()) {
            return;
        }
        repository.resolve(event.getMobType().getInternalName(), event.getLocation(), event.getMobLevel())
                .ifPresent(result -> event.setMobLevel(result.level()));
    }
}
