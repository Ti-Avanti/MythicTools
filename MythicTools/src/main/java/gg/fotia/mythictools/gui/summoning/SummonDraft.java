package gg.fotia.mythictools.gui.summoning;

import gg.fotia.mythictools.summoning.config.SummonRepository;
import org.bukkit.configuration.file.YamlConfiguration;

final class SummonDraft {
    final String id;
    final boolean effect;
    final String baseline;
    final YamlConfiguration yaml;
    boolean saving;
    SummonDraft(String id, boolean effect, String baseline, String source) {
        this.id = id; this.effect = effect; this.baseline = baseline;
        this.yaml = SummonRepository.yaml(source);
    }
}
