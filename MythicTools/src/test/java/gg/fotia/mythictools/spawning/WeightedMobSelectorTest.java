package gg.fotia.mythictools.spawning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedMobSelectorTest {

    @Test
    void selectsMemberAtExactWeightBoundaries() {
        MobGroup group = new MobGroup("undead", 3, 5, List.of(
                new MobGroupMember("skeleton", "SkeletalMinion", 70),
                new MobGroupMember("zombie", "AngrySludge", 30)));

        assertEquals("SkeletalMinion", WeightedMobSelector.selectAt(group, 0).mobId());
        assertEquals("SkeletalMinion", WeightedMobSelector.selectAt(group, 69).mobId());
        assertEquals("AngrySludge", WeightedMobSelector.selectAt(group, 70).mobId());
        assertEquals("AngrySludge", WeightedMobSelector.selectAt(group, 99).mobId());
    }

    @Test
    void selectsWithLongWeightTicketsBeyondIntegerRange() {
        MobGroup group = new MobGroup("large", 1, 1, List.of(
                new MobGroupMember("first", "MobA", 3_000_000_000L),
                new MobGroupMember("second", "MobB", 4_000_000_000L)));

        assertEquals(7_000_000_000L, group.totalWeight());
        assertEquals("MobA", WeightedMobSelector.selectAt(group, 2_999_999_999L).mobId());
        assertEquals("MobB", WeightedMobSelector.selectAt(group, 3_000_000_000L).mobId());
        assertEquals("MobB", WeightedMobSelector.selectAt(group, 6_999_999_999L).mobId());
    }

    @Test
    void validatesAmountAndPositiveWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobGroup("empty", 1, 1, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MobGroupMember("bad", "Mob", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MobGroup("bad", 3, 2,
                        List.of(new MobGroupMember("mob", "Mob", 1))));
        assertThrows(IllegalArgumentException.class,
                () -> new MobGroup("weight-overflow", 1, 1, List.of(
                        new MobGroupMember("first", "MobA", Long.MAX_VALUE),
                        new MobGroupMember("second", "MobB", 1L))));
    }
}
