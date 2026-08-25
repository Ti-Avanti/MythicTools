package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.boss.BossPhaseMode;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

/** 根据 Boss 阶段模式隐藏不会生效的另一套阶段字段。 */
final class BossBasicEditorFields {

    private BossBasicEditorFields() {
    }

    static List<String> forMode(YamlConfiguration yaml, List<String> current) {
        BossPhaseMode mode = BossPhaseMode.fromConfig(yaml.getString("phase-mode", "death-respawn"));
        List<String> fields = new ArrayList<>(current.stream()
                .filter(field -> mode == BossPhaseMode.DEATH_RESPAWN
                        ? !field.startsWith("mythic-native.")
                        : !field.equals("phases") && !field.startsWith("phases."))
                .toList());
        ensure(fields, "phase-mode");
        if (mode == BossPhaseMode.DEATH_RESPAWN) {
            ensure(fields, "phases");
        } else {
            ensure(fields, "mythic-native.mob");
            ensure(fields, "mythic-native.level");
        }
        fields.sort(String::compareTo);
        return List.copyOf(fields);
    }

    private static void ensure(List<String> fields, String field) {
        if (!fields.contains(field)) {
            fields.add(field);
        }
    }
}
