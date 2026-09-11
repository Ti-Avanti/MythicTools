package gg.fotia.mythictools.reward;

import gg.fotia.mythictools.config.PluginSettings;
import gg.fotia.mythictools.lang.LocaleService;
import gg.fotia.mythictools.storage.PendingRewardQueue;
import gg.fotia.mythictools.storage.QueueResult;
import gg.fotia.mythictools.text.MessageRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 将已抽取奖励交付给在线或离线玩家。 */
public final class RewardService {
    private final RewardRepository repository;
    private final PendingRewardQueue pendingRewards;
    private final LocaleService locales;
    private final MessageRenderer messages;
    private final PluginSettings settings;
    private final Function<UUID, OfflinePlayer> offlinePlayers;
    private final Executor fallbackExecutor;
    private final Logger logger;

    public RewardService(
            RewardRepository repository,
            PendingRewardQueue pendingRewards,
            LocaleService locales,
            MessageRenderer messages,
            PluginSettings settings,
            Executor fallbackExecutor,
            Logger logger) {
        this(
                repository,
                pendingRewards,
                locales,
                messages,
                settings,
                Bukkit::getOfflinePlayer,
                fallbackExecutor,
                logger);
    }

    RewardService(
            RewardRepository repository,
            PendingRewardQueue pendingRewards,
            LocaleService locales,
            MessageRenderer messages,
            PluginSettings settings,
            Function<UUID, OfflinePlayer> offlinePlayers,
            Executor fallbackExecutor,
            Logger logger) {
        this.repository = repository;
        this.pendingRewards = pendingRewards;
        this.locales = locales;
        this.messages = messages;
        this.settings = settings;
        this.offlinePlayers = offlinePlayers;
        this.fallbackExecutor = fallbackExecutor;
        this.logger = logger;
    }

    /** 交付一批奖励；物品支持落地、背包和离线排队。 */
    public void deliver(
            UUID playerId,
            String playerName,
            Player onlinePlayer,
            List<RewardGrant> grants,
            Location groundLocation,
            Map<String, ?> variables) {
        deliver(playerId, playerName, onlinePlayer, grants, groundLocation, variables, false);
    }

    /** 交付一批奖励，并可为 Boss 结算强制使用背包/离线队列。 */
    public void deliver(
            UUID playerId,
            String playerName,
            Player onlinePlayer,
            List<RewardGrant> grants,
            Location groundLocation,
            Map<String, ?> variables,
            boolean forceInventory) {
        deliverTracked(playerId, playerName, onlinePlayer, grants, groundLocation, variables, forceInventory);
    }

    /** 返回背包交付或持久队列接管完成信号，供首次击败待发记录确认使用。 */
    public CompletionStage<Void> deliverTracked(
            UUID playerId, String playerName, Player onlinePlayer, List<RewardGrant> grants,
            Location groundLocation, Map<String, ?> variables, boolean forceInventory) {
        Player recipient = onlinePlayer;
        OfflinePlayer placeholderContext = null;
        List<ItemStack> pendingItems = new ArrayList<>();
        String locale = onlinePlayer == null ? settings.defaultLocale() : locales.locale(onlinePlayer);
        for (RewardGrant grant : grants) {
            RewardEntry entry = grant.entry();
            if (entry.type() == RewardType.ITEM) {
                List<ItemStack> stacks = createStacks(entry.item(), grant.amount());
                if (!forceInventory && entry.delivery() == RewardDelivery.GROUND && groundLocation != null) {
                    stacks.forEach(stack -> groundLocation.getWorld().dropItemNaturally(groundLocation, stack));
                } else if (onlinePlayer == null) {
                    if (playerId != null) {
                        pendingItems.addAll(stacks);
                    }
                } else {
                    for (ItemStack stack : stacks) {
                        Map<Integer, ItemStack> overflow = onlinePlayer.getInventory().addItem(stack);
                        if (settings.dropOverflowAtFeet()) {
                            overflow.values().forEach(left -> recipient.getWorld()
                                    .dropItemNaturally(recipient.getLocation(), left));
                        } else {
                            pendingItems.addAll(overflow.values());
                        }
                    }
                }
            } else {
                if (placeholderContext == null && playerId != null) {
                    placeholderContext = offlinePlayers.apply(playerId);
                }
                executeCommand(entry, grant.amount(), playerName, onlinePlayer, placeholderContext, variables);
            }
            if (onlinePlayer != null && !entry.message(locale).isBlank()) {
                Map<String, Object> messageVariables = new java.util.HashMap<>(variables);
                messageVariables.put("amount", grant.amount());
                messageVariables.put("reward", entry.display(locale));
                messageVariables.put("rarity", repository.rarityOrDefault(entry.rarityId()).display(locale));
                messages.send(onlinePlayer, messages.render(entry.message(locale), onlinePlayer, messageVariables));
            }
        }
        if (!pendingItems.isEmpty() && playerId != null) {
            return queueOrFallback(playerId, onlinePlayer, pendingItems);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> queueOrFallback(UUID playerId, Player onlinePlayer, List<ItemStack> items) {
        List<ItemStack> snapshots = copyItems(items);
        CompletionStage<QueueResult> queued;
        try {
            queued = pendingRewards.queue(playerId, snapshots);
        } catch (RuntimeException exception) {
            return scheduleFallback(playerId, onlinePlayer, snapshots);
        }
        if (queued == null) {
            return scheduleFallback(playerId, onlinePlayer, snapshots);
        }
        return queued.handle((result, failure) -> failure == null && result == QueueResult.STORED)
                .thenCompose(stored -> stored ? CompletableFuture.completedFuture(null)
                        : scheduleFallback(playerId, onlinePlayer, snapshots));
    }

    private CompletionStage<Void> scheduleFallback(UUID playerId, Player onlinePlayer, List<ItemStack> items) {
        if (onlinePlayer == null) {
            return retainOrLog(playerId, items);
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            fallbackExecutor.execute(() -> {
                try {
                    deliverFallback(playerId, onlinePlayer, items).whenComplete((ignored, failure) -> {
                        if (failure == null) {
                            completion.complete(null);
                        } else {
                            completion.completeExceptionally(failure);
                        }
                    });
                } catch (RuntimeException exception) {
                    completion.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            return retainOrLog(playerId, items);
        }
        return completion;
    }

    private CompletionStage<Void> deliverFallback(UUID playerId, Player player, List<ItemStack> items) {
        if (player == null || !player.isOnline()) {
            return retainOrLog(playerId, items);
        }
        List<ItemStack> retained = new ArrayList<>();
        for (ItemStack item : copyItems(items)) {
            try {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                for (ItemStack leftover : overflow.values()) {
                    try {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    } catch (RuntimeException exception) {
                        retained.add(leftover.clone());
                    }
                }
            } catch (RuntimeException exception) {
                try {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                } catch (RuntimeException dropException) {
                    retained.add(item.clone());
                }
            }
        }
        return retained.isEmpty() ? CompletableFuture.completedFuture(null) : retainOrLog(playerId, retained);
    }

    private CompletionStage<Void> retainOrLog(UUID playerId, List<ItemStack> items) {
        CompletionStage<QueueResult> retained;
        try {
            retained = pendingRewards.retainForRetry(playerId, copyItems(items));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "无法持久保存离线奖励，奖励可能无法恢复: " + playerId, exception);
            return CompletableFuture.failedFuture(exception);
        }
        if (retained == null) {
            logger.severe("无法持久保存离线奖励，仓储未返回接管结果: " + playerId);
            return CompletableFuture.failedFuture(new IllegalStateException("奖励仓储未返回接管结果"));
        }
        return retained.handle((result, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "无法持久保存离线奖励，奖励可能无法恢复: " + playerId, failure);
                throw new java.util.concurrent.CompletionException(failure);
            } else if (result != QueueResult.STORED) {
                logger.severe("无法持久保存离线奖励，仓储接管结果=" + result + ": " + playerId);
                throw new IllegalStateException("奖励仓储接管失败: " + result);
            }
            return null;
        });
    }

    /** 执行出生/死亡等配置中的控制台指令。 */
    public void executeConsoleCommands(List<String> commands, Player context, Map<String, ?> variables) {
        for (String command : commands) {
            String parsed = applyVariables(command, variables);
            if (context != null) {
                parsed = PlaceholderAPI.setPlaceholders(context, parsed);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(parsed));
        }
    }

    /** 返回用于 Boss 悬停文本的本地化奖励名称。 */
    public String rewardName(RewardGrant grant, String locale) {
        return grant.entry().display(locale);
    }

    /** 返回用于 Boss 悬停文本的本地化稀有度。 */
    public String rarityName(RewardGrant grant, String locale) {
        return repository.rarityOrDefault(grant.entry().rarityId()).display(locale);
    }

    private void executeCommand(
            RewardEntry entry,
            int amount,
            String playerName,
            Player onlinePlayer,
            OfflinePlayer placeholderContext,
            Map<String, ?> variables) {
        String safeName = playerName == null ? "" : playerName;
        if (safeName.isBlank() && entry.command().contains("{player}")) {
            logger.warning("指令奖励包含 {player} 但本次没有玩家接收者，已跳过: " + entry.command());
            return;
        }
        for (int index = 0; index < amount; index++) {
            String command = applyVariables(entry.command(), variables).replace("{player}", safeName);
            if (placeholderContext != null) {
                command = PlaceholderAPI.setPlaceholders(placeholderContext, command);
            }
            if (entry.commandExecutor() == CommandExecutorType.PLAYER) {
                if (onlinePlayer != null) {
                    Bukkit.dispatchCommand(onlinePlayer, stripSlash(command));
                }
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(command));
            }
        }
    }

    private static List<ItemStack> createStacks(ItemStack template, int amount) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        int maximum = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(maximum, remaining));
            result.add(stack);
            remaining -= stack.getAmount();
        }
        return result;
    }

    private static List<ItemStack> copyItems(List<ItemStack> items) {
        return items.stream().map(ItemStack::clone).toList();
    }

    private static String applyVariables(String input, Map<String, ?> variables) {
        String result = input;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
