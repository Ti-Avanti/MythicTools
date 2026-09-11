package gg.fotia.mythictools.gui;

import gg.fotia.mythictools.boss.BossPhaseMode;
import gg.fotia.mythictools.boss.BossSpawnConfigurationValidator;
import gg.fotia.mythictools.boss.FinalStageLootMode;
import gg.fotia.mythictools.boss.IntermediateStageLootMode;
import gg.fotia.mythictools.config.ConfigValues;
import gg.fotia.mythictools.config.YamlFiles;
import java.io.IOException;
import gg.fotia.mythictools.MythicToolsPlugin;
import gg.fotia.mythictools.config.ConfigIoService;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** 编辑会话的持久化、刷新、回滚与清理。 */
final class EditorSessionService {
    private final GuiContext context;
    private final Set<UUID> saving = new HashSet<>();

    EditorSessionService(GuiContext context) {
        this.context = context;
    }

    /** 读取与保存由配置 I/O 队列执行；完成校验和写盘后才通知调用方更新界面。 */
    void persistSession(Player player, EditorSession session, IoAction success, Consumer<Exception> failure)
            throws IOException {
        MythicToolsPlugin plugin = (MythicToolsPlugin) context.plugin;
        UUID playerId = player.getUniqueId();
        if (saving.contains(playerId)) {
            return;
        }
        EditorSession detached = session.withYaml(copyYaml(session.yaml));
        validateSession(player, detached);
        saving.add(playerId);
        plugin.saveEditedConfiguration(session.file, session.type.domain(), snapshot -> {
            try {
                YamlConfiguration latest = YamlFiles.load(detached.file);
                if (!detached.isTargetUnchanged(latest)) {
                    throw new IllegalStateException(context.messages.text(player, "common.config-conflict"));
                }
                return detached.mergeInto(latest).saveToString();
            } catch (IOException | InvalidConfigurationException exception) {
                throw new IllegalStateException(exception.getMessage(), exception);
            }
        }).whenComplete((snapshot, problem) -> plugin.configurationIo().onMain(() -> {
            saving.remove(playerId);
            if (problem == null) {
                session.markSaved();
                EditorSession current = context.sessions.editors.get(playerId);
                if (current != null) {
                    current.markSaved();
                }
            }
            if (context.closed || !player.isOnline()
                    || !(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder)) {
                cleanupSession(playerId);
                return null;
            }
            if (problem != null) {
                Throwable cause = ConfigIoService.cause(problem);
                if (cause instanceof java.util.ConcurrentModificationException) {
                    cause = new IllegalStateException(context.messages.text(player, "common.config-conflict"));
                }
                failure.accept(cause instanceof Exception exception ? exception : new IllegalStateException(cause));
                return null;
            }
            try {
                YamlFiles.using(snapshot, () -> {
                    success.run();
                    return null;
                });
            } catch (RuntimeException exception) {
                failure.accept(exception);
            }
            return null;
        }));
    }

    private void validateSession(Player player, EditorSession session) {
        if (session.type == AdminType.MOB_GROUP) {
            validateMobGroup(player, session.yaml);
        } else if (session.type == AdminType.BOSS) {
            validateBoss(player, session.yaml);
        }
    }

    boolean isSaving(UUID playerId) {
        return saving.contains(playerId);
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws IOException;
    }

    void refreshSession(Player player, EditorSession previous, Consumer<EditorSession> action)
            throws IOException {
        refreshSession(player, previous, false, action);
    }

    void refreshSession(
            Player player,
            EditorSession previous,
            boolean preserveCreationDraft,
            Consumer<EditorSession> action) throws IOException {
        try {
            EditorSession refreshed = context.targets.loadSession(previous.type, previous.id);
            if (preserveCreationDraft) {
                refreshed.inheritCreationDraft(previous);
            }
            context.sessions.editors.put(player.getUniqueId(), refreshed);
            action.accept(refreshed);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("保存后的配置无法重新读取", exception);
        }
    }

    void refreshAfterFailure(Player player, EditorSession previous, Consumer<EditorSession> action) {
        try {
            refreshSession(player, previous, true, action);
        } catch (IOException refreshException) {
            context.messages.send(player, "common.config-error",
                    Map.of("reason", refreshException.getMessage()));
            resetEditingState(player.getUniqueId());
            context.core.openList(player, previous.type, 0);
        }
    }

    YamlConfiguration copyYaml(YamlConfiguration source) throws IOException {
        YamlConfiguration copy = new YamlConfiguration();
        try {
            copy.loadFromString(source.saveToString());
            return copy;
        } catch (InvalidConfigurationException exception) {
            throw new IOException("无法复制编辑会话", exception);
        }
    }

    void discardNewUnchangedTarget(EditorSession session) throws IOException {
        CreationDraftCleaner.discard(session, context.plugin.getDataFolder());
    }

    void cleanupSession(UUID playerId) {
        if (saving.contains(playerId)) {
            return;
        }
        try {
            GuiSessionStateCleaner.cleanup(
                    playerId,
                    context.sessions.editors,
                    context.sessions.draftMaps(),
                    context.plugin.getDataFolder());
        } catch (IOException exception) {
            context.plugin.getLogger().warning("清理未保存的新建配置失败: " + exception.getMessage());
        }
    }

    void resetEditingState(UUID playerId) {
        context.chatInput.discard(playerId);
        cleanupSession(playerId);
    }

    private void validateMobGroup(Player player, YamlConfiguration yaml) {
        try {
            int minimum = ConfigValues.requireInt(yaml, "amount.min", 1, Integer.MAX_VALUE);
            int maximum = ConfigValues.requireInt(yaml, "amount.max", 1, Integer.MAX_VALUE);
            ConfigValues.requireOrdered(minimum, maximum, "amount.min", "amount.max");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(context.messages.text(player, "gui.mob-group.invalid-amount"));
        }
        ConfigurationSection members = yaml.getConfigurationSection("members");
        if (members == null || members.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException(context.messages.text(player, "gui.mob-group.empty-members"));
        }
        long totalWeight = 0L;
        for (String memberId : members.getKeys(false)) {
            String mobId = members.getString(memberId + ".mob", "");
            if (!context.mobExists.test(mobId)) {
                throw new IllegalArgumentException(context.messages.text(
                        player, "gui.mob-member-editor.invalid-mob") + ": " + mobId);
            }
            long weight;
            try {
                weight = ConfigValues.requireLong(
                        members, memberId + ".weight", 1L, context.safetyLimits.maxWeight());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(context.messages.text(
                        player, "gui.mob-member-editor.invalid-weight")
                        .replace("{max}", Long.toString(context.safetyLimits.maxWeight())));
            }
            try {
                totalWeight = ConfigValues.addExact(totalWeight, weight, "members.weight");
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(context.messages.text(
                        player, "gui.mob-group.invalid-total-weight"));
            }
        }
    }

    private void validateBoss(Player player, YamlConfiguration yaml) {
        BossPhaseMode mode;
        try {
            mode = BossPhaseMode.fromConfig(yaml.getString("phase-mode", "death-respawn"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(context.messages.text(player, "gui.boss.invalid-phase-mode"));
        }
        if (mode == BossPhaseMode.DEATH_RESPAWN) {
            List<Map<?, ?>> phases = yaml.getMapList("phases");
            if (phases.isEmpty()) {
                throw new IllegalArgumentException(context.messages.text(player, "gui.boss.empty-phases"));
            }
            for (Map<?, ?> phase : phases) {
                Object rawMobId = phase.get("mob");
                validateBossMob(player, rawMobId == null ? "" : String.valueOf(rawMobId));
                validateBossLevel(player, phase.get("level"));
            }
        } else {
            validateBossMob(player, yaml.getString("mythic-native.mob", ""));
            validateBossLevel(player, yaml.get("mythic-native.level", 1.0));
        }
        try {
            if (yaml.isConfigurationSection("loot")) {
                IntermediateStageLootMode.fromConfig(yaml.getString("loot.intermediate-stage", "none"));
                FinalStageLootMode.fromConfig(yaml.getString("loot.final-stage", "mythictools-only"));
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(context.messages.text(player, "gui.boss.invalid-loot-policy"));
        }
        try {
            BossSpawnConfigurationValidator.validate(yaml);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    context.messages.text(player, "gui.boss.invalid-time-window"), exception);
        }
    }

    private void validateBossMob(Player player, String mobId) {
        if (mobId == null || mobId.isBlank() || !context.mobExists.test(mobId)) {
            throw new IllegalArgumentException(
                    context.messages.text(player, "gui.boss.invalid-mob") + ": " + mobId);
        }
    }

    private void validateBossLevel(Player player, Object value) {
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue()) || number.doubleValue() <= 0.0D) {
            throw new IllegalArgumentException(context.messages.text(player, "gui.boss.invalid-level"));
        }
    }
}
