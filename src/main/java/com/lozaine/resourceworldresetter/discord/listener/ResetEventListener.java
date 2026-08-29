package com.lozaine.resourceworldresetter.discord.listener;

import com.lozaine.resourceworldresetter.api.event.ResourceWorldPostResetEvent;
import com.lozaine.resourceworldresetter.api.event.ResourceWorldPreResetEvent;
import com.lozaine.resourceworldresetter.api.model.ResetFailureType;
import com.lozaine.resourceworldresetter.api.model.ResetPhase;
import com.lozaine.resourceworldresetter.discord.RwrDiscordPlugin;
import com.lozaine.resourceworldresetter.discord.config.DiscordConfig;
import com.lozaine.resourceworldresetter.discord.webhook.WebhookEventCategory;
import com.lozaine.resourceworldresetter.discord.webhook.WebhookService;
import com.google.gson.JsonObject;
import java.util.Optional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Forwards selected RWR API events to Discord.
 *
 * <p>v1 only emits configured warnings (pre-reset), successful completions, failures, cancellations,
 * and interrupted operations — never intermediate phases.
 */
public final class ResetEventListener implements Listener {
    private final RwrDiscordPlugin plugin;

    public ResetEventListener(RwrDiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPreReset(ResourceWorldPreResetEvent event) {
        plugin.refreshApiAvailability();
        DiscordConfig config = plugin.discordConfig();
        WebhookService webhooks = plugin.webhooks();
        if (config == null || webhooks == null || !config.sendWarnings()) {
            return;
        }
        JsonObject embed =
                webhooks.embeds().buildWarning(event.getOperationId(), event.getWorldId(), event.getWorldName());
        webhooks.enqueue(WebhookEventCategory.WARNING, event.getOperationId(), embed);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPostReset(ResourceWorldPostResetEvent event) {
        plugin.refreshApiAvailability();
        DiscordConfig config = plugin.discordConfig();
        WebhookService webhooks = plugin.webhooks();
        if (config == null || webhooks == null) {
            return;
        }

        ResetPhase phase = event.getPhase();
        Optional<ResetFailureType> failure = event.getFailure();
        WebhookEventCategory category;
        if (phase == ResetPhase.COMPLETE) {
            if (!config.sendSuccess()) {
                return;
            }
            category = WebhookEventCategory.SUCCESS;
        } else if (phase == ResetPhase.INTERRUPTED) {
            if (!config.sendInterrupted()) {
                return;
            }
            category = WebhookEventCategory.INTERRUPTED;
        } else if (phase == ResetPhase.FAILED
                && failure.isPresent()
                && failure.get() == ResetFailureType.EVENT_CANCELLED) {
            if (!config.sendCancellations()) {
                return;
            }
            category = WebhookEventCategory.CANCELLATION;
        } else if (phase == ResetPhase.FAILED) {
            if (!config.sendFailures()) {
                return;
            }
            category = WebhookEventCategory.FAILURE;
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
