package gg.fotia.mythictools.leveling;

import java.util.Optional;

/** 坐标点或区域引用，只在所属世界参与距离比较。 */
public record DistanceOrigin(String id, boolean enabled, String world,
                             HorizontalShape point, String regionId) {
    public Optional<HorizontalShape> shape(RegionLookup regions) {
        if (!enabled) {
            return Optional.empty();
        }
        return point == null ? regions.shape(world, regionId) : Optional.of(point);
    }
}
