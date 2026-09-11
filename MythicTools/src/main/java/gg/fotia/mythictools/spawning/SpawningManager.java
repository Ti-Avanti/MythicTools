package gg.fotia.mythictools.spawning;

import gg.fotia.mythictools.integration.MythicMobGateway;
import gg.fotia.mythictools.runtime.BukkitTaskScheduler;
import gg.fotia.mythictools.runtime.OwnedTasks;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 执行群系随机尝试与固定刷怪点定时任务。 */
public final class SpawningManager implements Listener {
    private final SpawningConfigView repository;
    private final MythicMobGateway mythicMobs;
    private final SpawnLocationFinder locationFinder;
    private final long checkPeriodTicks;
    private final boolean enabled;
    private final Map<AttemptKey, Long> lastAttempts = new HashMap<>();
    private final Map<String, Set<UUID>> biomeEntities = new HashMap<>();
    private final Map<String, Set<UUID>> pointEntities = new HashMap<>();
    private final Map<UUID, String> entityPoints = new HashMap<>();
    private final Map<String, Long> nextPointSpawns = new HashMap<>();
    private final OwnedTasks tasks;
    private final SpawnBatchQueue spawnBatches;
    private final Map<String, NearbyMobIndex> nearbyIndices = new HashMap<>();
    private final Map<UUID, String> entityRules = new HashMap<>();
    private final Map<UUID, gg.fotia.mythictools.runtime.TaskHandle> despawnTasks = new HashMap<>();
    private long lifecycleGeneration;

    public SpawningManager(
            JavaPlugin plugin,
            SpawningConfigView repository,
            MythicMobGateway mythicMobs,
            SpawnLocationFinder locationFinder,
            long checkPeriodTicks,
            boolean enabled) {
        this(repository, mythicMobs, locationFinder, checkPeriodTicks, enabled,
                new OwnedTasks(new BukkitTaskScheduler(plugin)));
    }

    public SpawningManager(
            SpawningConfigView repository,
            MythicMobGateway mythicMobs,
            SpawnLocationFinder locationFinder,
            long checkPeriodTicks,
            boolean enabled,
            OwnedTasks tasks) {
        this(repository, mythicMobs, locationFinder, checkPeriodTicks, enabled, tasks, 32);
    }

    public SpawningManager(
            SpawningConfigView repository, MythicMobGateway mythicMobs, SpawnLocationFinder locationFinder,
            long checkPeriodTicks, boolean enabled, OwnedTasks tasks, int maxSpawnsPerTick) {
        this.repository = repository;
        this.mythicMobs = mythicMobs;
        this.locationFinder = locationFinder;
        this.checkPeriodTicks = checkPeriodTicks;
        this.enabled = enabled;
        this.tasks = tasks;
        this.spawnBatches = new SpawnBatchQueue(maxSpawnsPerTick);
    }

    /** 启动所有刷怪任务。 */
    public void start() {
        stop();
        if (!enabled) {
            return;
        }
        tasks.beginGeneration();
        long now = System.currentTimeMillis();
        repository.spawnPoints().forEach(point ->
                nextPointSpawns.put(point.id(), now + Duration.ofSeconds(point.intervalSeconds()).toMillis()));
        tasks.repeating(() -> {
            nearbyIndices.clear();
            spawnBatches.tick();
        }, 1L, 1L);
        tasks.repeating(this::tickPoints, 20L, 20L);
        tasks.repeating(this::tickBiomes, checkPeriodTicks, checkPeriodTicks);
    }

    /** 停止任务并清理运行时计时，同时移除本插件生成且仍存活的实体。 */
    public void stop() {
        lifecycleGeneration++;
        tasks.cancelAll();
        spawnBatches.clear();
        nearbyIndices.clear();
        entityRules.clear();
        despawnTasks.clear();
        lastAttempts.clear();
        nextPointSpawns.clear();
        Set<UUID> tracked = new HashSet<>();
        biomeEntities.values().forEach(tracked::addAll);
        pointEntities.values().forEach(tracked::addAll);
        biomeEntities.clear();
        pointEntities.clear();
        entityPoints.clear();
        tracked.forEach(uuid -> mythicMobs.activeMob(uuid).ifPresent(ActiveMob::remove));
    }

    /** 获取固定刷怪点当前存活数。 */
    public int aliveAtPoint(String pointId) {
        cleanup(pointEntities.computeIfAbsent(pointId, ignored -> new HashSet<>()));
        return pointEntities.get(pointId).size();
    }

    /** 返回本管理器当前仍存活的去重实体数。 */
    public int trackedEntityCount() {
        cleanupAll();
        Set<UUID> tracked = new HashSet<>();
        biomeEntities.values().forEach(tracked::addAll);
        pointEntities.values().forEach(tracked::addAll);
        return tracked.size();
    }

    /** 获取固定刷怪点下次刷新剩余秒数。 */
    public long secondsUntilNext(String pointId) {
        Long next = nextPointSpawns.get(pointId);
        return next == null ? -1L : Math.max(0L, (next - System.currentTimeMillis() + 999L) / 1000L);
    }

    /** 判断刷怪点是否已启用且存在。 */
    public boolean isPointEnabled(String pointId) {
        SpawnPoint point = repository.spawnPoint(pointId);
        return enabled && point != null && point.enabled();
    }

    /** 立即触发一个固定刷怪点；用于管理员操作和黑盒验证。 */
    public boolean triggerPoint(String pointId) {
        SpawnPoint point = repository.spawnPoint(pointId);
        if (!enabled || point == null || !point.enabled() || aliveAtPoint(pointId) >= point.maxAlive()) {
            return false;
        }
        Long previousNext = nextPointSpawns.get(pointId);
        int before = pointEntities.getOrDefault(pointId, Set.of()).size();
        nextPointSpawns.put(pointId, System.currentTimeMillis());
        tickPoints();
        int after = pointEntities.getOrDefault(pointId, Set.of()).size();
        if (after > before || spawnBatches.contains("point:" + pointId)) {
            return true;
        }
        if (previousNext == null) {
            nextPointSpawns.remove(pointId);
        } else {
            nextPointSpawns.put(pointId, previousNext);
        }
        return false;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        var despawn = despawnTasks.remove(entityId);
        if (despawn != null) {
            despawn.cancel();
        }
        String ruleId = entityRules.remove(entityId);
        if (ruleId != null) {
            Set<UUID> entities = biomeEntities.get(ruleId);
            if (entities != null) {
                entities.remove(entityId);
            }
            nearbyIndices.remove(ruleId);
        }
        String pointId = entityPoints.remove(entityId);
        if (pointId == null) {
            return;
        }
        Set<UUID> entities = pointEntities.get(pointId);
        if (entities != null) {
            entities.remove(event.getEntity().getUniqueId());
        }
        SpawnPoint point = repository.spawnPoint(pointId);
        if (point != null && point.enabled() && point.spawnOnDeath() && aliveAtPoint(pointId) == 0) {
            nextPointSpawns.put(pointId, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastAttempts.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    private void tickBiomes() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String worldName = player.getWorld().getName();
            Biome biome = player.getLocation().getBlock().getBiome();
            for (BiomeSpawnRule rule : repository.biomeRules(worldName, biome)) {
                AttemptKey key = new AttemptKey(player.getUniqueId(), rule.id());
                long last = lastAttempts.getOrDefault(key, 0L);
                if (now - last < Duration.ofSeconds(rule.intervalSeconds()).toMillis()) {
                    continue;
                }
                lastAttempts.put(key, now);
                if (ThreadLocalRandom.current().nextDouble() > rule.chance()) {
                    continue;
                }
                Set<UUID> global = biomeEntities.computeIfAbsent(rule.id(), ignored -> new HashSet<>());
                if (global.size() >= rule.maxAliveGlobal()) {
                    continue;
                }
                int nearby = nearbyCount(rule, player.getLocation(), global);
                if (nearby >= rule.maxAliveNearby()) {
                    continue;
                }
                locationFinder.find(player, rule).ifPresent(location -> {
                    int nearbyAvailable = rule.maxAliveNearby() - nearby;
                    int amount = Math.min(spawnAmount(rule.mobGroupId(), rule.minAmount(), rule.maxAmount()),
                            Math.min(rule.maxAliveGlobal() - global.size(), nearbyAvailable));
                    Object configuration = repository.identity();
                    spawnBatches.submit("biome:" + rule.id() + ":" + player.getUniqueId(), amount, () -> {
                        if (repository.identity() != configuration || !player.isOnline()
                                || !player.getWorld().equals(location.getWorld())
                                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                                || global.size() >= rule.maxAliveGlobal()
                                || nearbyCount(rule, player.getLocation(), global) >= rule.maxAliveNearby()) {
                            return false;
                        }
                        return spawn(selectedMob(rule.mobId(), rule.mobGroupId()), location,
                                rule.level(), rule.despawnSeconds()).map(entityId -> {
                                    global.add(entityId);
                                    entityRules.put(entityId, rule.id());
                                    var entity = Bukkit.getEntity(entityId);
                                    NearbyMobIndex current = nearbyIndices.get(rule.id());
                                    if (current == null) {
                                        nearbyIndices.put(rule.id(), new NearbyMobIndex(global));
                                    } else {
                                        current.add(entity == null ? location : entity.getLocation());
                                    }
                                    return true;
                                }).orElse(false);
                    });
                });
            }
        }
    }

    private void tickPoints() {
        cleanupAll();
        long now = System.currentTimeMillis();
        for (SpawnPoint point : repository.spawnPoints()) {
            if (!point.enabled() || now < nextPointSpawns.getOrDefault(point.id(), now)) {
                continue;
            }
            Set<UUID> entities = pointEntities.computeIfAbsent(point.id(), ignored -> new HashSet<>());
            int available = point.maxAlive() - entities.size();
            if (available > 0 && point.location().getWorld().isChunkLoaded(point.location().getBlockX() >> 4,
                    point.location().getBlockZ() >> 4)) {
                int amount = Math.min(available,
                        spawnAmount(point.mobGroupId(), point.minAmount(), point.maxAmount()));
                Object configuration = repository.identity();
                Location location = point.location();
                spawnBatches.submit("point:" + point.id(), amount, () -> {
                    if (repository.identity() != configuration || entities.size() >= point.maxAlive()
                            || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                        return false;
                    }
                    return spawn(selectedMob(point.mobId(), point.mobGroupId()), location,
                            point.level(), point.despawnSeconds()).map(uuid -> {
                        entities.add(uuid);
                        entityPoints.put(uuid, point.id());
                        return true;
                    }).orElse(false);
                });
            }
            nextPointSpawns.put(point.id(), now + Duration.ofSeconds(point.intervalSeconds()).toMillis());
        }
    }

    private Optional<UUID> spawn(String mobId, Location location, double level, long despawnSeconds) {
        long generation = lifecycleGeneration;
        Optional<ActiveMob> spawned = mythicMobs.spawn(mobId, location, level);
        if (generation != lifecycleGeneration) {
            spawned.ifPresent(ActiveMob::remove);
            return Optional.empty();
        }
        spawned.ifPresent(activeMob -> {
            if (despawnSeconds > 0) {
                UUID entityId = activeMob.getUniqueId();
                long delay = despawnSeconds > Long.MAX_VALUE / 20L ? Long.MAX_VALUE : despawnSeconds * 20L;
                despawnTasks.put(entityId, tasks.later(() -> {
                    despawnTasks.remove(entityId);
                    mythicMobs.activeMob(entityId).filter(current -> !current.isDead()).ifPresent(ActiveMob::remove);
                }, delay));
            }
        });
        return spawned.map(ActiveMob::getUniqueId);
    }

    private int spawnAmount(String groupId, int fallbackMinimum, int fallbackMaximum) {
        MobGroup group = groupId == null ? null : repository.mobGroup(groupId);
        return group == null
                ? randomBetween(fallbackMinimum, fallbackMaximum)
                : randomBetween(group.minAmount(), group.maxAmount());
    }

    private String selectedMob(String mobId, String groupId) {
        MobGroup group = groupId == null ? null : repository.mobGroup(groupId);
        if (group == null) {
            return mobId;
        }
        return WeightedMobSelector.select(group).mobId();
    }

    private int nearbyCount(BiomeSpawnRule rule, Location location, Set<UUID> entities) {
        return nearbyIndices.computeIfAbsent(rule.id(), ignored -> new NearbyMobIndex(entities))
                .count(location, rule.nearbyRadius(), rule.maxAliveNearby());
    }

    private void cleanupAll() {
        biomeEntities.values().forEach(this::cleanup);
        pointEntities.values().forEach(this::cleanup);
        entityPoints.keySet().removeIf(uuid -> !mythicMobs.isLoadedAndActive(uuid));
        entityRules.keySet().removeIf(uuid -> !mythicMobs.isLoadedAndActive(uuid));
        despawnTasks.entrySet().removeIf(entry -> {
            if (mythicMobs.isLoadedAndActive(entry.getKey())) {
                return false;
            }
            entry.getValue().cancel();
            return true;
        });
    }

    private void cleanup(Set<UUID> entities) {
        entities.removeIf(uuid -> !mythicMobs.isLoadedAndActive(uuid));
    }

    private static int randomBetween(int minimum, int maximum) {
        return minimum == maximum ? minimum
                : (int) ThreadLocalRandom.current().nextLong(minimum, (long) maximum + 1L);
    }

    private record AttemptKey(UUID playerId, String ruleId) {
    }
}
