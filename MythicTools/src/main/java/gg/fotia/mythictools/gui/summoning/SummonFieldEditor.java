package gg.fotia.mythictools.gui.summoning;

import gg.fotia.mythictools.summoning.config.SummonRepository;
import gg.fotia.mythictools.summoning.effect.EffectShape;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** 类型化字段编辑与动态资源选择。 */
final class SummonFieldEditor {
    private final SummonGui gui;
    SummonFieldEditor(SummonGui gui) { this.gui = gui; }

    void open(Player player, SummonDraft draft, String group, String prefix, Runnable back) {
        Runnable reopen = () -> open(player, draft, group, prefix, back);
        List<SummonGui.Entry> entries = new ArrayList<>();
        List<SummonEditorFields.Field> fields = new ArrayList<>(SummonEditorFields.group(group));
        if (draft.effect) {
            fields.removeIf(field -> field.path().equals("preset"));
            fields.add(0, SummonEditorFields.Field.select("shape", Arrays.stream(EffectShape.values()).map(EffectShape::key).toArray(String[]::new)));
        }
        String visualType = draft.yaml.getString("visual.type", "none");
        for (var field : fields) {
            if (group.equals("visual")) {
                if (visualType.equals("none") && !field.path().equals("visual.type")) continue;
                if (visualType.equals("craftengine") && field.path().startsWith("visual.animations")) continue;
                if (!visualType.equals("craftengine") && Set.of("visual.rotation", "visual.float-height").contains(field.path())) continue;
            }
            String labelKey = field.path().startsWith("sounds.") ? "sound." + field.path().substring(field.path().lastIndexOf('.') + 1) : field.path();
            Object value = effective(draft, prefix, field.path());
            String shown = value == null ? gui.label(player, "none") : String.valueOf(value);
            entries.add(new SummonGui.Entry(gui.label(player, "fields." + labelKey), shown,
                    click -> edit(player, draft, prefix, field, click, reopen)));
        }
        Map<Character, SummonGui.Entry> footer = new HashMap<>(gui.editor.footer(player, draft));
        footer.put('b', gui.action(player, "back", back));
        footer.put('v', gui.action(player, "preview", () -> gui.editor.preview(player, draft, reopen)));
        gui.page(player, draft.id + " · " + gui.label(player, "groups." + group), entries, 0, footer);
    }
    private Object effective(SummonDraft draft, String prefix, String path) {
        if (draft.yaml.contains(prefix + path)) return draft.yaml.get(prefix + path);
        if (!prefix.isEmpty()) {
            String source = gui.service.repository().source(true, draft.yaml.getString(prefix + "preset", "arcane"));
            return SummonRepository.yaml(source).get(path);
        }
        return null;
    }
    private void edit(Player player, SummonDraft draft, String prefix, SummonEditorFields.Field field, ClickType click, Runnable reopen) {
        String path = prefix + field.path();
        ConsumerValue setter = value -> {
            if (path.startsWith("item.")) draft.yaml.set("item-copy", null);
            if (path.equals("visual.type") && !Objects.equals(value, draft.yaml.get(path))) {
                draft.yaml.set("visual.id", ""); draft.yaml.set("visual.animations", null);
            }
            draft.yaml.set(path, value); reopen.run();
        };
        List<String> choices = options(draft, field);
        if (choices != null) {
            gui.choose(player, gui.label(player, "fields." + field.path()), choices, setter::set, reopen); return;
        }
        Object value = effective(draft, prefix, field.path());
        if (field.kind() == SummonEditorFields.Kind.BOOLEAN) { setter.set(!Boolean.TRUE.equals(value)); return; }
        if (field.kind() == SummonEditorFields.Kind.NUMBER && click != ClickType.MIDDLE && click != ClickType.DROP) {
            double current = value instanceof Number number ? number.doubleValue() : field.min();
            double next = Math.max(field.min(), Math.min(field.max(), current + field.step() * (click.isRightClick() ? -1 : 1) * (click.isShiftClick() ? 10 : 1)));
            setter.set(numeric(next, field)); return;
        }
        gui.ask(player, gui.label(player, "fields." + field.path()), text -> {
            Object result = switch (field.kind()) {
                case NUMBER -> numeric(Double.parseDouble(text), field);
                case LIST -> text.equals("-") ? List.of() : Arrays.stream(text.split("\\|", -1)).map(String::trim).toList();
                default -> text.equals("-") ? "" : text;
            };
            if (field.path().equals("primary") || field.path().equals("secondary")) gg.fotia.mythictools.summoning.effect.EffectLayer.color(String.valueOf(result));
            setter.set(result);
        }, reopen);
    }
    private Number numeric(double number, SummonEditorFields.Field field) {
        if (!Double.isFinite(number) || number < field.min() || number > field.max()) throw new IllegalArgumentException(field.min() + " .. " + field.max());
        if (field.step() >= 1 && Math.rint(number) != number) throw new IllegalArgumentException("summon.error.integer");
        if (field.step() >= 1) return (int) number;
        return Math.round(number * 1000) / 1000.0;
    }
    private List<String> options(SummonDraft draft, SummonEditorFields.Field field) {
        String path = field.path();
        if (path.equals("boss-id")) return gui.plugin.bossRepository().bossIds().stream().sorted().toList();
        if (path.equals("preset")) return gui.service.repository().ids(true);
        if (path.equals("item.material")) return Arrays.stream(Material.values()).filter(m -> m.isItem() && !m.isAir()).map(Enum::name).sorted().toList();
        if (path.equals("visual.id")) return gui.service.visuals().ids(draft.yaml.getString("visual.type", "none"));
        if (path.startsWith("visual.animations.")) {
            List<String> choices = new ArrayList<>(List.of(""));
            choices.addAll(gui.service.visuals().animations(draft.yaml.getString("visual.type", "none"), draft.yaml.getString("visual.id", "")));
            return choices;
        }
        return field.kind() == SummonEditorFields.Kind.SELECT ? field.choices() : null;
    }
    @FunctionalInterface private interface ConsumerValue { void set(Object value); }
}
