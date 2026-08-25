package gg.fotia.mythictools.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import gg.fotia.mythictools.boss.BossRepository;
import gg.fotia.mythictools.reward.RewardRepository;
import gg.fotia.mythictools.spawning.SpawningRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryReloadCoordinatorTest {

    @TempDir
    Path dataFolder;

    private RepositorySnapshotStore store;
    private RewardRepository rewards;
    private SpawningRepository spawning;
    private BossRepository bosses;
    private RepositoryReloadCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        RepositoryLoadContext context = new RepositoryLoadContext(
                dataFolder.toFile(), Logger.getLogger("RepositoryReloadCoordinatorTest"),
                ignored -> true, ignored -> null);
        PluginSettings settings = new PluginSettings(
                "zh_CN", true, Map.of(), true, true, true,
                100L, 12, 12, 32, true, "<?>", "data.db", false);

        store = new RepositorySnapshotStore();
        rewards = new RewardRepository(context, store);
        spawning = new SpawningRepository(context, settings, store);
        bosses = new BossRepository(context, settings, rewards, store);
        coordinator = new RepositoryReloadCoordinator(store, rewards, spawning, bosses);
        writeRequiredRoots();
    }

    @Test
    void strictSpawningFailureKeepsExactOldBundleAndCandidateRewardsUnpublished() throws Exception {
        writeGroup("old", "say old");
        writeBoss("old");
        coordinator.reload(ConfigLoadMode.STRICT);
        RepositorySnapshots oldBundle = store.current();

        Files.delete(rewardGroup("old"));
        writeGroup("new", "say new");
        Files.writeString(dataFolder.resolve("spawning/biomes.yml"), "rules: [", StandardCharsets.UTF_8);

        assertThrows(ConfigLoadException.class, () -> coordinator.reload(ConfigLoadMode.STRICT));

        assertSame(oldBundle, store.current());
        assertTrue(rewards.group("old").isPresent());
        assertTrue(rewards.group("new").isEmpty());
    }

    @Test
    void strictSuccessPublishesAllRepositoriesInOneBundleSwap() throws Exception {
        writeGroup("new", "say new");
        writeMobGroup("pack");
        writeBoss("new");
        RepositorySnapshots before = store.current();

        ConfigLoadReport report = coordinator.reload(ConfigLoadMode.STRICT);

        assertFalse(report.blocks(ConfigLoadMode.STRICT));
        assertNotSame(before, store.current());
        assertTrue(rewards.group("new").isPresent());
        assertTrue(spawning.mobGroupIds().contains("pack"));
        assertTrue(bosses.bossIds().contains("test"));
    }

    @Test
    void bossesValidateAgainstCandidateRewardsFromTheSameReload() throws Exception {
        writeGroup("old", "say old");
        writeBoss("old");
        coordinator.reload(ConfigLoadMode.STRICT);
        RepositorySnapshots oldBundle = store.current();

        Files.delete(rewardGroup("old"));
        writeGroup("new", "say new");
        assertThrows(ConfigLoadException.class, () -> coordinator.reload(ConfigLoadMode.STRICT));
        assertSame(oldBundle, store.current());

        writeBoss("new");
        coordinator.reload(ConfigLoadMode.STRICT);
        assertTrue(rewards.group("new").isPresent());
        assertEquals("new", bosses.boss("test").rewards().killerRewards().get(0).groupId());
    }

    @Test
    void startupLenientIsolatesBadOptionalFileAndPublishesValidFilesWithDiagnostics() throws Exception {
        writeGroup("good", "say good");
        Files.writeString(rewardGroup("bad"), "entries: [", StandardCharsets.UTF_8);
        writeBoss("good");

        ConfigLoadReport report = coordinator.reload(ConfigLoadMode.STARTUP_LENIENT);

        assertFalse(report.blocks(ConfigLoadMode.STARTUP_LENIENT));
        assertTrue(report.hasErrors());
        assertTrue(rewards.group("good").isPresent());
        assertTrue(rewards.group("bad").isEmpty());
        ConfigDiagnostic diagnostic = report.diagnostics().stream()
                .filter(value -> value.sourcePath().endsWith("bad.yml"))
                .findFirst()
                .orElseThrow();
        assertEquals(ConfigDomain.REWARDS, diagnostic.domain());
        assertFalse(diagnostic.yamlPath().isBlank());
        assertEquals(ConfigProblemCode.YAML_SYNTAX, diagnostic.problemCode());
    }

    @Test
    void prepareNeverMutatesPublishedSnapshot() throws Exception {
        writeGroup("old", "say old");
        writeBoss("old");
        coordinator.reload(ConfigLoadMode.STRICT);
        RepositorySnapshots oldBundle = store.current();

        Files.delete(rewardGroup("old"));
        writeGroup("candidate", "say candidate");
        PreparedSnapshot<gg.fotia.mythictools.reward.RewardSnapshot> prepared =
                rewards.prepare(ConfigLoadMode.STRICT);

        assertSame(oldBundle, store.current());
        assertTrue(rewards.group("old").isPresent());
        assertTrue(rewards.group("candidate").isEmpty());
        assertTrue(prepared.snapshot().group("candidate").isPresent());
    }

    @Test
    void rollbackRestoresDiskAndKeepsExactRuntimeSnapshotAfterStrictFailure() throws Exception {
        writeGroup("old", "say old");
        writeBoss("old");
        coordinator.reload(ConfigLoadMode.STRICT);
        RepositorySnapshots oldBundle = store.current();
        Path target = rewardGroup("old");
        byte[] original = Files.readAllBytes(target);

        assertThrows(ConfigLoadException.class, () -> ReloadRollback.mutate(
                target,
                () -> Files.writeString(target, "entries: []\n", StandardCharsets.UTF_8),
                () -> coordinator.reload(ConfigLoadMode.STRICT)));

        assertSame(oldBundle, store.current());
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(target)));
        assertTrue(rewards.group("old").isPresent());
    }

    private void writeRequiredRoots() throws Exception {
        write("rarities.yml", """
                rarities:
                  common:
                    display: Common
                    color: WHITE
                    priority: 1
                """);
        write("spawning/biomes.yml", "rules: {}\n");
    }

    private void writeGroup(String id, String command) throws Exception {
        write("drops/groups/" + id + ".yml", """
                entries:
                  reward:
                    type: command
                    weight: 1
                    min-amount: 1
                    max-amount: 1
                    rarity: common
                    command: %s
                """.formatted(command));
    }

    private void writeMobGroup(String id) throws Exception {
        write("spawning/groups/" + id + ".yml", """
                amount:
                  min: 1
                  max: 1
                members:
                  member:
                    mob: TestMob
                    weight: 1
                """);
    }

    private void writeBoss(String group) throws Exception {
        write("bosses/test.yml", """
                display: Test
                phase-mode: death-respawn
                phases:
                  - mob: TestMob
                    level: 1.0
                rewards:
                  damage-ranking:
                    enabled: false
                  killer:
                    enabled: true
                    groups:
                      - group: %s
                        copies: 1
                """.formatted(group));
    }

    private Path rewardGroup(String id) throws Exception {
        Path path = dataFolder.resolve("drops/groups/" + id + ".yml");
        Files.createDirectories(path.getParent());
        return path;
    }

    private void write(String relative, String content) throws Exception {
        Path path = dataFolder.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
