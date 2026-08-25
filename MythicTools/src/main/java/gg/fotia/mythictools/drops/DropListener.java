package gg.fotia.mythictools.drops;

import gg.fotia.mythictools.boss.BossDropAction;
import gg.fotia.mythictools.integration.MythicMobGateway;
import gg.fotia.mythictools.reward.MobDropRule;
import gg.fotia.mythictools.reward.RewardRepository;
import gg.fotia.mythictools.reward.RewardService;
import gg.fotia.mythictools.reward.WeightedRewardSelector;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** 接管已配置 MythicMob 的原掉落并发放两层权重奖励。 */
public final class DropListener implements Listener {
    private final MythicMobGateway mythicMobs;
    private final RewardRepository repository;
    private final WeightedRewardSelector selector;
    private final RewardService rewards;
    private final Function<UUID, Optional<BossDropAction>> bossDropAction;

    public DropListener(
            MythicMobGateway mythicMobs,
            RewardRepository repository,
            WeightedRewardSelector selector,
            RewardService rewards,
            Function<UUID, Optional<BossDropAction>> bossDropAction) {
        this.mythicMobs = mythicMobs;
        this.repository = repository;
        this.selector = selector;
        this.rewards = rewards;
        this.bossDropAction = bossDropAction;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        BossDropAction action = actionFor(event.getEntity().getUniqueId());
        if (action == BossDropAction.CLEAR_MYTHIC_DROPS) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        if (action == BossDropAction.KEEP_MYTHIC_DROPS) {
            return;
        }
        mythicMobs.mobId(event.getEntity()).flatMap(repository::mobRule).ifPresent(rule -> {
            event.getDrops().clear();
            event.setDroppedExp(randomBetween(rule.minExperience(), rule.maxExperience()));
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        BossDropAction action = actionFor(event.getEntity().getUniqueId());
        if (action == BossDropAction.CLEAR_MYTHIC_DROPS) {
            event.setDrops(new ArrayList<>());
            return;
        }
        if (action == BossDropAction.KEEP_MYTHIC_DROPS) {
            return;
        }
        repository.mobRule(event.getMobType().getInternalName()).ifPresent(rule -> {
            event.setDrops(new ArrayList<>());
            if (bossDropAction.apply(event.getEntity().getUniqueId()).isPresent()) {
                return;
            }
            Player killer = event.getKiller() instanceof Player player ? player : null;
            Location location = event.getEntity().getLocation();
            UUID recipientId = killer == null ? null : killer.getUniqueId();
            String recipientName = killer == null ? "" : killer.getName();
            rewards.deliver(
                    recipientId,
                    recipientName,
                    killer,
                    selector.select(rule),
                    location,
                    Map.of(
                            "player", recipientName,
                            "mob", rule.mobId(),
                            "location", formatLocation(location)));
        });
    }

    private BossDropAction actionFor(UUID entityId) {
        return bossDropAction.apply(entityId).orElse(BossDropAction.LEGACY);
    }

    private static int randomBetween(int minimum, int maximum) {
        return minimum == maximum ? minimum : ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }

    private static String formatLocation(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }
}
