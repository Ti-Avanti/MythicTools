package gg.fotia.mythictools.reward;

import gg.fotia.mythictools.config.ConfigDiagnostic;
import gg.fotia.mythictools.config.ConfigValues;
import gg.fotia.mythictools.config.ConfigDomain;
import gg.fotia.mythictools.config.ConfigLoadException;
import gg.fotia.mythictools.config.ConfigLoadMode;
import gg.fotia.mythictools.config.ConfigLoadReport;
import gg.fotia.mythictools.config.ConfigProblemCode;
import gg.fotia.mythictools.config.ConfigSeverity;
import gg.fotia.mythictools.config.PreparedSnapshot;
import gg.fotia.mythictools.config.RepositorySnapshotStore;
import gg.fotia.mythictools.config.SafetyLimits;
import gg.fotia.mythictools.config.RepositoryLoadContext;
import gg.fotia.mythictools.config.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** 加载与查询稀有度、掉落组和怪物掉落规则。 */
public final class RewardRepository {
    private final RepositoryLoadContext context;
    private final RepositorySnapshotStore store;
    private final SafetyLimits limits;

    public RewardRepository(JavaPlugin plugin) {
        this(RepositoryLoadContext.rewards(plugin), new RepositorySnapshotStore(), SafetyLimits.defaults());
    }

    public RewardRepository(JavaPlugin plugin, RepositorySnapshotStore store) {
        this(RepositoryLoadContext.rewards(plugin), store, SafetyLimits.defaults());
    }

    public RewardRepository(JavaPlugin plugin, RepositorySnapshotStore store, SafetyLimits limits) {
        this(RepositoryLoadContext.rewards(plugin), store, limits);
    }

    public RewardRepository(RepositoryLoadContext context, RepositorySnapshotStore store) {
        this(context, store, SafetyLimits.defaults());
    }

    public RewardRepository(RepositoryLoadContext context, RepositorySnapshotStore store, SafetyLimits limits) {
        this.context = context;
        this.store = store;
        this.limits = java.util.Objects.requireNonNull(limits, "limits");
    }

    /** 兼容旧调用；新运行时应通过 RepositoryReloadCoordinator 一次发布三个仓库。 */
    public void reload() {
        PreparedSnapshot<RewardSnapshot> prepared = prepare(ConfigLoadMode.STARTUP_LENIENT);
        if (prepared.report().blocks(ConfigLoadMode.STARTUP_LENIENT)) {
            throw new ConfigLoadException(prepared.report());
        }
        logDiagnostics(prepared.report());
        store.publish(store.current().withRewards(prepared.snapshot()));
    }

    /** 只构造本仓库候选快照，不修改当前发布指针。 */
    public PreparedSnapshot<RewardSnapshot> prepare(ConfigLoadMode mode) {
        Map<String, Rarity> rarities = new LinkedHashMap<>();
        Map<String, DropGroup> groups = new LinkedHashMap<>();
        Map<String, MobDropRule> mobRules = new LinkedHashMap<>();
        List<ConfigDiagnostic> diagnostics = new ArrayList<>();
        loadRarities(rarities, diagnostics);
        if (diagnostics.stream().noneMatch(value -> value.severity() == ConfigSeverity.FATAL)) {
            loadGroups(rarities, groups, diagnostics);
            loadMobRules(groups, mobRules, diagnostics);
        }
        return new PreparedSnapshot<>(
                new RewardSnapshot(rarities, groups, mobRules),
                new ConfigLoadReport(diagnostics));
    }

    public Optional<Rarity> rarity(String id) {
        return store.current().rewards().rarity(id);
    }

    public Rarity rarityOrDefault(String id) {
        return rarity(id).orElseGet(() -> new Rarity(id, Map.of("zh_CN", id, "en_US", id), "", 999));
    }

    /** 返回按优先级排序的用户预设稀有度。 */
    public List<Rarity> rarities() {
        return Rarity.ordered(store.current().rewards().rarities());
    }

    public Optional<DropGroup> group(String id) {
        return store.current().rewards().group(id);
    }

    /** 查询掉落组中的确定奖励条目。 */
    public Optional<RewardEntry> entry(String groupId, String entryId) {
        return group(groupId).flatMap(group -> group.entries().stream()
                .filter(entry -> entry.id().equals(entryId)).findFirst());
    }

    public Optional<MobDropRule> mobRule(String mobId) {
        return store.current().rewards().mobRule(mobId);
    }

    public Collection<String> groupIds() {
        return store.current().rewards().groupIds();
    }

    /** 返回 GUI 可引用的全部首次击败专用奖励。 */
    public List<FirstDefeatRewardOption> firstDefeatOptions() {
        List<FirstDefeatRewardOption> result = new ArrayList<>();
        for (String groupId : groupIds()) {
            group(groupId).orElseThrow().entries().stream()
                    .filter(entry -> entry.grantMode() == RewardGrantMode.FIRST_DEFEAT)
                    .forEach(entry -> result.add(new FirstDefeatRewardOption(groupId, entry)));
        }
        result.sort(java.util.Comparator.comparing(FirstDefeatRewardOption::groupId)
                .thenComparing(option -> option.entry().id()));
        return List.copyOf(result);
    }

    public Collection<String> mobIds() {
        return store.current().rewards().mobIds();
    }

    /** 提供给 Boss 候选解析与原子重载协调器的只读当前快照。 */
    public RewardSnapshot currentSnapshot() {
        return store.current().rewards();
    }

    public File groupFile(String id) {
        return new File(context.dataFolder(), "drops/groups/" + id + ".yml");
    }

    public File mobFile(String id) {
        return new File(context.dataFolder(), "drops/mobs/" + id + ".yml");
    }

    private void loadRarities(Map<String, Rarity> rarities, List<ConfigDiagnostic> diagnostics) {
        File file = new File(context.dataFolder(), "rarities.yml");
        try {
            YamlConfiguration yaml = YamlFiles.load(file);
            ConfigurationSection root = requireSection(yaml, "rarities", file);
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = requireSection(root, id, file);
                rarities.put(id, new Rarity(id, readLocalized(section, "display", id),
                        section.getString("color", ""), section.getInt("priority", 999)));
            }
        } catch (IOException exception) {
            diagnostics.add(diagnostic(ConfigSeverity.FATAL, file, "rarities", ConfigProblemCode.IO_ERROR,
                    "无法读取稀有度配置", exception));
        } catch (InvalidConfigurationException exception) {
            diagnostics.add(diagnostic(ConfigSeverity.FATAL, file, "$", ConfigProblemCode.YAML_SYNTAX,
                    "稀有度 YAML 语法错误", exception));
        } catch (IllegalArgumentException exception) {
            diagnostics.add(diagnostic(ConfigSeverity.FATAL, file, "rarities", problemCode(exception),
                    exception.getMessage(), exception));
        }
    }

    private void loadGroups(
            Map<String, Rarity> rarities,
            Map<String, DropGroup> groups,
            List<ConfigDiagnostic> diagnostics) {
        for (File file : yamlFiles(new File(context.dataFolder(), "drops/groups"))) {
            try {
                YamlConfiguration yaml = YamlFiles.load(file);
                String id = stripExtension(file.getName());
                ConfigurationSection entries = requireSection(yaml, "entries", file);
                List<RewardEntry> parsed = new ArrayList<>();
                for (String entryId : entries.getKeys(false)) {
                    parsed.add(parseEntry(entryId, requireSection(entries, entryId, file), file, rarities));
                }
                if (parsed.isEmpty()) {
                    throw new IllegalArgumentException("entries 不能为空");
                }
                checkedTotalWeight(parsed.stream().map(RewardEntry::weight).toList(), "entries.weight");
                groups.put(id, new DropGroup(id, parsed));
            } catch (IOException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.IO_ERROR,
                        "无法读取掉落组配置", exception));
            } catch (InvalidConfigurationException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.YAML_SYNTAX,
                        "掉落组 YAML 语法错误", exception));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "entries", problemCode(exception),
                        exception.getMessage(), exception));
            }
        }
    }

    private void loadMobRules(
            Map<String, DropGroup> groups,
            Map<String, MobDropRule> mobRules,
            List<ConfigDiagnostic> diagnostics) {
        for (File file : yamlFiles(new File(context.dataFolder(), "drops/mobs"))) {
            try {
                YamlConfiguration yaml = YamlFiles.load(file);
                String mobId = yaml.getString("mob-id", stripExtension(file.getName()));
                int maxDrops = ConfigValues.intOrDefault(
                        yaml, "max-drops", 1, 0, limits.maxDropsPerMob());
                int minExp = ConfigValues.intOrDefault(
                        yaml, "experience.min", 0, 0, limits.maxExperiencePerMob());
                int maxExp = ConfigValues.intOrDefault(
                        yaml, "experience.max", minExp, 0, limits.maxExperiencePerMob());
                ConfigValues.requireOrdered(minExp, maxExp, "experience.min", "experience.max");
                List<DropGroupRef> refs = new ArrayList<>();
                for (Map<?, ?> raw : yaml.getMapList("groups")) {
                    String groupId = String.valueOf(raw.get("id"));
                    long weight = ConfigValues.longValueOrDefault(
                            raw.get("weight"), 1L, "groups.weight", 1L, limits.maxWeight());
                    int min = ConfigValues.intValueOrDefault(
                            raw.get("min-amount"), 0, "groups.min-amount", 0, limits.maxDropsPerMob());
                    int max = ConfigValues.intValueOrDefault(
                            raw.get("max-amount"), 1, "groups.max-amount", 0, limits.maxDropsPerMob());
                    ConfigValues.requireOrdered(min, max, "groups.min-amount", "groups.max-amount");
                    if (!groups.containsKey(groupId)) {
                        throw new IllegalArgumentException("引用了不存在的掉落组: " + groupId);
                    }
                    refs.add(new DropGroupRef(groupId, weight, min, max));
                }
                checkedTotalWeight(refs.stream().map(DropGroupRef::weight).toList(), "groups.weight");
                FirstDefeatRewardConfig firstDefeat = parseFirstDefeat(
                        yaml, "first-defeat", groups, file, true);
                mobRules.put(mobId, new MobDropRule(
                        mobId, maxDrops, minExp, maxExp, refs, firstDefeat));
            } catch (IOException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.IO_ERROR,
                        "无法读取怪物掉落配置", exception));
            } catch (InvalidConfigurationException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "$", ConfigProblemCode.YAML_SYNTAX,
                        "怪物掉落 YAML 语法错误", exception));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(diagnostic(ConfigSeverity.ERROR, file, "groups", problemCode(exception),
                        exception.getMessage(), exception));
            }
        }
    }

    private RewardEntry parseEntry(
            String id,
            ConfigurationSection section,
            File file,
            Map<String, Rarity> rarities) {
        RewardType type = parseEnum(RewardType.class, section.getString("type", "item"), "type");
        RewardGrantMode grantMode = parseEnum(
                RewardGrantMode.class, section.getString("grant-mode", "weighted"), "grant-mode");
        long weight = grantMode == RewardGrantMode.WEIGHTED
                ? ConfigValues.longOrDefault(section, "weight", 1L, 1L, limits.maxWeight())
                : 0L;
        int amountLimit = type == RewardType.ITEM
                ? limits.maxItemAmountPerGrant()
                : limits.maxCommandExecutionsPerGrant();
        int min = ConfigValues.intOrDefault(section, "min-amount", 1, 1, amountLimit);
        int max = ConfigValues.intOrDefault(section, "max-amount", min, 1, amountLimit);
        ConfigValues.requireOrdered(min, max,
                "entries." + id + ".min-amount", "entries." + id + ".max-amount");
        String rarity = section.getString("rarity", "common");
        if (!rarities.containsKey(rarity)) {
            throw new IllegalArgumentException("entries." + id + " 引用了不存在的稀有度: " + rarity);
        }
        ItemStack item = null;
        String command = null;
        if (type == RewardType.ITEM) {
            item = section.getItemStack("item");
            if (item == null) {
                Material material = Material.matchMaterial(section.getString("material", ""));
                if (material == null || material.isAir()) {
                    throw new IllegalArgumentException(file.getName() + " entries." + id + " 缺少有效 item/material");
                }
                item = new ItemStack(material);
            }
            item.setAmount(1);
        } else {
            command = section.getString("command");
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("entries." + id + ".command 不能为空");
            }
        }
        return new RewardEntry(
                id,
                type,
                grantMode,
                weight,
                min,
                max,
                rarity,
                readLocalized(section, "display", id),
                readLocalized(section, "message", ""),
                item,
                parseEnum(RewardDelivery.class, section.getString("delivery", "ground"), "delivery"),
                command,
                parseEnum(CommandExecutorType.class, section.getString("executor", "console"), "executor"));
    }

    private FirstDefeatRewardConfig parseFirstDefeat(
            ConfigurationSection yaml,
            String path,
            Map<String, DropGroup> groups,
            File file,
            boolean killerOnly) {
        if (!yaml.getBoolean(path + ".enabled", false)) {
            return FirstDefeatRewardConfig.disabled();
        }
        FirstDefeatScope scope = parseEnum(
                FirstDefeatScope.class, yaml.getString(path + ".scope", "player"), path + ".scope");
        FirstDefeatRecipient recipient = parseEnum(
                FirstDefeatRecipient.class,
                yaml.getString(path + ".recipient", "killer"), path + ".recipient");
        if (killerOnly && recipient != FirstDefeatRecipient.KILLER) {
            throw new IllegalArgumentException(file.getName() + " 普通 MythicMob 首次奖励只能发给击杀者");
        }
        List<RewardEntryRef> entries = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList(path + ".entries")) {
            String groupId = String.valueOf(raw.get("group"));
            String entryId = String.valueOf(raw.get("entry"));
            DropGroup group = groups.get(groupId);
            RewardEntry entry = group == null ? null : group.entries().stream()
                    .filter(candidate -> candidate.id().equals(entryId)).findFirst().orElse(null);
            if (entry == null) {
                throw new IllegalArgumentException(file.getName() + " 引用了不存在的首次奖励: "
                        + groupId + "/" + entryId);
            }
            if (entry.grantMode() != RewardGrantMode.FIRST_DEFEAT) {
                throw new IllegalArgumentException(file.getName() + " 引用的奖励不是 first-defeat 模式: "
                        + groupId + "/" + entryId);
            }
            entries.add(new RewardEntryRef(groupId, entryId));
        }
        return new FirstDefeatRewardConfig(true, scope, recipient, entries);
    }

    private static Map<String, String> readLocalized(ConfigurationSection section, String path, String fallback) {
        ConfigurationSection localized = section.getConfigurationSection(path);
        if (localized == null) {
            String single = section.getString(path, fallback);
            return Map.of("zh_CN", single, "en_US", single);
        }
        Map<String, String> values = new HashMap<>();
        for (String locale : localized.getKeys(false)) {
            values.put(locale, localized.getString(locale, fallback));
        }
        return values;
    }

    private static ConfigurationSection requireSection(ConfigurationSection parent, String path, File file) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException(file.getName() + " 缺少节点 " + path);
        }
        return section;
    }

    private static List<File> yamlFiles(File directory) {
        File[] files = directory.listFiles((ignored, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        return files == null ? List.of() : java.util.Arrays.stream(files).sorted().toList();
    }

    private static String stripExtension(String name) {
        return name.substring(0, name.length() - 4);
    }

    private static long checkedTotalWeight(List<Long> weights, String path) {
        long total = 0L;
        for (long weight : weights) {
            total = ConfigValues.addExact(total, weight, path);
        }
        return total;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " 的值无效: " + value, exception);
        }
    }

    private ConfigDiagnostic diagnostic(
            ConfigSeverity severity,
            File file,
            String yamlPath,
            ConfigProblemCode code,
            String message,
            Throwable cause) {
        return new ConfigDiagnostic(
                severity, ConfigDomain.REWARDS, file.toPath(), yamlPath, code, message, cause);
    }

    private static ConfigProblemCode problemCode(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("缺少节点")) {
            return ConfigProblemCode.MISSING_SECTION;
        }
        if (message.contains("缺少") || message.contains("不能为空")) {
            return ConfigProblemCode.MISSING_VALUE;
        }
        if (message.contains("不存在")) {
            return ConfigProblemCode.MISSING_REFERENCE;
        }
        return ConfigProblemCode.INVALID_VALUE;
    }

    private void logDiagnostics(ConfigLoadReport report) {
        for (ConfigDiagnostic diagnostic : report.diagnostics()) {
            context.logger().log(
                    diagnostic.severity() == ConfigSeverity.WARNING
                            ? java.util.logging.Level.WARNING : java.util.logging.Level.SEVERE,
                    diagnostic.sourcePath() + "#" + diagnostic.yamlPath() + " ["
                            + diagnostic.problemCode() + "] " + diagnostic.message(),
                    diagnostic.cause());
        }
    }
}
