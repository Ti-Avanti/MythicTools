package gg.fotia.mythictools.blackboxtest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 集中维护兼容旧命令的验收夹具名称与别名。 */
final class FixtureCatalog {
    private static final Map<String, String> ALIASES = Map.of(
            "qa-point", "spawn-point",
            "qa-boss", "boss-manual",
            "external-remove", "spawn-point");
    private static final List<String> ACCEPTANCE_MODES = List.of(
            "qa-point", "qa-boss", "invalid-reload", "valid-reload", "limit-overflow",
            "reward-offline", "reward-overflow", "corrupt-pending", "chat-await", "external-remove");

    private final List<String> modes;

    FixtureCatalog(List<String> existingModes) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(existingModes);
        merged.addAll(ACCEPTANCE_MODES);
        modes = List.copyOf(merged);
    }

    List<String> modes() {
        return modes;
    }

    String canonical(String rawMode) {
        String normalized = rawMode.toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(normalized, normalized);
    }
}
