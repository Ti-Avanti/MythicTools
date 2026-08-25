package gg.fotia.mythictools.platform;

import java.util.logging.Logger;

/** 创建与当前服务端能力匹配的平台桥接。 */
public final class PlatformBridgeFactory {
    private PlatformBridgeFactory() {
    }

    public static PlatformBridge create(ServerPlatform platform, Logger logger) {
        return platform == ServerPlatform.PAPER
                ? new PaperPlatformBridge()
                : new SpigotPlatformBridge(logger);
    }
}
