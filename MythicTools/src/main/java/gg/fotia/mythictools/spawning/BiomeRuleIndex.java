package gg.fotia.mythictools.spawning;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.block.Biome;

/** 在配置发布时按世界与群系建立索引，保留原规则顺序。 */
final class BiomeRuleIndex {
    private final Map<Biome, List<BiomeSpawnRule>> anyWorld;
    private final Map<String, Map<Biome, List<BiomeSpawnRule>>> worlds;

    BiomeRuleIndex(Collection<BiomeSpawnRule> rules) {
        anyWorld = group(rules.stream().filter(rule -> rule.worlds().isEmpty()).toList());
        Map<String, Map<Biome, List<BiomeSpawnRule>>> byWorld = new HashMap<>();
        rules.stream().flatMap(rule -> rule.worlds().stream()).distinct().forEach(world ->
                byWorld.put(world, group(rules.stream()
                        .filter(rule -> rule.worlds().isEmpty() || rule.worlds().contains(world)).toList())));
        worlds = Map.copyOf(byWorld);
    }

    List<BiomeSpawnRule> matching(String world, Biome biome) {
        return worlds.getOrDefault(world, anyWorld).getOrDefault(biome, List.of());
    }

    private static Map<Biome, List<BiomeSpawnRule>> group(Collection<BiomeSpawnRule> rules) {
        Map<Biome, java.util.ArrayList<BiomeSpawnRule>> index = new HashMap<>();
        for (BiomeSpawnRule rule : rules) {
            if (rule.enabled()) {
                rule.biomes().forEach(biome -> index.computeIfAbsent(biome, ignored -> new java.util.ArrayList<>()).add(rule));
            }
        }
        Map<Biome, List<BiomeSpawnRule>> result = new HashMap<>();
        index.forEach((biome, matching) -> result.put(biome, List.copyOf(matching)));
        return Map.copyOf(result);
    }
}
