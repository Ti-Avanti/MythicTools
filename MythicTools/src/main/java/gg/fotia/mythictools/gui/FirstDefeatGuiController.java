package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.reward.FirstDefeatRewardOption;
import gg.fotia.mythictools.reward.RewardEntry;
import gg.fotia.mythictools.reward.RewardEntryRef;
import gg.fotia.mythictools.reward.RewardType;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Boss 与普通 MythicMob 共用的首次击败设置、奖励列表和选择器。 */
final class FirstDefeatGuiController {
    private static final String KEY_SEPARATOR = "\u001f";

    private final GuiContext context;

    FirstDefeatGuiController(GuiContext context) {
        this.context = context;
    }

    void openMenu(Player player, EditorSession session) {
        GuiTemplate template = context.screens.template("first-defeat-menu");
        FirstDefeatSettings.ensureDefaults(session.yaml, session.type);
        GuiHolder holder = new GuiHolder(
                GuiView.FIRST_DEFEAT_MENU, session.type, session.id, 0);
        Map<String, Object> variables = Map.of(
                "id", session.id,
                "enabled", EditorValueFormats.formatValue(
                        FirstDefeatSettings.enabled(session.yaml, session.type)),
                "scope", localizedOption(player, "scope", FirstDefeatSettings.scope(session.yaml, session.type)),
                "recipient", localizedOption(
                        player, "recipient", FirstDefeatSettings.recipient(session.yaml, session.type)),
                "amount", FirstDefeatSettings.entries(session.yaml, session.type).size());
        Inventory inventory = context.screens.createInventory(holder, template, player, variables);
        for (char symbol : new char[]{'e', 's', 'l', 'b'}) {
            context.screens.fillStatic(inventory, template, symbol, player, variables);
        }
        if (session.type == AdminType.BOSS
                && FirstDefeatSettings.scope(session.yaml, session.type).equals("player")) {
            context.screens.fillStatic(inventory, template, 'r', player, variables);
        }
        player.openInventory(inventory);
    }

    void handleMenu(Player player, GuiHolder holder, int slot) {
        EditorSession session = session(player, holder);
        if (session == null) {
            return;
        }
        GuiTemplate template = context.screens.template("first-defeat-menu");
        if (template.slots('e').contains(slot)) {
            boolean enabled = !FirstDefeatSettings.enabled(session.yaml, session.type);
            if (enabled && FirstDefeatSettings.entries(session.yaml, session.type).isEmpty()) {
                context.messages.send(player, "gui.first-defeat.empty", Map.of());
                openMenu(player, session);
                return;
            }
            save(player, session,
                    yaml -> FirstDefeatSettings.setEnabled(yaml, session.type, enabled),
                    refreshed -> openMenu(player, refreshed));
        } else if (template.slots('s').contains(slot)) {
            String scope = FirstDefeatSettings.scope(session.yaml, session.type)
                    .equals("player") ? "server" : "player";
            save(player, session,
                    yaml -> FirstDefeatSettings.setScope(yaml, session.type, scope),
                    refreshed -> openMenu(player, refreshed));
        } else if (template.slots('r').contains(slot) && session.type == AdminType.BOSS) {
            String recipient = FirstDefeatSettings.recipient(session.yaml, session.type)
                    .equals("killer") ? "participants" : "killer";
            save(player, session,
                    yaml -> FirstDefeatSettings.setRecipient(yaml, session.type, recipient),
                    refreshed -> openMenu(player, refreshed));
        } else if (template.slots('l').contains(slot)) {
            openRewardList(player, session, 0);
        } else if (template.slots('b').contains(slot)) {
            context.core.openCategory(player, session);
        }
    }

    void openRewardList(Player player, EditorSession session, int requestedPage) {
        GuiTemplate template = context.screens.template("first-defeat-reward-list");
        List<RewardEntryRef> entries = FirstDefeatSettings.entries(session.yaml, session.type);
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (entries.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(
                GuiView.FIRST_DEFEAT_REWARD_LIST, session.type, session.id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", session.id, "page", page + 1));
        Map<RewardEntryRef, FirstDefeatRewardOption> options = optionsByReference();
        int start = page * pageSize;
        for (int index = 0; index < template.slots('e').size() && start + index < entries.size(); index++) {
            RewardEntryRef reference = entries.get(start + index);
            int contentSlot = template.slots('e').get(index);
            inventory.setItem(contentSlot, optionItem(
                    template, player, options.get(reference), reference, true));
            holder.valuesBySlot.put(contentSlot, encode(reference));
        }
        for (char symbol : new char[]{'a', 'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleRewardList(
            Player player,
            GuiHolder holder,
            int slot,
            boolean deleteClick) {
        EditorSession session = session(player, holder);
        if (session == null) {
            return;
        }
        String encoded = holder.valuesBySlot.get(slot);
        if (encoded != null && deleteClick) {
            RewardEntryRef reference = decode(encoded);
            save(player, session, yaml -> {
                FirstDefeatSettings.remove(yaml, session.type, reference);
                if (FirstDefeatSettings.entries(yaml, session.type).isEmpty()) {
                    FirstDefeatSettings.setEnabled(yaml, session.type, false);
                }
            }, refreshed -> openRewardList(player, refreshed, holder.page));
            return;
        }
        GuiTemplate template = context.screens.template("first-defeat-reward-list");
        if (template.slots('a').contains(slot)) {
            openRewardSelector(player, session, 0);
        } else if (template.slots('p').contains(slot)) {
            openRewardList(player, session, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openRewardList(player, session, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            openMenu(player, session);
        }
    }

    private void openRewardSelector(Player player, EditorSession session, int requestedPage) {
        GuiTemplate template = context.screens.template("first-defeat-reward-selector");
        List<RewardEntryRef> selected = FirstDefeatSettings.entries(session.yaml, session.type);
        List<FirstDefeatRewardOption> options = context.firstDefeatRewardOptions.get().stream()
                .filter(option -> !selected.contains(option.reference()))
                .toList();
        int pageSize = template.slots('e').size();
        int maximumPage = Math.max(0, (options.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maximumPage, requestedPage));
        GuiHolder holder = new GuiHolder(
                GuiView.FIRST_DEFEAT_REWARD_SELECTOR, session.type, session.id, page);
        Inventory inventory = context.screens.createInventory(holder, template, player,
                Map.of("id", session.id, "page", page + 1));
        int start = page * pageSize;
        for (int index = 0; index < template.slots('e').size() && start + index < options.size(); index++) {
            FirstDefeatRewardOption option = options.get(start + index);
            int contentSlot = template.slots('e').get(index);
            inventory.setItem(contentSlot, optionItem(
                    template, player, option, option.reference(), false));
            holder.valuesBySlot.put(contentSlot, encode(option.reference()));
        }
        for (char symbol : new char[]{'p', 'b', 'n'}) {
            context.screens.fillStatic(inventory, template, symbol, player, Map.of());
        }
        player.openInventory(inventory);
    }

    void handleRewardSelector(Player player, GuiHolder holder, int slot) {
        EditorSession session = session(player, holder);
        if (session == null) {
            return;
        }
        String encoded = holder.valuesBySlot.get(slot);
        if (encoded != null) {
            RewardEntryRef reference = decode(encoded);
            save(player, session,
                    yaml -> FirstDefeatSettings.add(yaml, session.type, reference),
                    refreshed -> openRewardList(player, refreshed, 0));
            return;
        }
        GuiTemplate template = context.screens.template("first-defeat-reward-selector");
        if (template.slots('p').contains(slot)) {
            openRewardSelector(player, session, holder.page - 1);
        } else if (template.slots('n').contains(slot)) {
            openRewardSelector(player, session, holder.page + 1);
        } else if (template.slots('b').contains(slot)) {
            openRewardList(player, session, 0);
        }
    }

    private ItemStack optionItem(
            GuiTemplate template,
            Player player,
            FirstDefeatRewardOption option,
            RewardEntryRef reference,
            boolean configured) {
        RewardEntry entry = option == null ? null : option.entry();
        String locale = context.playerLocale.apply(player);
        Map<String, Object> variables = Map.of(
                "group", reference.groupId(),
                "entry", reference.entryId(),
                "reward", entry == null ? reference.entryId() : entry.display(locale));
        ItemStack item = template.item('e', player, variables);
        if (entry != null) {
            item.setType(entry.type() == RewardType.ITEM && entry.item() != null
                    ? entry.item().getType() : Material.COMMAND_BLOCK);
        }
        return item;
    }

    private Map<RewardEntryRef, FirstDefeatRewardOption> optionsByReference() {
        Map<RewardEntryRef, FirstDefeatRewardOption> result = new HashMap<>();
        context.firstDefeatRewardOptions.get().forEach(option -> result.put(option.reference(), option));
        return result;
    }

    private EditorSession session(Player player, GuiHolder holder) {
        EditorSession session = context.sessions.editors.get(player.getUniqueId());
        if (session == null || (session.type != AdminType.BOSS && session.type != AdminType.MOB_DROP)) {
            context.core.openList(player, holder.type, 0);
            return null;
        }
        return session;
    }

    private void save(
            Player player,
            EditorSession session,
            Consumer<YamlConfiguration> mutation,
            Consumer<EditorSession> success) {
        try {
            EditorSession working = session.withYaml(context.sessionService.copyYaml(session.yaml));
            mutation.accept(working.yaml);
            context.sessionService.persistSession(player, working, () -> {
                context.messages.send(player, "common.saved", Map.of());
                context.sessionService.refreshSession(player, session, success);
            }, exception -> {
                context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
                context.sessionService.refreshAfterFailure(player, session, success);
            });
        } catch (IOException | RuntimeException exception) {
            context.messages.send(player, "common.config-error", Map.of("reason", exception.getMessage()));
            context.sessionService.refreshAfterFailure(player, session, success);
        }
    }

    private String localizedOption(Player player, String field, String value) {
        return context.messages.text(player, "gui.first-defeat." + field + "." + value);
    }

    private static String encode(RewardEntryRef reference) {
        return reference.groupId() + KEY_SEPARATOR + reference.entryId();
    }

    private static RewardEntryRef decode(String encoded) {
        String[] parts = encoded.split(KEY_SEPARATOR, 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("首次击败奖励引用损坏");
        }
        return new RewardEntryRef(parts[0], parts[1]);
    }
}
