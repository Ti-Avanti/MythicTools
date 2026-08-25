package gg.fotia.mythictools.version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;

/** 已声明兼容范围内的服务端版本能力描述。 */
public final class ServerVersion {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[.-].*)?$");
    private static final ServerVersion UNSUPPORTED = new ServerVersion(0, 0, 0, false);

    private final int major;
    private final int minor;
    private final int patch;
    private final boolean supported;

    private ServerVersion(int major, int minor, int patch, boolean supported) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.supported = supported;
    }

    /** 根据当前 Bukkit 版本字符串检测服务端。 */
    public static ServerVersion detect() {
        return fromBukkitVersion(Bukkit.getBukkitVersion());
    }

    /**
     * 解析 Bukkit 版本字符串并判定是否处于本插件的兼容范围。
     *
     * 支持 1.20.1、1.20.4、所有 1.21.x、26.1.2 及以上补丁和所有 26.2.x。
     */
    public static ServerVersion fromBukkitVersion(String bukkitVersion) {
        if (bukkitVersion == null) {
            return UNSUPPORTED;
        }
        Matcher matcher = VERSION_PATTERN.matcher(bukkitVersion.trim());
        if (!matcher.matches()) {
            return UNSUPPORTED;
        }
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return new ServerVersion(major, minor, patch, isSupportedRange(major, minor, patch));
        } catch (NumberFormatException exception) {
            return UNSUPPORTED;
        }
    }

    private static boolean isSupportedRange(int major, int minor, int patch) {
        return (major == 1 && minor == 20 && (patch == 1 || patch == 4))
                || (major == 1 && minor == 21)
                || (major == 26 && ((minor == 1 && patch >= 2) || minor == 2));
    }

    public boolean isSupported() {
        return supported;
    }

    /**
     * 1.21.2 起的 Bukkit/Spigot API 提供 item-model 与 tooltip-style 对应的 ItemMeta 方法。
     */
    public boolean supportsItemModelAndTooltipStyle() {
        return supported && isAtLeast(1, 21, 2);
    }

    /** 判断当前版本是否至少达到给定语义版本。 */
    public boolean isAtLeast(int targetMajor, int targetMinor, int targetPatch) {
        if (major != targetMajor) {
            return major > targetMajor;
        }
        if (minor != targetMinor) {
            return minor > targetMinor;
        }
        return patch >= targetPatch;
    }
}
