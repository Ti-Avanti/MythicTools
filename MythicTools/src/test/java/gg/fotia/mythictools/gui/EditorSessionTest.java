package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EditorSessionTest {

    @Test
    void onlyNewEmptyDropGroupIsDiscardable() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("entries");
        EditorSession session = new EditorSession(
                AdminType.DROP_GROUP, "draft", new File("draft.yml"), "", yaml);

        assertFalse(session.isDiscardableEmptyDropGroup());

        session.markNewlyCreated();
        assertTrue(session.isDiscardableEmptyDropGroup());

        yaml.set("entries.reward.type", "item");
        assertFalse(session.isDiscardableEmptyDropGroup());
    }

    @Test
    void newNonDropTargetIsNeverDiscardableAsDropGroup() {
        EditorSession session = new EditorSession(
                AdminType.BOSS, "draft", new File("draft.yml"), "", new YamlConfiguration());

        session.markNewlyCreated();

        assertFalse(session.isDiscardableEmptyDropGroup());
    }

    @Test
    void mergesOnlyItsOwnSharedFileNodeWhenAnotherRuleChanged() {
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.set("rules.mine.enabled", false);
        loaded.set("rules.other.enabled", false);
        EditorSession session = new EditorSession(
                AdminType.BIOME_RULE, "mine", new File("biomes.yml"), "rules.mine", loaded);
        session.yaml.set("rules.mine.enabled", true);

        YamlConfiguration latest = new YamlConfiguration();
        latest.set("rules.mine.enabled", false);
        latest.set("rules.other.enabled", true);

        assertTrue(session.isTargetUnchanged(latest));
        YamlConfiguration merged = session.mergeInto(latest);
        assertTrue(merged.getBoolean("rules.mine.enabled"));
        assertTrue(merged.getBoolean("rules.other.enabled"));
    }

    @Test
    void detectsConflictWhenTheSameTargetChanged() {
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.set("rules.mine.level", 1);
        EditorSession session = new EditorSession(
                AdminType.BIOME_RULE, "mine", new File("biomes.yml"), "rules.mine", loaded);

        YamlConfiguration latest = new YamlConfiguration();
        latest.set("rules.mine.level", 2);

        assertFalse(session.isTargetUnchanged(latest));
    }

    @Test
    void newlyCreatedUnchangedMobGroupIsDiscardableUntilFirstSave() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("amount.min", 1);
        yaml.set("amount.max", 1);
        yaml.set("members.default.mob", "Mob");
        yaml.set("members.default.weight", 1);
        EditorSession session = new EditorSession(
                AdminType.MOB_GROUP, "draft", new File("draft.yml"), "", yaml);

        session.markNewlyCreated();

        assertTrue(session.isDiscardableNewMobGroup(yaml));
        yaml.set("members.default.weight", 2);
        assertFalse(session.isDiscardableNewMobGroup(yaml));
    }

    @Test
    void newlyCreatedMobGroupRemainsDiscardableAfterCancellingCategoryChanges() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("amount.min", 1);
        yaml.set("amount.max", 1);
        yaml.set("members.default.mob", "Mob");
        yaml.set("members.default.weight", 1);
        EditorSession original = new EditorSession(
                AdminType.MOB_GROUP, "draft", new File("draft.yml"), "", yaml);
        original.markNewlyCreated();

        EditorSession refreshed = new EditorSession(
                AdminType.MOB_GROUP, "draft", new File("draft.yml"), "", yaml);
        refreshed.inheritCreationDraft(original);

        assertTrue(refreshed.isDiscardableNewMobGroup(yaml));
    }

    @Test
    void everyNewUnchangedTargetIsDiscardableButExistingTargetsAreNot() {
        for (AdminType type : AdminType.values()) {
            String rootPath = type == AdminType.BIOME_RULE ? "rules.draft" : "";
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set(rootPath.isEmpty() ? "enabled" : rootPath + ".enabled", false);
            EditorSession existing = new EditorSession(
                    type, "draft", new File("draft.yml"), rootPath, yaml);

            assertFalse(existing.isDiscardableNewTarget(yaml), type + " 的已有配置不能删除");

            existing.markNewlyCreated();
            assertTrue(existing.isDiscardableNewTarget(yaml), type + " 的未修改新草稿应可删除");
        }
    }

    @Test
    void newTargetIsNotDiscardableAfterItsPersistedNodeChanges() {
        YamlConfiguration baseline = new YamlConfiguration();
        baseline.set("rules.draft.enabled", false);
        EditorSession session = new EditorSession(
                AdminType.BIOME_RULE, "draft", new File("biomes.yml"), "rules.draft", baseline);
        session.markNewlyCreated();

        YamlConfiguration saved = new YamlConfiguration();
        saved.set("rules.draft.enabled", true);

        assertFalse(session.isDiscardableNewTarget(saved));
    }
}
