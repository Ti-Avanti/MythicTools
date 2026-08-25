package gg.fotia.mythictools.runtime;

/** 完整运行时切换前需要保持为空的受管运行状态。 */
public record RuntimeActivity(int spawningEntities, int bossFights) {
    public RuntimeActivity {
        if (spawningEntities < 0 || bossFights < 0) {
            throw new IllegalArgumentException("运行时活动数量不能小于 0");
        }
    }

    public void requireIdle() {
        if (spawningEntities > 0 || bossFights > 0) {
            throw new ActiveRuntimeStateException(spawningEntities, bossFights);
        }
    }
}
