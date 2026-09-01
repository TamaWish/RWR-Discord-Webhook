package io.github.tamawish.rwr.discord.command;

import io.github.tamawish.rwr.discord.RwrDiscordPlugin;
import io.github.tamawish.rwr.discord.locale.LocaleService;
import io.github.tamawish.rwr.discord.webhook.DeliveryStats;
import io.github.tamawish.rwr.discord.webhook.WebhookService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Handles the {@code /rwr discord} administration namespace. */
public final class DiscordCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "rwrdiscord.admin";
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final RwrDiscordPlugin plugin;

    public DiscordCommand(RwrDiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LocaleService locale = plugin.locale();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(locale.format("command.no-permission", "permission", PERMISSION));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(locale.raw("command.unknown"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender, locale);
            case "status" -> handleStatus(sender, locale);
            default -> sender.sendMessage(locale.raw("command.unknown"));
        }
        return true;
    }

    private void handleReload(CommandSender sender, LocaleService locale) {
        Optional<String> error = plugin.reloadServices();
        if (error.isPresent()) {
            sender.sendMessage(locale.format("command.reload-failed", "reason", error.get()));
        } else {
            sender.sendMessage(locale.raw("command.reload-success"));
        }
    }

    private void handleStatus(CommandSender sender, LocaleService locale) {
        plugin.refreshApiAvailability();
        WebhookService webhooks = plugin.webhooks();
        DeliveryStats stats = plugin.deliveryStats();

        sender.sendMessage(locale.raw("command.status-header"));
        sender.sendMessage(locale.format(
                "command.status-service",
                "state",
                plugin.isApiAvailable() ? "available" : "unavailable"));
        sender.sendMessage(locale.format(
                "command.status-webhook",
                "state",
                webhooks != null && webhooks.isConfigured() ? "configured" : "disabled"));
        int queueSize = webhooks == null ? 0 : webhooks.pendingCount();
        sender.sendMessage(locale.format("command.status-queue", "size", String.valueOf(queueSize)));
        sender.sendMessage(locale.format(
                "command.status-last-success", "time", formatInstant(stats.lastSuccess(), locale)));
        sender.sendMessage(locale.format(
                "command.status-last-failure",
                "time",
                formatInstant(stats.lastFailure(), locale),
                "detail",
                stats.lastFailureDetail().map(WebhookService::redact).orElse("-")));
        sender.sendMessage(
                locale.format("command.status-retries", "count", String.valueOf(stats.sessionRetries())));
    }

    private static String formatInstant(Optional<Instant> instant, LocaleService locale) {
        return instant.map(TIME::format).orElse(locale.raw("command.status-never"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("reload", "status").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
