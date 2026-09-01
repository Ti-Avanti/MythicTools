package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RewardEntryDraftTest {
    @Test
    void isolatesChangesUntilTheDedicatedRewardEditorSaves() {
        ItemStack diamond = clonedStack();
        ItemStack emerald = clonedStack();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries.reward.type", "item");
        yaml.set("entries.reward.item", diamond);
        yaml.set("entries.reward.weight", 10);
        yaml.set("entries.reward.rarity", "common");

        RewardEntryDraft draft = RewardEntryDraft.load(yaml, "entries.reward");
        draft.set("weight", 4_000_000_000L);
        draft.item(emerald);

        assertEquals(10, yaml.getInt("entries.reward.weight"));
        assertSame(diamond, yaml.getItemStack("entries.reward.item"));

        draft.applyTo(yaml, "entries.reward");

        assertEquals(4_000_000_000L, yaml.getLong("entries.reward.weight"));
        assertSame(emerald, yaml.getItemStack("entries.reward.item"));
    }

    @Test
    void listsOnlyEditableMetadataFields() {
        ItemStack sword = clonedStack();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries.reward.type", "item");
        yaml.set("entries.reward.item", sword);
        yaml.set("entries.reward.weight", 100);
        yaml.set("entries.reward.display.zh_CN", "奖励");

        RewardEntryDraft draft = RewardEntryDraft.load(yaml, "entries.reward");

        assertEquals("item", draft.type());
        assertSame(sword, draft.item());
        assertTrueContains(draft.fields(), "weight");
        assertTrueContains(draft.fields(), "display.zh_CN");
        assertFalse(draft.fields().contains("type"));
        assertFalse(draft.fields().stream().anyMatch(field -> field.startsWith("item")));
    }

    @Test
    void createsCompleteDefaultsForBothRewardTypes() {
        RewardEntryDraft item = RewardEntryDraft.create("item", "custom-tier");
        RewardEntryDraft command = RewardEntryDraft.create("command", "legendary");

        assertEquals(100L, item.value("weight"));
        assertEquals("weighted", item.value("grant-mode"));
        assertEquals("ground", item.value("delivery"));
        assertEquals("custom-tier", item.value("rarity"));
        assertEquals("legendary", command.value("rarity"));
        assertEquals("console", command.value("executor"));
        assertEquals("say Reward for {player}", command.value("command"));
    }

    @Test
    void hidesWeightWhenRewardIsGuaranteedForFirstDefeat() {
        RewardEntryDraft draft = RewardEntryDraft.create("item", "common");

        draft.set("grant-mode", "first-defeat");

        assertTrueContains(draft.fields(), "grant-mode");
        assertFalse(draft.fields().contains("weight"));
        draft.set("grant-mode", "weighted");
        assertTrueContains(draft.fields(), "weight");
    }

    @Test
    void suppliesEditableWeightWhenFirstDefeatRewardReturnsToWeightedMode() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries.reward.type", "command");
        yaml.set("entries.reward.grant-mode", "first-defeat");
        yaml.set("entries.reward.command", "say first");

        RewardEntryDraft draft = RewardEntryDraft.load(yaml, "entries.reward");
        draft.set("grant-mode", "weighted");

        assertEquals(100L, draft.value("weight"));
        assertTrueContains(draft.fields(), "weight");
    }

    @Test
    void refusesToCreateRewardWithoutConfiguredRarity() {
        assertThrows(IllegalArgumentException.class,
                () -> RewardEntryDraft.create("item", "  "));
    }

    @Test
    void normalizesLegacyTypeAndRewritesPortableMaterialFallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries.reward.type", "ITEM");
        yaml.set("entries.reward.item", clonedStack());
        yaml.set("entries.reward.material", "EMERALD");
        yaml.set("entries.reward.weight", 10);

        RewardEntryDraft draft = RewardEntryDraft.load(yaml, "entries.reward");

        assertEquals("item", draft.type());
        assertFalse(draft.fields().contains("material"));
        draft.applyTo(yaml, "entries.reward");
        assertEquals("STONE", yaml.getString("entries.reward.material"));
    }

    @Test
    void rejectsUnknownRewardType() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries.reward.type", "mystery");

        assertThrows(IllegalArgumentException.class,
                () -> RewardEntryDraft.load(yaml, "entries.reward"));
    }

    @Test
    void normalizesLoadedAndEditedWeightToLong() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries.reward.type", "command");
        yaml.set("entries.reward.weight", 10);

        RewardEntryDraft draft = RewardEntryDraft.load(yaml, "entries.reward");
        assertInstanceOf(Long.class, draft.value("weight"));
        assertEquals(10L, draft.weight());

        draft.set("weight", 4_000_000_000L);
        assertInstanceOf(Long.class, draft.value("weight"));
        assertEquals(4_000_000_000L, draft.weight());
    }

    @Test
    void suppliesEditableFiniteOptionDefaultsForLegacyEntries() {
        YamlConfiguration itemYaml = new YamlConfiguration();
        itemYaml.set("entries.reward.type", "item");
        RewardEntryDraft item = RewardEntryDraft.load(itemYaml, "entries.reward");

        YamlConfiguration commandYaml = new YamlConfiguration();
        commandYaml.set("entries.reward.type", "command");
        RewardEntryDraft command = RewardEntryDraft.load(commandYaml, "entries.reward");

        assertEquals("ground", item.value("delivery"));
        assertTrueContains(item.fields(), "delivery");
        assertEquals("console", command.value("executor"));
        assertTrueContains(command.fields(), "executor");
    }

    private static void assertTrueContains(java.util.List<String> values, String expected) {
        org.junit.jupiter.api.Assertions.assertTrue(values.contains(expected),
                () -> "Expected " + expected + " in " + values);
    }

    private static ItemStack clonedStack() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.clone()).thenReturn(stack);
        when(stack.getType()).thenReturn(Material.STONE);
        return stack;
    }
}
