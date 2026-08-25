package gg.fotia.mythictools.blackboxtest;

/** 保存跨命令的测试夹具与最近一次正式重载证据。 */
final class ProbeState {
    private String activeFixture = "none";
    private ReloadState lastReload = ReloadState.initial();

    synchronized void activateFixture(String fixture) {
        activeFixture = fixture;
    }

    synchronized String activeFixture() {
        return activeFixture;
    }

    synchronized void recordReload(boolean success, String before, String after, String reason) {
        recordReload(success, before, after, reason, true);
    }

    synchronized void recordReload(
            boolean success,
            String before,
            String after,
            String reason,
            boolean identityPreserved) {
        lastReload = new ReloadState(success, before, after, reason, identityPreserved, false);
    }

    synchronized ReloadState lastReload() {
        return lastReload;
    }

    synchronized void clear() {
        activeFixture = "none";
        lastReload = ReloadState.initial();
    }

    record ReloadState(
            boolean success,
            String before,
            String after,
            String reason,
            boolean identityPreserved,
            boolean never) {
        static ReloadState initial() {
            return new ReloadState(false, "none", "none", "none", false, true);
        }

        String status() {
            return never ? "never" : success ? "success" : "failure";
        }

        boolean preserved() {
            return !never && before.equals(after);
        }
    }
}
