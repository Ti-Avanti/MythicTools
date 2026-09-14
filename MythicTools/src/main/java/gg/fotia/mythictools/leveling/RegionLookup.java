package gg.fotia.mythictools.leveling;

import java.util.List;
import java.util.Optional;

/** 隔离可选 WorldGuard 依赖，未安装时坐标点仍可独立工作。 */
public interface RegionLookup {
    boolean available();

    Optional<HorizontalShape> shape(String world, String regionId);

    List<String> ids(String world);

    void invalidate();

    static RegionLookup unavailable() {
        return new RegionLookup() {
            public boolean available() { return false; }
            public Optional<HorizontalShape> shape(String world, String regionId) { return Optional.empty(); }
            public List<String> ids(String world) { return List.of(); }
            public void invalidate() { }
        };
    }
}
