package gg.fotia.mythictools.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gg.fotia.mythictools.reward.CommandExecutorType;
import gg.fotia.mythictools.reward.DropGroup;
import gg.fotia.mythictools.reward.RewardDelivery;
import gg.fotia.mythictools.reward.RewardEntry;
import gg.fotia.mythictools.reward.RewardSnapshot;
import gg.fotia.mythictools.reward.RewardType;
import gg.fotia.mythictools.spawning.SpawnPoint;
import gg.fotia.mythictools.spawning.SpawningSnapshot;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RepositorySnapshotsTest {

    @Test
    void snapshotsCloneMutableItemStacksAndLocationsAtBothBoundaries() {
        ItemStack sourceItem = new ItemStack(Material.DIAMOND, 1);
        RewardEntry entry = new RewardEntry(
                "item", RewardType.ITEM, 1, 1, 1, "common",
                Map.of(), Map.of(), sourceItem, RewardDelivery.INVENTORY, null, CommandExecutorType.CONSOLE);
        RewardSnapshot rewards = new RewardSnapshot(Map.of(), Map.of("group", new DropGroup("group", List.of(entry))), Map.of());

        Location sourceLocation = new Location(null, 1.0, 2.0, 3.0);
        SpawningSnapshot spawning = new SpawningSnapshot(
                Map.of(),
                Map.of("point", new SpawnPoint(
                        "point", "mob", null, sourceLocation, 60L, 1, 1, 1, 1.0, false, 0L, true)),
                Map.of());

        sourceItem.setAmount(32);
        sourceLocation.setX(99.0);
        ItemStack returnedItem = rewards.group("group").orElseThrow().entries().get(0).item();
        Location returnedLocation = spawning.spawnPoint("point").location();
        returnedItem.setAmount(16);
        returnedLocation.setX(88.0);

        assertEquals(1, rewards.group("group").orElseThrow().entries().get(0).item().getAmount());
        assertEquals(1.0, spawning.spawnPoint("point").location().getX());
    }
}
