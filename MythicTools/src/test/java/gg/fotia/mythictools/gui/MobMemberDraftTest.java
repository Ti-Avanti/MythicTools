package gg.fotia.mythictools.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MobMemberDraftTest {

    @Test
    void isolatesAndAppliesMemberChanges() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("members.skeleton.mob", "SkeletalMinion");
        yaml.set("members.skeleton.weight", 70);

        MobMemberDraft draft = MobMemberDraft.load(yaml, "members.skeleton");
        draft.mobId("AngrySludge");
        draft.weight(4_000_000_000L);

        assertEquals("SkeletalMinion", yaml.getString("members.skeleton.mob"));
        draft.applyTo(yaml, "members.skeleton");
        assertEquals("AngrySludge", yaml.getString("members.skeleton.mob"));
        assertEquals(4_000_000_000L, yaml.getLong("members.skeleton.weight"));
    }

    @Test
    void loadsAndKeepsLongWeights() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("members.skeleton.mob", "SkeletalMinion");
        yaml.set("members.skeleton.weight", 4_000_000_000L);

        MobMemberDraft draft = MobMemberDraft.load(yaml, "members.skeleton");

        assertEquals(4_000_000_000L, draft.weight());
    }
}
