package gg.fotia.mythictools.spawning;

import org.bukkit.Location;

/** 固定位置定时生成 MythicMob 的配置。 */
public record SpawnPoint(
        String id,
        String mobId,
        String mobGroupId,
        Location location,
        long intervalSeconds,
        int minAmount,
        int maxAmount,
        int maxAlive,
        double level,
        boolean spawnOnDeath,
        long despawnSeconds,
        boolean enabled) {
    public SpawnPoint {
        location = location.clone();
    }

    @Override
    public Location location() {
        return location.clone();
    }

    public boolean usesMobGroup() {
        return mobGroupId != null;
    }
}
