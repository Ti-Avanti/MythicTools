package gg.fotia.mythictools.gui.summoning;

import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.config.ConfigIoService;
import gg.fotia.mythictools.gui.ChatInputManager;
import gg.fotia.mythictools.gui.GuiTemplate;
import gg.fotia.mythictools.gui.GuiTemplateRepository;
import gg.fotia.mythictools.summoning.SummonService;
import gg.fotia.mythictools.summoning.config.SummonNumbers;
import gg.fotia.mythictools.summoning.config.SummonRepository;
import gg.fotia.mythictools.text.MessageRenderer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;

/** 召唤编辑器的导航、库存事件与草稿持久化；字段行为由独立控制器负责。 */
public final class SummonGui implements Listener, AutoCloseable {
    record Entry(String name, String value, Consumer<ClickType> action) { }
    final MythicToolsPlugin plugin;
    final SummonService service;
    final MessageRenderer messages;
    final ChatInputManager input;
    final Map<UUID, SummonDraft> drafts = new HashMap<>();
    final SummonEditor editor;
    final SummonFieldEditor fields;
    private final SummonDraftStore store;
    private final GuiTemplate template;
    private final String defaultItem;
    private boolean closed;

    public SummonGui(MythicToolsPlugin plugin, SummonService service, MessageRenderer messages,
                     ChatInputManager input, GuiTemplateRepository templates) {
        this.plugin = plugin; this.service = service; this.messages = messages; this.input = input;
        this.template = templates.get("summon-editor");
        this.store = new SummonDraftStore(plugin, service);
        this.editor = new SummonEditor(this); this.fields = new SummonFieldEditor(this);
        try (var stream = plugin.getResource("summoning/items/example.yml")) {
            defaultItem = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    public void open(Player player) {
        SummonDraft draft = drafts.get(player.getUniqueId());
        if (draft != null) editor.open(player, draft); else list(player, false, 0, "");
    }
    Entry action(Player player, String key, Runnable action) { return new Entry(label(player, "actions." + key), "", click -> action.run()); }
    String label(Player player, String key) { return messages.text(player, "summon.gui." + key); }
    String format(Player player, String key, Map<String, ?> values) {
        String value = label(player, key);
        for (var entry : values.entrySet()) value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        return value;
    }
    void page(Player player, String title, List<Entry> entries, int requested, Map<Character, Entry> footer) {
        if (closed || !player.isOnline()) return;
        int size = template.slots('e').size();
        int page = Math.max(0, Math.min(requested, Math.max(0, (entries.size() - 1) / size)));
        SummonMenu holder = new SummonMenu();
        holder.inventory = messages.platform().createInventory(holder, template.size(),
                template.title(player, Map.of("title", title, "page", page + 1)));
        for (int index = 0; index < size && index + page * size < entries.size(); index++) {
            put(player, holder, template.slots('e').get(index), 'e', entries.get(index + page * size));
        }
        Map<Character, Entry> controls = new HashMap<>(footer);
        if (page > 0) controls.put('p', action(player, "previous", () -> page(player, title, entries, page - 1, footer)));
        if ((page + 1) * size < entries.size()) controls.put('n', action(player, "next", () -> page(player, title, entries, page + 1, footer)));
        controls.forEach((symbol, entry) -> template.slots(symbol).forEach(slot -> put(player, holder, slot, symbol, entry)));
        player.openInventory(holder.inventory);
    }
    private void put(Player player, SummonMenu menu, int slot, char symbol, Entry entry) {
        menu.inventory.setItem(slot, template.item(symbol, player, Map.of("name", entry.name(), "value", entry.value(), "hint", label(player, "hint"))));
        menu.actions.put(slot, entry.action());
    }
    void ask(Player player, String field, Consumer<String> accept, Runnable cancel) {
        input.begin(player, field, text -> {
            if (closed || !player.hasPermission("mythictools.admin")) return;
            try { accept.accept(text); } catch (RuntimeException error) { service.report(player, error); cancel.run(); }
        }, cancel);
    }
    void choose(Player player, String title, List<String> values, Consumer<String> select, Runnable back) {
        selector(player, title, values, select, back, "");
    }
    private void selector(Player player, String title, List<String> values, Consumer<String> select, Runnable back, String search) {
        List<Entry> entries = values.stream().filter(id -> id.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .map(id -> new Entry(choiceLabel(player, id), id, click -> select.accept(id))).toList();
        page(player, title, entries, 0, Map.of('b', action(player, "back", back),
                'f', action(player, "search", () -> ask(player, label(player, "actions.search"),
                        text -> selector(player, title, values, select, back, text.equals("-") ? "" : text), back))));
    }
    void list(Player player, boolean effect, int page, String filter) {
        var repository = service.repository();
        List<Entry> entries = new ArrayList<>();
        for (String id : repository.ids(effect)) {
            if (!id.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) continue;
            String value = effect ? String.join(", ", repository.references(id)) : describe(player, repository.get(id));
            entries.add(new Entry(effect ? choiceLabel(player, id) : id, value, click -> {
                SummonDraft draft = new SummonDraft(id, effect, repository.source(effect, id), repository.source(effect, id));
                drafts.put(player.getUniqueId(), draft); editor.open(player, draft);
            }));
        }
        page(player, label(player, effect ? "presets" : "list"), entries, page, Map.of(
                'b', action(player, "back", () -> plugin.adminGui().openMain(player)),
                'c', action(player, "new", () -> create(player, effect)),
                'v', action(player, effect ? "items" : "presets", () -> list(player, !effect, 0, "")),
                'f', action(player, "search", () -> ask(player, label(player, "actions.search"),
                        text -> list(player, effect, 0, text.equals("-") ? "" : text), () -> list(player, effect, page, filter)))));
    }
    private String describe(Player player, gg.fotia.mythictools.summoning.config.SummonDefinition definition) {
        String status = definition.enabled() ? label(player, "enabled") : label(player, "disabled");
        try { service.validate(definition); } catch (RuntimeException error) {
            status = messages.containsText(player, error.getMessage()) ? messages.text(player, error.getMessage()) : error.getMessage();
        }
        return format(player, "summary", Map.of("boss", definition.bossId(), "seconds", definition.windupTicks() / 20.0,
                "effects", definition.layers().stream().map(layer -> layer.preset()).toList(), "type", definition.visual().type(), "status", status));
    }

    private String choiceLabel(Player player, String id) {
        if (id.isEmpty()) return label(player, "none");
        String key = "summon.gui.preset-names." + id;
        return messages.containsText(player, key) ? messages.text(player, key) + " · " + id : id;
    }
    private void create(Player player, boolean effect) {
        ask(player, label(player, "id"), value -> {
            String id = SummonNumbers.id(value);
            if (service.repository().source(effect, id) != null) throw new IllegalArgumentException("summon.error.duplicate");
            String source = effect ? service.repository().source(true, service.repository().ids(true).stream().findFirst().orElse("arcane")) : defaultItem;
            if (effect && source == null) source = "shape: arcane\n";
            SummonDraft draft = new SummonDraft(id, effect, null, source);
            draft.yaml.set("deleted", null);
            drafts.put(player.getUniqueId(), draft);
            if (!effect) {
                draft.yaml.set("enabled", false);
                choose(player, label(player, "boss"), plugin.bossRepository().bossIds().stream().sorted().toList(), boss -> {
                    draft.yaml.set("boss-id", boss); editor.open(player, draft);
                }, () -> editor.open(player, draft));
            } else editor.open(player, draft);
        }, () -> list(player, effect, 0, ""));
    }
    void copy(Player player, SummonDraft original) {
        ask(player, label(player, "id"), id -> {
            SummonNumbers.id(id);
            if (service.repository().source(original.effect, id) != null) throw new IllegalArgumentException("summon.error.duplicate");
            SummonDraft copy = new SummonDraft(id, original.effect, null, original.yaml.saveToString());
            drafts.put(player.getUniqueId(), copy); editor.open(player, copy);
        }, () -> editor.open(player, original));
    }
    void save(Player player, SummonDraft draft, boolean deleting) {
        if (draft.saving) return;
        if (deleting) draft.yaml.set("deleted", true);
        draft.saving = true;
        store.save(draft).whenComplete((saved, error) -> plugin.configurationIo().onMain(() -> {
            draft.saving = false;
            if (closed || !player.isOnline()) return null;
            if (error != null) {
                if (deleting) draft.yaml.set("deleted", null);
                service.report(player, ConfigIoService.cause(error)); editor.open(player, draft);
            } else {
                messages.send(player, "common.saved", Map.of()); drafts.remove(player.getUniqueId());
                if (deleting) list(player, draft.effect, 0, "");
                else {
                    String source = service.repository().source(draft.effect, draft.id);
                    SummonDraft fresh = new SummonDraft(draft.id, draft.effect, source, source);
                    drafts.put(player.getUniqueId(), fresh); editor.open(player, fresh);
                }
            }
            return null;
        }));
    }
    void confirm(Player player, String action, Runnable accept, Runnable back) {
        page(player, action, List.of(action(player, "confirm", accept), action(player, "back", back)), 0, Map.of());
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SummonMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("mythictools.admin")) return;
        SummonDraft draft = drafts.get(player.getUniqueId());
        if (draft != null && draft.saving) return;
        var action = menu.actions.get(event.getRawSlot());
        if (action != null) try { action.accept(event.getClick()); } catch (RuntimeException | LinkageError error) { service.report(player, error); }
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SummonMenu) event.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { drafts.remove(event.getPlayer().getUniqueId()); }
    public void close() {
        closed = true; drafts.clear();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof SummonMenu) player.closeInventory();
    }
}
