package io.github.tamawish.rwr.discord.listener;

import io.github.tamawish.rwr.api.event.ResourceWorldPostResetEvent;
import io.github.tamawish.rwr.api.event.ResourceWorldResetWarningEvent;
import io.github.tamawish.rwr.api.model.ResetFailureType;
import io.github.tamawish.rwr.api.model.ResetPhase;
import io.github.tamawish.rwr.discord.RwrDiscordPlugin;
import io.github.tamawish.rwr.discord.config.DiscordConfig;
import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import io.github.tamawish.rwr.discord.webhook.WebhookService;
import com.google.gson.JsonObject;
import java.util.Optional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Forwards selected RWR API events to Discord.
 *
 * <p>v1 only emits configured scheduled warnings, successful completions, failures, cancellations,
 * and interrupted operations — never intermediate phases.
 */
public final class ResetEventListener implements Listener {
    private final RwrDiscordPlugin plugin;

    public ResetEventListener(RwrDiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWarning(ResourceWorldResetWarningEvent event) {
        DiscordConfig config = plugin.discordConfig();
        WebhookService webhooks = plugin.webhooks();
        if (!plugin.isApiAvailable() || config == null || webhooks == null || !config.sendWarnings()) {
            return;
        }
        JsonObject embed = webhooks.embeds()
                .buildWarning(
                        event.getWorldId(),
                        event.getWorldName(),
                        event.getMinutesRemaining(),
                        event.getScheduledResetAt());
        String correlationId = "warning:"
                + event.getWorldId()
                + ":"
                + event.getScheduledResetAt().toEpochMilli()
                + ":"
                + event.getMinutesRemaining();
        webhooks.enqueue(NotificationCategory.WARNING, correlationId, embed);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPostReset(ResourceWorldPostResetEvent event) {
        DiscordConfig config = plugin.discordConfig();
        WebhookService webhooks = plugin.webhooks();
        if (!plugin.isApiAvailable() || config == null || webhooks == null) {
            return;
        }

        ResetPhase phase = event.getPhase();
        Optional<ResetFailureType> failure = event.getFailure();
        NotificationCategory category;
        if (phase == ResetPhase.COMPLETE) {
            if (!config.sendSuccess()) {
                return;
            }
            category = NotificationCategory.SUCCESS;
        } else if (phase == ResetPhase.INTERRUPTED) {
            if (!config.sendInterrupted()) {
                return;
            }
            category = NotificationCategory.INTERRUPTED;
        } else if (phase == ResetPhase.FAILED
                && failure.isPresent()
                && failure.get() == ResetFailureType.EVENT_CANCELLED) {
            if (!config.sendCancellations()) {
                return;
            }
            category = NotificationCategory.CANCELLATION;
        } else if (phase == ResetPhase.FAILED) {
            if (!config.sendFailures()) {
                return;
            }
            category = NotificationCategory.FAILURE;
        } else {
            // Intermediate phases are never forwarded.
            return;
        }

        JsonObject embed = webhooks.embeds()
                .buildTerminal(
                        category,
                        event.getOperationId(),
                        event.getWorldId(),
                        event.getWorldName(),
                        phase,
                        failure,
                        event.getSafety(),
                        event.getMessage());
        webhooks.enqueue(category, event.getOperationId(), embed);
    }
}
