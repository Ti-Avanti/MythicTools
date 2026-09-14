package gg.fotia.mythictools.leveling;

import gg.fotia.mythictools.config.ConfigDiagnostic;
import gg.fotia.mythictools.config.ConfigProblemCode;
import gg.fotia.mythictools.config.ConfigSeverity;
import gg.fotia.mythictools.config.ConfigValues;
import gg.fotia.mythictools.config.RepositoryLoadContext;
import gg.fotia.mythictools.config.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;

/** 加载可复用坐标点和 WorldGuard 区域引用。 */
final class LevelOriginRepository {
    private final RepositoryLoadContext context;
    private final RegionLookup regions;

    LevelOriginRepository(RepositoryLoadContext context, RegionLookup regions) {
        this.context = context;
        this.regions = regions;
    }

    Map<String, DistanceOrigin> load(boolean region, List<ConfigDiagnostic> diagnostics) {
        Map<String, DistanceOrigin> origins = new LinkedHashMap<>();
        for (File file : LevelingConfigFiles.list(context.dataFolder(), region ? "regions" : "points")) {
            try {
                var yaml = YamlFiles.load(file);
                String id = LevelingConfigFiles.id(file);
                boolean enabled = LevelingConfigFiles.enabled(yaml, true);
                String world = LevelingConfigFiles.required(yaml, "location.world");
                String regionId = region ? yaml.getString("region-id", "").trim() : null;
                HorizontalShape point = region ? null : new HorizontalShape.Point(
                        ConfigValues.doubleOrDefault(yaml, "location.x", 0.0, -30_000_000.0, 30_000_000.0),
                        ConfigValues.doubleOrDefault(yaml, "location.z", 0.0, -30_000_000.0, 30_000_000.0));
                if (enabled && context.worldResolver().apply(world) == null) {
                    diagnostics.add(LevelingConfigFiles.problem(file, ConfigSeverity.ERROR,
                            ConfigProblemCode.WORLD_UNAVAILABLE, "世界未加载: " + world, null));
                    continue;
                }
                if (enabled && region) {
                    if (regionId.isEmpty()) {
                        throw new IllegalArgumentException("region-id 不能为空");
                    }
                    if (!regions.available()) {
                        diagnostics.add(LevelingConfigFiles.problem(file, ConfigSeverity.WARNING,
                                ConfigProblemCode.MISSING_REFERENCE, "WorldGuard 未启用，该区域引用暂不生效", null));
                    } else if (regions.shape(world, regionId).isEmpty()) {
                        throw new IllegalArgumentException("区域不存在或没有实际边界: " + world + "/" + regionId);
                    }
                }
                origins.put(id, new DistanceOrigin((region ? "region:" : "point:") + id,
                        enabled, world, point, regionId));
            } catch (IOException exception) {
                diagnostics.add(LevelingConfigFiles.problem(file, ConfigSeverity.ERROR,
                        ConfigProblemCode.IO_ERROR, "无法读取距离来源", exception));
            } catch (InvalidConfigurationException exception) {
                diagnostics.add(LevelingConfigFiles.problem(file, ConfigSeverity.ERROR,
                        ConfigProblemCode.YAML_SYNTAX, "距离来源 YAML 格式错误", exception));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(LevelingConfigFiles.problem(file, ConfigSeverity.ERROR,
                        ConfigProblemCode.INVALID_VALUE, exception.getMessage(), exception));
            }
        }
        return Map.copyOf(origins);
    }
}
