package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.summoning.config.SummonDefinition;
import gg.fotia.mythictools.summoning.visual.VisualHandle;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/** 所有可变字段仅由主线程访问。 */
final class SummonRitual {
    final UUID id = UUID.randomUUID();
    final UUID player;
    final SummonDefinition definition;
    final Location origin;
    final Location castingOrigin;
    final boolean preview;
    final Runnable returnToEditor;
    ItemStack escrow;
    VisualHandle visual = VisualHandle.NONE;
    UUID fight;
    int age;
    int successAge = -1;
    int lastCountdown = -1;
    int endingAge = -1;
    boolean visualStarted;
    boolean chargeStarted;
    boolean idleStarted;

    SummonRitual(UUID player, SummonDefinition definition, Location origin, Location castingOrigin,
                 ItemStack escrow, boolean preview, Runnable returnToEditor) {
        this.player = player;
        this.definition = definition;
        this.origin = origin.clone();
        this.castingOrigin = castingOrigin.clone();
        this.escrow = escrow;
        this.preview = preview;
        this.returnToEditor = returnToEditor;
    }
    boolean pending() { return successAge < 0; }
}
