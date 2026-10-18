package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.boss.*;
import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.runtime.*;
import gg.fotia.mythictools.summoning.config.*;
import gg.fotia.mythictools.summoning.visual.*;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.*;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SummonServiceTest {
    private final UUID playerId = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final BossManager bosses = inlineMock(BossManager.class);
    private final BossRepository bossConfigs = inlineMock(BossRepository.class);
    private final SummonRepository repository = inlineMock(SummonRepository.class);
    private final SummonItems items = inlineMock(SummonItems.class);
    private final SummonRefunds refunds = inlineMock(SummonRefunds.class);
    private final VisualProviders visuals = inlineMock(VisualProviders.class);
    private final VisualHandle handle = mock(VisualHandle.class);
    private final World world = mock(World.class);
    private final ItemStack held = new ItemStack(Material.NETHER_STAR, 2);
    private final ManualScheduler scheduler = new ManualScheduler();
    private MockedStatic<Bukkit> bukkit;
    private SummonService service;
    private SummonDefinition definition;

    @BeforeEach void setup() {
        bukkit = mockStatic(Bukkit.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
        var plugin = inlineMock(MythicToolsPlugin.class);
        var settings = inlineMock(PluginSettings.class);
        when(settings.overrideBoss()).thenReturn(true);
        when(plugin.settings()).thenReturn(settings);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var messages = inlineMock(MessageRenderer.class);
        when(messages.renderKey(any(Player.class), anyString(), anyMap())).thenReturn(Component.empty());
        when(bossConfigs.boss("boss")).thenReturn(inlineMock(BossConfig.class));
        when(bosses.summon(eq("boss"), any())).thenReturn(Optional.of(new BossSpawnResult(UUID.randomUUID(), UUID.randomUUID())));
        when(visuals.create(any(), any(), any(), anyDouble())).thenReturn(handle);
        when(items.identify(any())).thenReturn("stone");
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItemInMainHand()).thenReturn(held);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getPlayers()).thenReturn(List.of());
        var border = mock(WorldBorder.class);
        when(border.isInside(any())).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        Block air = mock(Block.class), floor = mock(Block.class);
        when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> ((Location) call.getArgument(0)).getY() < 64 ? floor : air);
        var visual = VisualConfig.read(SummonRepository.yaml("visual:\n  duration-seconds: 0\n"));
        definition = new SummonDefinition("stone", true, "boss", held, 1, 60000, "", Set.of(), "player-location", 6,
                1, 4, 0.1, 3, false, "none", List.of(), visual, List.of(), Map.of());
        when(repository.get("stone")).thenReturn(definition);
        service = new SummonService(plugin, bosses, bossConfigs, repository,
                new SummonSettings(2, 32, 1000, 10, 3, 1), items, refunds, messages, visuals, new OwnedTasks(scheduler));
        service.start();
    }
    @AfterEach void cleanup() {
        try {
            if (service != null) service.close();
        } finally {
            if (bukkit != null) bukkit.close();
        }
    }

    @Test void doubleClickCannotConsumeTwiceOrCreateBossBeforeWindup() {
        assertTrue(service.tryUse(player));
        assertTrue(service.tryUse(player));
        assertEquals(1, held.getAmount());
        assertEquals(1, service.pendingCount());
        verify(bosses, never()).summon(anyString(), any());
        scheduler.tick(); scheduler.tick();
        verify(bosses, never()).summon(anyString(), any());
        scheduler.tick();
        verify(bosses).summon(eq("boss"), any());
        assertEquals(0, service.pendingCount());
        verify(refunds, never()).give(any(), any());
        service.tryUse(player);
        assertEquals(1, held.getAmount());
        verify(bosses, times(1)).summon(anyString(), any());
    }
    @Test void interruptionRefundsOnceAndStopsFutureSpawn() {
        service.tryUse(player);
        service.interrupt(playerId, false);
        service.interrupt(playerId, false);
        scheduler.tick(); scheduler.tick(); scheduler.tick();
        verify(refunds, times(1)).give(eq(playerId), argThat(stack -> stack != null && stack.getAmount() == 1));
        verify(bosses, never()).summon(anyString(), any());
        assertEquals(0, service.sessionCount());
    }
    @Test void walkingOutsideCastingOriginCancelsAndRefunds() {
        service.tryUse(player);
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 0));
        scheduler.tick();
        verify(refunds).give(eq(playerId), argThat(stack -> stack != null && stack.getAmount() == 1));
        assertEquals(0, service.pendingCount());
    }
    @Test void previewNeverReservesItemsOrCreatesBoss() {
        service.preview(player, definition, () -> { });
        scheduler.tick(); scheduler.tick(); scheduler.tick(); scheduler.tick();
        assertEquals(2, held.getAmount());
        verify(bosses, never()).summon(anyString(), any());
        verify(refunds, never()).give(any(), any());
        assertEquals(0, service.pendingCount());
    }
    @Test void failedSpawnRefundsAndDoesNotStartCooldown() {
        when(bosses.summon(anyString(), any())).thenReturn(Optional.empty());
        service.tryUse(player);
        scheduler.tick(); scheduler.tick(); scheduler.tick();
        verify(refunds).give(eq(playerId), argThat(stack -> stack != null && stack.getAmount() == 1));
        assertEquals(0, service.pendingCount());
        service.tryUse(player);
        assertEquals(1, service.pendingCount());
    }
    @Test void damageDoesNotInterruptWhenDisabled() {
        service.tryUse(player);
        service.interrupt(playerId, true);
        assertEquals(1, service.pendingCount());
    }

    private static final class ManualScheduler implements TaskScheduler {
        Runnable repeating;
        public TaskHandle execute(Runnable command) { command.run(); return () -> { }; }
        public TaskHandle later(Runnable command, long delayTicks) { return () -> { }; }
        public TaskHandle repeating(Runnable command, long delayTicks, long periodTicks) {
            repeating = command; return () -> repeating = null;
        }
        void tick() { if (repeating != null) repeating.run(); }
    }

    private static <T> T inlineMock(Class<T> type) {
        return org.mockito.Mockito.mock(type, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
    }
}
