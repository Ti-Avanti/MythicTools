package gg.fotia.mythictools.summoning.config;

import gg.fotia.mythictools.config.YamlFiles;
import gg.fotia.mythictools.item.ItemFactory;
import gg.fotia.mythictools.summoning.effect.EffectLayer;
import gg.fotia.mythictools.summoning.visual.VisualConfig;
import java.io.File;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/** 原始编辑稿与不可变业务快照分离，整个候选验证成功后才发布。 */
public final class SummonRepository {
    public record Snapshot(Map<String, SummonDefinition> items, Map<String, String> sources,
                           Map<String, String> effects) {
        public Snapshot { items = Map.copyOf(items); sources = Map.copyOf(sources); effects = Map.copyOf(effects); }
    }
    private final File root;
    private final ItemFactory factory;
    private final int maxLayers;
    private Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of());

    public SummonRepository(File root, ItemFactory factory, int maxLayers) {
        this.root = root;
        this.factory = factory;
        this.maxLayers = maxLayers;
    }

    public Snapshot prepare() {
        Map<String, String> effects = readDirectory("effects");
        effects.forEach((id, source) -> EffectLayer.read(id, yaml(source)));
        Map<String, String> sources = readDirectory("items");
        Map<String, SummonDefinition> items = new LinkedHashMap<>();
        sources.forEach((id, source) -> items.put(id, parse(id, yaml(source), effects)));
        return new Snapshot(items, sources, effects);
    }

    public void publish(Snapshot candidate) { snapshot = candidate; }
    public Snapshot snapshot() { return snapshot; }
    public SummonDefinition get(String id) { return snapshot.items().get(id); }
    public List<String> ids(boolean effects) {
        return (effects ? snapshot.effects() : snapshot.sources()).keySet().stream().sorted().toList();
    }
    public File file(boolean effect, String id) {
        return new File(root, "summoning/" + (effect ? "effects/" : "items/") + SummonNumbers.id(id) + ".yml");
    }
    public String source(boolean effect, String id) {
        return (effect ? snapshot.effects() : snapshot.sources()).get(id);
    }
    public SummonDefinition draft(String id, YamlConfiguration yaml) { return parse(id, yaml, snapshot.effects()); }
    public List<String> references(String preset) {
        return snapshot.items().values().stream().filter(item -> item.layers().stream()
                .anyMatch(layer -> layer.preset().equals(preset))).map(SummonDefinition::id).sorted().toList();
    }
    public List<String> bossReferences(String boss) {
        return snapshot.items().values().stream().filter(item -> item.bossId().equals(boss))
                .map(SummonDefinition::id).sorted().toList();
    }
    public static void validateBossBindings(Snapshot candidate, gg.fotia.mythictools.boss.BossSnapshot bosses) {
        candidate.items().values().stream().filter(SummonDefinition::enabled).forEach(item -> {
            if (bosses.boss(item.bossId()) == null) throw new IllegalArgumentException(item.id() + ": boss-id " + item.bossId());
        });
    }

    private Map<String, String> readDirectory(String folder) {
        Map<String, String> result = new LinkedHashMap<>();
        File[] files = YamlFiles.list(new File(root, "summoning/" + folder));
        if (files == null) return result;
        Arrays.sort(files);
        for (File file : files) {
            try {
                String id = SummonNumbers.id(file.getName().substring(0, file.getName().length() - 4));
                YamlConfiguration yaml = YamlFiles.load(file);
                if (!yaml.getBoolean("deleted", false)) result.put(id, yaml.saveToString());
            } catch (Exception exception) {
                throw new IllegalArgumentException(file.getName() + ": " + exception.getMessage(), exception);
            }
        }
        return result;
    }

    private SummonDefinition parse(String id, YamlConfiguration yaml, Map<String, String> presets) {
        ConfigurationSection item = yaml.getConfigurationSection("item");
        if (item == null) throw new IllegalArgumentException(id + ": item");
        ItemStack stack = yaml.getItemStack("item-copy");
        if (stack == null) stack = factory.create(item, null, Map.of("id", id));
        if (stack.getType().isAir()) throw new IllegalArgumentException(id + ": item.material");
        stack.setAmount(1);
        String mode = yaml.getString("spawn.mode", "player-location");
        if (!Set.of("player-location", "target-block").contains(mode)) throw new IllegalArgumentException("spawn.mode");
        String countdown = yaml.getString("windup.countdown", "actionbar");
        if (!Set.of("none", "chat", "actionbar", "title").contains(countdown)) throw new IllegalArgumentException("windup.countdown");
        List<EffectLayer> layers = new ArrayList<>();
        ConfigurationSection layerSection = yaml.getConfigurationSection("effects");
        if (layerSection != null) {
            if (layerSection.getKeys(false).size() > maxLayers) throw new IllegalArgumentException("effects <= " + maxLayers);
            for (String key : layerSection.getKeys(false)) {
                ConfigurationSection override = layerSection.getConfigurationSection(key);
                if (override == null) throw new IllegalArgumentException("effects." + key);
                String preset = override.getString("preset", "arcane");
                String source = presets.get(preset);
                if (source == null) throw new IllegalArgumentException("effects." + key + ".preset: " + preset);
                YamlConfiguration resolved = yaml(source);
                override.getKeys(true).stream().filter(k -> !override.isConfigurationSection(k))
                        .forEach(k -> resolved.set(k, override.get(k)));
                layers.add(EffectLayer.read(preset, resolved));
            }
        }
        return new SummonDefinition(id, yaml.getBoolean("enabled", false), yaml.getString("boss-id", ""), stack,
                SummonNumbers.integer(yaml, "consume-amount", 1, 0, 64),
                Math.round(SummonNumbers.number(yaml, "cooldown-seconds", 60, 0, 86400) * 1000),
                yaml.getString("permission", ""), Set.copyOf(yaml.getStringList("worlds")), mode,
                SummonNumbers.number(yaml, "spawn.target-distance", 6, 1, 32),
                SummonNumbers.integer(yaml, "max-active", 1, 1, 1000),
                (int) Math.round(SummonNumbers.number(yaml, "windup.seconds", 5, 0.1, 300) * 20),
                SummonNumbers.number(yaml, "windup.charge-start", 0.1, 0, 1),
                SummonNumbers.number(yaml, "windup.move-radius", 3, 0, 64),
                yaml.getBoolean("windup.damage-interrupt", false), countdown, layers, VisualConfig.read(yaml),
                List.of(SoundCue.read(yaml, "start"), SoundCue.read(yaml, "charge"),
                        SoundCue.read(yaml, "success"), SoundCue.read(yaml, "cancel")),
                Map.of("start", yaml.getString("messages.start", ""), "countdown", yaml.getString("messages.countdown", ""),
                        "success", yaml.getString("messages.success", ""), "cancelled", yaml.getString("messages.cancelled", "")));
    }

    public static YamlConfiguration yaml(String source) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(source == null ? "" : source);
            return yaml;
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }
}
