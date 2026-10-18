package gg.fotia.mythictools.summoning;

import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

public final class SummonListener implements Listener {
    private final SummonService service;
    public SummonListener(SummonService service) { this.service = service; }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        var player = event.getPlayer();
        if (player.isSneaking() && service.previewing(player.getUniqueId())) {
            event.setCancelled(true); service.stopPreview(player.getUniqueId(), true); return;
        }
        // 空气右键常被 Bukkit 预先标为取消；方块交互则尊重保护插件的拒绝。
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY) return;
        if (service.tryUse(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player && event.getFinalDamage() > 0) service.interrupt(player.getUniqueId(), true);
    }
    @EventHandler public void death(PlayerDeathEvent event) { service.interrupt(event.getEntity().getUniqueId(), false); }
    @EventHandler public void quit(PlayerQuitEvent event) { service.interrupt(event.getPlayer().getUniqueId(), false); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { service.interrupt(event.getPlayer().getUniqueId(), false); }
}
