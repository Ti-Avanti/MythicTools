package gg.fotia.mythictools.blackboxtest;

/** 确保 QA 实体的 MythicMobs 登记与世界实体都被清理。 */
final class TestEntityCleanup {
    private TestEntityCleanup() {
    }

    static void remove(Runnable mythicRemoval, Runnable physicalRemoval) {
        try {
            mythicRemoval.run();
        } finally {
            physicalRemoval.run();
        }
    }
}
