package gg.fotia.mythictools.integration;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.constants.MobKeys;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** MythicMobs 5.x 的集中集成模块。 */
public final class MythicMobsAdapter implements MythicMobGateway {
    /** 生成指定 MythicMob，ID 无效时返回空。 */
    public Optional<ActiveMob> spawn(String mobId, Location location, double level) {
        if (MythicBukkit.inst().getMobManager().getMythicMob(mobId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(MythicBukkit.inst().getMobManager().spawnMob(mobId, location, level));
    }

    /** 获取 Bukkit 实体对应的 MythicMob ID。 */
    public Optional<String> mobId(Entity entity) {
        // 使用新旧 MM 共有的持久标记，死亡注销后仍能识别，避免依赖新版 getMythicType。
        return Optional.ofNullable(entity.getPersistentDataContainer().get(MobKeys.TYPE, PersistentDataType.STRING));
    }

    /** 获取活动 MythicMob。 */
    public Optional<ActiveMob> activeMob(UUID entityId) {
        return MythicBukkit.inst().getMobManager().getActiveMob(entityId);
    }

    @Override
    public boolean isLoadedAndActive(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        return entity != null && entity.isValid() && activeMob(entityId).isPresent();
    }

    /** 判断 MythicMob 配置是否存在。 */
    public boolean exists(String mobId) {
        return MythicBukkit.inst().getMobManager().getMythicMob(mobId).isPresent();
    }

    /** 返回当前已加载的全部 MythicMob ID。 */
    public List<String> mobIds() {
        return MythicBukkit.inst().getMobManager().getMobNames().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
