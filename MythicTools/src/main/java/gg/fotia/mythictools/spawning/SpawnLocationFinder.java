package gg.fotia.mythictools.spawning;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** 在玩家附近已加载区块中寻找安全自然生成位置。 */
public final class SpawnLocationFinder {
    private final int attempts;

    public SpawnLocationFinder(int attempts) {
        this.attempts = attempts;
    }

    /** 按规则的距离、高度与光照限制寻找生成点。 */
    public Optional<Location> find(Player player, BiomeSpawnRule rule) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = rule.minDistance() == rule.maxDistance()
                    ? rule.minDistance()
                    : random.nextDouble(rule.minDistance(), rule.maxDistance());
            int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);
            double centerX = x + 0.5;
            double centerZ = z + 0.5;
            double horizontalDistanceSquared = Math.pow(centerX - origin.getX(), 2)
                    + Math.pow(centerZ - origin.getZ(), 2);
            if (horizontalDistanceSquared < Math.pow(rule.minDistance(), 2)
                    || horizontalDistanceSquared > Math.pow(rule.maxDistance(), 2)) {
                continue;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int y = groundY + 1;
            if (y < rule.minY() || y > rule.maxY()) {
                continue;
            }
            Location location = new Location(world, centerX, y, centerZ);
            if (!world.getWorldBorder().isInside(location)) {
                continue;
            }
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block floor = world.getBlockAt(x, y - 1, z);
            int light = feet.getLightLevel();
            if (floor.getType().isSolid() && feet.isPassable() && head.isPassable()
                    && light >= rule.minLight() && light <= rule.maxLight()) {
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }
}
