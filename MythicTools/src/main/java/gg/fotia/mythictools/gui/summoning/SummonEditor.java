package gg.fotia.mythictools.gui.summoning;

import gg.fotia.mythictools.summoning.config.SummonRepository;
import java.util.*;
import org.bukkit.entity.Player;

/** 总览、粒子层及预览页面。 */
final class SummonEditor {
    private final SummonGui gui;
    SummonEditor(SummonGui gui) { this.gui = gui; }

    void open(Player player, SummonDraft draft) {
        if (draft.effect) { gui.fields.open(player, draft, "layer", "", () -> overview(player, draft)); return; }
        overview(player, draft);
    }
    void overview(Player player, SummonDraft draft) {
        List<SummonGui.Entry> entries = new ArrayList<>();
        if (draft.effect) {
            entries.add(new SummonGui.Entry(gui.label(player, "preset-settings"),
                    String.join(", ", gui.service.repository().references(draft.id)),
                    click -> gui.fields.open(player, draft, "layer", "", () -> overview(player, draft))));
        } else {
            for (String group : List.of("item", "rules", "windup", "visual", "messages", "sound-start", "sound-charge", "sound-success", "sound-cancel")) {
                entries.add(new SummonGui.Entry(gui.label(player, "groups." + group), "",
                        click -> gui.fields.open(player, draft, group, "", () -> overview(player, draft))));
            }
            entries.add(new SummonGui.Entry(gui.label(player, "groups.effects"), "", click -> layers(player, draft)));
            entries.add(gui.action(player, "copy-hand", () -> {
                var held = player.getInventory().getItemInMainHand().clone();
                if (held.getType().isAir()) throw new IllegalArgumentException("summon.error.amount");
                held.setAmount(1); draft.yaml.set("item-copy", held); overview(player, draft);
            }));
            entries.add(gui.action(player, "clear-hand", () -> { draft.yaml.set("item-copy", null); overview(player, draft); }));
        }
        entries.add(gui.action(player, "copy", () -> gui.copy(player, draft)));
        gui.page(player, draft.id, entries, 0, footer(player, draft));
    }

    Map<Character, SummonGui.Entry> footer(Player player, SummonDraft draft) {
        Map<Character, SummonGui.Entry> footer = new HashMap<>();
        footer.put('b', gui.action(player, "discard", () -> gui.confirm(player, gui.label(player, "actions.discard"), () -> {
            gui.drafts.remove(player.getUniqueId()); gui.list(player, draft.effect, 0, "");
        }, () -> overview(player, draft))));
        footer.put('s', gui.action(player, "save", () -> gui.save(player, draft, false)));
        footer.put('v', gui.action(player, "preview", () -> preview(player, draft, () -> overview(player, draft))));
        footer.put('x', gui.action(player, "delete", () -> gui.confirm(player, gui.label(player, "actions.delete"),
                () -> gui.save(player, draft, true), () -> overview(player, draft))));
        if (!draft.effect) footer.put('g', gui.action(player, "give", () -> {
            var definition = gui.service.repository().get(draft.id);
            if (definition == null) throw new IllegalArgumentException("summon.error.save-first");
            gui.service.give(player, definition, 1);
            gui.messages.send(player, "summon.given", Map.of("id", draft.id, "amount", 1));
        }));
        return footer;
    }

    void layers(Player player, SummonDraft draft) {
        List<SummonGui.Entry> entries = new ArrayList<>();
        var section = draft.yaml.getConfigurationSection("effects");
        if (section != null) for (String key : section.getKeys(false)) {
            String path = "effects." + key;
            String preset = draft.yaml.getString(path + ".preset", "arcane");
            var defaults = SummonRepository.yaml(gui.service.repository().source(true, preset));
            entries.add(new SummonGui.Entry(key + " · " + preset,
                    gui.format(player, "layer-summary", Map.of("start", draft.yaml.getDouble(path + ".start", defaults.getDouble("start", 0)) * 100,
                            "end", draft.yaml.getDouble(path + ".end", defaults.getDouble("end", 1)) * 100)),
                    click -> layer(player, draft, key)));
        }
        Map<Character, SummonGui.Entry> footer = new HashMap<>(footer(player, draft));
        footer.put('b', gui.action(player, "back", () -> overview(player, draft)));
        footer.put('c', gui.action(player, "add-layer", () -> gui.choose(player, gui.label(player, "presets"),
                gui.service.repository().ids(true), preset -> {
                    var current = draft.yaml.getConfigurationSection("effects");
                    if (current != null && current.getKeys(false).size() >= gui.service.maxLayers()) throw new IllegalArgumentException("summon.error.layers");
                    int next = 1;
                    while (draft.yaml.contains("effects.layer" + next)) next++;
                    draft.yaml.set("effects.layer" + next + ".preset", preset); layers(player, draft);
                }, () -> layers(player, draft))));
        gui.page(player, gui.label(player, "groups.effects"), entries, 0, footer);
    }
    private void layer(Player player, SummonDraft draft, String key) {
        String prefix = "effects." + key;
        List<SummonGui.Entry> entries = List.of(
                gui.action(player, "edit", () -> gui.fields.open(player, draft, "layer", prefix + ".", () -> layer(player, draft, key))),
                gui.action(player, "copy", () -> {
                    var section = draft.yaml.getConfigurationSection("effects");
                    if (section.getKeys(false).size() >= gui.service.maxLayers()) throw new IllegalArgumentException("summon.error.layers");
                    int index = 1; while (draft.yaml.contains("effects.layer" + index)) index++;
                    draft.yaml.createSection("effects.layer" + index, leaves(draft.yaml.getConfigurationSection(prefix)));
                    layers(player, draft);
                }),
                gui.action(player, "move-up", () -> reorder(player, draft, key, -1)),
                gui.action(player, "move-down", () -> reorder(player, draft, key, 1)),
                gui.action(player, "delete", () -> {
                    draft.yaml.set(prefix, null); layers(player, draft);
                }));
        gui.page(player, key, entries, 0, Map.of('b', gui.action(player, "back", () -> layers(player, draft)),
                'v', gui.action(player, "preview", () -> preview(player, draft, () -> layer(player, draft, key)))));
    }
    private void reorder(Player player, SummonDraft draft, String key, int offset) {
        var section = draft.yaml.getConfigurationSection("effects");
        List<String> keys = new ArrayList<>(section.getKeys(false));
        int from = keys.indexOf(key), to = from + offset;
        if (to >= 0 && to < keys.size()) {
            Map<String, Map<String, Object>> values = new LinkedHashMap<>();
            for (String entry : keys) values.put(entry, leaves(section.getConfigurationSection(entry)));
            Collections.swap(keys, from, to); draft.yaml.set("effects", null);
            for (String entry : keys) draft.yaml.createSection("effects." + entry, values.get(entry));
        }
        layers(player, draft);
    }
    void preview(Player player, SummonDraft draft, Runnable back) {
        var yaml = SummonRepository.yaml(draft.yaml.saveToString());
        if (draft.effect) {
            String preset = gui.service.repository().ids(true).stream().findFirst().orElseThrow();
            yaml = SummonRepository.yaml("item:\n  material: NETHER_STAR\nvisual:\n  duration-seconds: 2\n");
            yaml.set("effects.preview.preset", preset);
            for (String path : draft.yaml.getKeys(true)) if (!draft.yaml.isConfigurationSection(path)) {
                yaml.set("effects.preview." + path, draft.yaml.get(path));
            }
        }
        var definition = gui.service.repository().draft(draft.id, yaml);
        gui.service.preview(player, definition, () -> {
            if (player.isOnline() && gui.drafts.get(player.getUniqueId()) == draft
                    && player.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) back.run();
        });
        player.closeInventory();
    }

    private static Map<String, Object> leaves(org.bukkit.configuration.ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        section.getKeys(true).stream().filter(key -> !section.isConfigurationSection(key)).forEach(key -> values.put(key, section.get(key)));
        return values;
    }
}
