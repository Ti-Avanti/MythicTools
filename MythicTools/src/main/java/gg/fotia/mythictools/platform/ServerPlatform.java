package gg.fotia.mythictools.platform;

import java.util.Locale;
import org.bukkit.Bukkit;

/** 当前服务端提供的 Bukkit 平台能力。 */
public enum ServerPlatform {
    PAPER,
    SPIGOT;

    /** 根据服务端公开名称选择安全的平台实现，未知分支按 Spigot API 处理。 */
    public static ServerPlatform detect() {
        return detect(Bukkit.getName(), Bukkit.getVersion());
    }

    static ServerPlatform detect(String name, String version) {
        String identity = ((name == null ? "" : name) + " " + (version == null ? "" : version))
                .toLowerCase(Locale.ROOT);
        return containsPaperIdentity(identity) ? PAPER : SPIGOT;
    }

    private static boolean containsPaperIdentity(String identity) {
        return identity.contains("paper")
                || identity.contains("purpur")
                || identity.contains("leaf")
                || identity.contains("folia");
    }
}
