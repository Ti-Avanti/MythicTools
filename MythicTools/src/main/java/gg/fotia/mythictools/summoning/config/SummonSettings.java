package gg.fotia.mythictools.summoning.config;

import org.bukkit.configuration.ConfigurationSection;

public record SummonSettings(int periodTicks, double range, int particleBudget, int maxSessions,
                             int maxLayers, double quality) {
    public static SummonSettings read(ConfigurationSection yaml) {
        return new SummonSettings(
                SummonNumbers.integer(yaml, "Summoning.Update-Ticks", 2, 1, 10),
                SummonNumbers.number(yaml, "Summoning.View-Range", 32, 4, 96),
                SummonNumbers.integer(yaml, "Summoning.Particle-Budget", 12000, 128, 100000),
                SummonNumbers.integer(yaml, "Summoning.Max-Sessions", 32, 1, 256),
                SummonNumbers.integer(yaml, "Summoning.Max-Layers", 3, 1, 8),
                switch (yaml.getString("Summoning.Quality", "medium").toLowerCase(java.util.Locale.ROOT)) {
                    case "low" -> 0.5;
                    case "medium" -> 1;
                    case "high" -> 1.5;
                    default -> throw new IllegalArgumentException("Summoning.Quality: low / medium / high");
                });
    }
}
