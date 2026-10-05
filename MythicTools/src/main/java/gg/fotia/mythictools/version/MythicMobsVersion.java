package gg.fotia.mythictools.version;

import java.util.regex.Pattern;

/** MythicMobs 不同版本读取生成等级的事件阶段。 */
public final class MythicMobsVersion {
    private static final Pattern LEGACY_SPAWN_LEVEL =
            Pattern.compile("^5\\.[0-8](?:\\.\\d+)?(?:[-+].*)?$");

    private MythicMobsVersion() { }

    /** MM 5.9 起读取 PreSpawn 的等级，旧版仅读取 Spawn 的等级。 */
    public static boolean usesLegacySpawnLevelEvent(String version) {
        return version != null && LEGACY_SPAWN_LEVEL.matcher(version.trim()).matches();
    }
}
