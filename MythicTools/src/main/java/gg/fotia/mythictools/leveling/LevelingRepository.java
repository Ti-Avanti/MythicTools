package gg.fotia.mythictools.leveling;

import gg.fotia.mythictools.config.ConfigDiagnostic;
import gg.fotia.mythictools.config.ConfigLoadReport;
import gg.fotia.mythictools.config.ConfigProblemCode;
import gg.fotia.mythictools.config.ConfigSeverity;
import gg.fotia.mythictools.config.ConfigValues;
import gg.fotia.mythictools.config.PreparedSnapshot;
import gg.fotia.mythictools.config.RepositoryLoadContext;
import gg.fotia.mythictools.config.RepositorySnapshotStore;
import gg.fotia.mythictools.config.YamlFiles;
import gg.fotia.mythictools.spawning.SpawningSnapshot;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;

/** 准备距离等级规则，并通过现有仓库快照统一发布。 */
public final class LevelingRepository {
    private final RepositoryLoadContext context;
    private final RepositorySnapshotStore store;
    private final RegionLookup regions;

    public LevelingRepository(RepositoryLoadContext context, RepositorySnapshotStore store, RegionLookup regions) {
        this.context = context;
        this.store = store;
        this.regions = regions;
    }

    public PreparedSnapshot<LevelingSnapshot> prepare(SpawningSnapshot spawning) {
        regions.invalidate();
        List<ConfigDiagnostic> diagnostics = new ArrayList<>();
        LevelOriginRepository origins = new LevelOriginRepository(context, regions);
        Map<String, DistanceOrigin> points = origins.load(false, diagnostics);
        Map<String, DistanceOrigin> regionOrigins = origins.load(true, diagnostics);
        List<LevelRule> rules = new ArrayList<>();
        for (File file : LevelingConfigFiles.list(context.dataFolder(), "groups")) {
            try {
                var yaml = YamlFiles.load(file);
                DistanceCurve curve = curve(yaml);
                int priority = ConfigValues.intOrDefault(yaml, "priority", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
                if (!LevelingConfigFiles.enabled(yaml, false)) {
                    continue;
                }
                Set<String> mobs = new LinkedHashSet<>(strings(yaml, "mob-ids"));
                for (String groupId : strings(yaml, "mob-groups")) {
                    var group = spawning.mobGroup(groupId);
                    if (group == null) {
                        throw new IllegalArgumentException("怪物组不存在: " + groupId);
                    }
                    group.members().forEach(member -> mobs.add(member.mobId()));
                }
                if (mobs.isEmpty()) {
                    throw new IllegalArgumentException("至少选择一个 MM 怪物或怪物组");
                }
                for (String mob : mobs) {
                    if (!context.mythicMobExists().test(mob)) {
                        throw new IllegalArgumentException("MM 怪物不存在: " + mob);
                    }
                }
                List<DistanceOrigin> selected = new ArrayList<>();
                bind(strings(yaml, "point-ids"), points, selected, "坐标点");
                bind(strings(yaml, "region-ids"), regionOrigins, selected, "区域引用");
                if (selected.isEmpty()) {
                    throw new IllegalArgumentException("至少绑定一个坐标点或区域引用");
                }
                rules.add(new LevelRule(LevelingConfigFiles.id(file), priority, mobs, selected, curve));
            } catch (IOException exception) {
                diagnostics.add(LevelingConfigFiles.problem(file, ConfigSeverity.ERROR,
                        ConfigProblemCode.IO_ERROR, "无法读取距离等级组", exception));
            } catch (InvalidConfigurationException exception) {
                diagnostics.add(LevelingConfigFiles.problem(file, ConfigSeverity.ERROR,
                        ConfigProblemCode.YAML_SYNTAX, "距离等级组 YAML 格式错误", exception));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(LevelingConfigFiles.problem(file, ConfigSeverity.ERROR,
                        ConfigProblemCode.INVALID_VALUE, exception.getMessage(), exception));
            }
        }
        return new PreparedSnapshot<>(new LevelingSnapshot(points, regionOrigins, rules), new ConfigLoadReport(diagnostics));
    }

    public java.util.Optional<LevelingSnapshot.Result> resolve(String mobId, Location location, double level) {
        return location.getWorld() == null ? java.util.Optional.empty() : store.current().leveling()
                .resolve(mobId, location.getWorld().getName(), location.getX(), location.getZ(), level, regions);
    }

    public List<String> worldGuardRegionIds(String world) { return regions.ids(world); }

    private static DistanceCurve curve(ConfigurationSection yaml) {
        return new DistanceCurve(DistanceCurve.Mode.parse(yaml.getString("level-mode", "replace")),
                number(yaml, "base-level", 1.0, 1.0), number(yaml, "safe-distance", 0.0, 0.0),
                number(yaml, "distance-per-level", 100.0, Double.MIN_VALUE),
                number(yaml, "level-increase", 1.0, Double.MIN_VALUE), number(yaml, "max-level", 100.0, 1.0));
    }

    private static double number(ConfigurationSection yaml, String path, double fallback, double minimum) {
        return ConfigValues.doubleOrDefault(yaml, path, fallback, minimum, Double.MAX_VALUE);
    }

    private static List<String> strings(ConfigurationSection yaml, String path) {
        if (yaml.contains(path) && !yaml.isList(path)) {
            throw new IllegalArgumentException(path + " 必须是列表");
        }
        return yaml.getStringList(path).stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }

    private static void bind(List<String> ids, Map<String, DistanceOrigin> available,
                             List<DistanceOrigin> selected, String label) {
        for (String id : ids) {
            DistanceOrigin origin = available.get(id);
            if (origin == null) {
                throw new IllegalArgumentException(label + "不存在: " + id);
            }
            selected.add(origin);
        }
    }
}
