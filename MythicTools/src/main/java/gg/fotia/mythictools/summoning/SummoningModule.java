package gg.fotia.mythictools.summoning;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.boss.BossManager;
import gg.fotia.mythictools.boss.BossRepository;
import gg.fotia.mythictools.gui.ChatInputManager;
import gg.fotia.mythictools.gui.GuiTemplateRepository;
import gg.fotia.mythictools.gui.summoning.SummonGui;
import gg.fotia.mythictools.item.ItemFactory;
import gg.fotia.mythictools.storage.PendingRewardQueue;
import gg.fotia.mythictools.summoning.config.*;
import gg.fotia.mythictools.summoning.visual.VisualProviders;
import gg.fotia.mythictools.text.MessageRenderer;
import org.bukkit.configuration.ConfigurationSection;

/** 运行时装配入口，不在构造阶段注册监听器或启动任务。 */
public final class SummoningModule implements AutoCloseable {
    private final SummonService service;
    private final SummonListener listener;
    private final SummonGui gui;
    public SummoningModule(MythicToolsPlugin plugin, ConfigurationSection config, BossManager bosses,
                           BossRepository bossConfigs, PendingRewardQueue pending, MessageRenderer messages,
                           ItemFactory items, ChatInputManager chat, GuiTemplateRepository templates) {
        var settings = SummonSettings.read(config);
        var repository = new SummonRepository(plugin.getDataFolder(), items, settings.maxLayers());
        repository.publish(repository.prepare());
        service = new SummonService(plugin, bosses, bossConfigs, repository, settings, new SummonItems(plugin),
                new SummonRefunds(pending, plugin.getLogger()), messages, new VisualProviders(plugin));
        listener = new SummonListener(service);
        gui = new SummonGui(plugin, service, messages, chat, templates);
    }
    public SummonService service() { return service; }
    public SummonListener listener() { return listener; }
    public SummonGui gui() { return gui; }
    public void close() { try { service.close(); } finally { gui.close(); } }
}
