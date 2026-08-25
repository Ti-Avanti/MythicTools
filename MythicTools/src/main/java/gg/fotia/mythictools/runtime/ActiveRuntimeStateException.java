package gg.fotia.mythictools.runtime;

/** 完整运行时重载因仍有受管实体而被拒绝。 */
public final class ActiveRuntimeStateException extends IllegalStateException {
    private final int spawningEntities;
    private final int bossFights;

    ActiveRuntimeStateException(int spawningEntities, int bossFights) {
        super("active-managed-entities");
        this.spawningEntities = spawningEntities;
        this.bossFights = bossFights;
    }

    public int spawningEntities() {
        return spawningEntities;
    }

    public int bossFights() {
        return bossFights;
    }
}
