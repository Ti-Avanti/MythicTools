package gg.fotia.mythictools.config;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import gg.fotia.mythictools.boss.BossRepository;
import gg.fotia.mythictools.reward.RewardRepository;
import gg.fotia.mythictools.spawning.SpawningRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorySafetyLimitsTest {

    @TempDir
    Path dataFolder;

    private RepositorySnapshotStore store;
    private RepositoryReloadCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        World world = mock(World.class);
        RepositoryLoadContext context = new RepositoryLoadContext(
                dataFolder.toFile(), Logger.getLogger("RepositorySafetyLimitsTest"),
                ignored -> true, ignored -> world);
        PluginSettings settings = new PluginSettings(
                "zh_CN", true, Map.of(), true, true, true,
                100L, 12, 12, 32, true, "<?>", "data.db", false,
                SafetyLimits.defaults());
        store = new RepositorySnapshotStore();
        RewardRepository rewards = new RewardRepository(context, store, settings.safetyLimits());
        SpawningRepository spawning = new SpawningRepository(context, settings, store);
        BossRepository bosses = new BossRepository(context, settings, rewards, store);
        coordinator = new RepositoryReloadCoordinator(store, rewards, spawning, bosses);
        write("rarities.yml", """
                rarities:
                  common:
                    display: Common
                    color: WHITE
                    priority: 1
                """);
        write("spawning/biomes.yml", "rules: {}\n");
    }

    @Test
    void acceptsExactRewardSafetyBoundaries() throws Exception {
        write("drops/groups/commands.yml", commandGroup("1000000", "1", "16"));
        write("drops/groups/items.yml", itemGroup("1000000", "1", "2304"));
        write("drops/mobs/TestMob.yml", """
                mob-id: TestMob
                max-drops: 16
                experience:
                  min: 100000
                  max: 100000
                groups:
                  - id: commands
                    weight: 1000000
                    min-amount: 0
                    max-amount: 16
                """);
        writeBoss("commands", "1", "3", "1");

        coordinator.reload(ConfigLoadMode.STRICT);
    }

    @Test
    void rejectsRewardOverflowDescendingAndFractionalNumbersWithoutPublishing() throws Exception {
        write("drops/groups/old.yml", commandGroup("1", "1", "1"));
        writeBoss("old", "1", "3", "1");
        coordinator.reload(ConfigLoadMode.STRICT);
        RepositorySnapshots oldBundle = store.current();

        String[] invalidGroups = {
                commandGroup("1.5", "1", "1"),
                commandGroup("1000001", "1", "1"),
                commandGroup("1", "2", "1"),
                commandGroup("1", "1", "17"),
                itemGroup("1", "1", "2305")
        };
        for (int index = 0; index < invalidGroups.length; index++) {
            Path file = write("drops/groups/invalid-" + index + ".yml", invalidGroups[index]);
            assertThrows(ConfigLoadException.class, () -> coordinator.reload(ConfigLoadMode.STRICT));
            assertSame(oldBundle, store.current());
            Files.delete(file);
        }

        String[] invalidMobs = {
                mobRule("17", "0", "0", "1", "0", "1"),
                mobRule("1", "0", "100001", "1", "0", "1"),
                mobRule("1", "2", "1", "1", "0", "1"),
                mobRule("1", "0", "0", "1.5", "0", "1"),
                mobRule("1", "0", "0", "1000001", "0", "1"),
                mobRule("1", "0", "0", "1", "2", "1")
        };
        for (String invalidMob : invalidMobs) {
            Path file = write("drops/mobs/invalid.yml", invalidMob);
            assertThrows(ConfigLoadException.class, () -> coordinator.reload(ConfigLoadMode.STRICT));
            assertSame(oldBundle, store.current());
            Files.delete(file);
        }
    }

    @Test
    void rejectsBiomeRuleClampsAndInvalidOrdering() throws Exception {
        String[] invalidFields = {
                "chance: .NaN",
                "interval-seconds: 0",
                "min-distance: 0",
                "min-distance: 20\n    max-distance: 10",
                "height:\n      min: 100\n      max: 50",
                "light:\n      min: 16\n      max: 16",
                "amount:\n      min: 0\n      max: 1",
                "limits:\n      nearby: 0\n      global: 1\n      radius: 1",
                "level: 0.0",
                "despawn-seconds: -1"
        };
        for (String invalid : invalidFields) {
            write("spawning/biomes.yml", biomeRule(invalid));
            assertThrows(ConfigLoadException.class, () -> coordinator.reload(ConfigLoadMode.STRICT), invalid);
        }
    }

    @Test
    void rejectsFixedPointClamps() throws Exception {
        String[] invalidFields = {
                "interval-seconds: 0",
                "amount:\n  min: 0\n  max: 1",
                "amount:\n  min: 2\n  max: 1",
                "max-alive: 0",
                "level: 0.0",
                "despawn-seconds: -1"
        };
        for (String invalid : invalidFields) {
            Path file = write("spawning/points/invalid.yml", spawnPoint(invalid));
            assertThrows(ConfigLoadException.class, () -> coordinator.reload(ConfigLoadMode.STRICT), invalid);
            Files.delete(file);
        }
    }

    @Test
    void rejectsBossRecipientCopyRankAndPerRecipientSelectionLimits() throws Exception {
        write("drops/groups/rewards.yml", commandGroup("1", "1", "1"));
        String[] invalidBosses = {
                boss("rewards", "1", "21", "1"),
                boss("rewards", "0", "3", "1"),
                boss("rewards", "1", "3", "17"),
                boss("rewards", "1", "3", "16", "16", "1")
        };
        for (String invalidBoss : invalidBosses) {
            Path file = write("bosses/invalid.yml", invalidBoss);
            assertThrows(ConfigLoadException.class, () -> coordinator.reload(ConfigLoadMode.STRICT));
            Files.delete(file);
        }
    }

    private String commandGroup(String weight, String minimum, String maximum) {
        return """
                entries:
                  reward:
                    type: command
                    weight: %s
                    min-amount: %s
                    max-amount: %s
                    rarity: common
                    command: say test
                """.formatted(weight, minimum, maximum);
    }

    private String itemGroup(String weight, String minimum, String maximum) {
        return """
                entries:
                  reward:
                    type: item
                    weight: %s
                    min-amount: %s
                    max-amount: %s
                    rarity: common
                    material: DIAMOND
                """.formatted(weight, minimum, maximum);
    }

    private String mobRule(
            String maxDrops,
            String minExperience,
            String maxExperience,
            String weight,
            String minimum,
            String maximum) {
        return """
                mob-id: TestMob
                max-drops: %s
                experience:
                  min: %s
                  max: %s
                groups:
                  - id: old
                    weight: %s
                    min-amount: %s
                    max-amount: %s
                """.formatted(maxDrops, minExperience, maxExperience, weight, minimum, maximum);
    }

    private String biomeRule(String invalidField) {
        return """
                rules:
                  invalid:
                    enabled: true
                    mob: TestMob
                    biomes: [FOREST]
                    chance: 0.5
                    interval-seconds: 30
                    min-distance: 12
                    max-distance: 32
                    height:
                      min: -64
                      max: 320
                    light:
                      min: 0
                      max: 15
                    amount:
                      min: 1
                      max: 1
                    limits:
                      nearby: 1
                      global: 1
                      radius: 1
                    level: 1.0
                    despawn-seconds: 0
                    %s
                """.formatted(invalidField.replace("\n", "\n    "));
    }

    private String spawnPoint(String invalidField) {
        return """
                enabled: true
                mob: TestMob
                location:
                  world: world
                  x: 0
                  y: 64
                  z: 0
                interval-seconds: 30
                amount:
                  min: 1
                  max: 1
                max-alive: 1
                level: 1.0
                despawn-seconds: 0
                %s
                """.formatted(invalidField);
    }

    private void writeBoss(String group, String rank, String maxRecipients, String copies) throws Exception {
        write("bosses/test.yml", boss(group, rank, maxRecipients, copies));
    }

    private String boss(String group, String rank, String maxRecipients, String... copies) {
        StringBuilder groups = new StringBuilder();
        for (String copy : copies) {
            groups.append("""
                            - group: %s
                              copies: %s
                    """.formatted(group, copy));
        }
        return """
                display: Test
                phase-mode: death-respawn
                phases:
                  - mob: TestMob
                    level: 1.0
                rewards:
                  damage-ranking:
                    enabled: true
                    max-recipients: %s
                    ranks:
                      '%s':
                %s
                  killer:
                    enabled: false
                """.formatted(maxRecipients, rank, groups.toString().indent(8));
    }

    private Path write(String relative, String content) throws Exception {
        Path path = dataFolder.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
