package gg.fotia.mythictools.summoning.config;

import org.bukkit.configuration.ConfigurationSection;

public record SoundCue(String stage, String sound, float volume, float pitch) {
    public static SoundCue read(ConfigurationSection yaml, String stage) {
        String path = "sounds." + stage;
        return new SoundCue(stage, yaml.getString(path + ".sound", ""),
                (float) SummonNumbers.number(yaml, path + ".volume", 1, 0, 4),
                (float) SummonNumbers.number(yaml, path + ".pitch", 1, 0.5, 2));
    }
}
