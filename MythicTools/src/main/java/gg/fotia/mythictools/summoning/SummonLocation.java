package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.summoning.config.SummonDefinition;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class SummonLocation {
    private SummonLocation() { }
    static Location find(Player player, SummonDefinition definition) {
        Location origin = player.getLocation().clone();
        if (definition.locationMode().equals("target-block")) {
            var trace = player.rayTraceBlocks(definition.targetDistance(), FluidCollisionMode.NEVER);
            if (trace == null || trace.getHitBlock() == null) throw new IllegalArgumentException("summon.error.location");
            origin = trace.getHitBlock().getLocation().add(0.5, 1, 0.5);
            origin.setYaw(player.getLocation().getYaw());
        }
        validate(origin);
        return origin;
    }
    static void validate(Location location) {
        var world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                || location.getY() < world.getMinHeight() || location.getY() + 2 >= world.getMaxHeight()
                || !world.getWorldBorder().isInside(location) || !location.getBlock().isPassable()
                || !location.clone().add(0, 1, 0).getBlock().isPassable()
                || location.getBlock().isLiquid() || location.clone().add(0, -0.1, 0).getBlock().isPassable()) {
            throw new IllegalArgumentException("summon.error.location");
        }
    }
}
