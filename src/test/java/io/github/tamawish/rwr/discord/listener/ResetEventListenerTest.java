package io.github.tamawish.rwr.discord.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import io.github.tamawish.rwr.api.event.ResourceWorldResetWarningEvent;
import io.github.tamawish.rwr.api.event.ResourceWorldPostResetEvent;
import io.github.tamawish.rwr.api.model.FailureSafety;
import io.github.tamawish.rwr.api.model.ResetFailureType;
import io.github.tamawish.rwr.api.model.ResetPhase;
import io.github.tamawish.rwr.discord.RwrDiscordPlugin;
import io.github.tamawish.rwr.discord.config.DiscordConfig;
import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import io.github.tamawish.rwr.discord.webhook.EmbedBuilder;
import io.github.tamawish.rwr.discord.webhook.WebhookService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ResetEventListenerTest {
    @Test
    void forwardsEveryEnabledScheduledWarningWithoutAnOperationId() {
        RwrDiscordPlugin plugin = mock(RwrDiscordPlugin.class);
        DiscordConfig config = mock(DiscordConfig.class);
        WebhookService webhooks = mock(WebhookService.class);
        EmbedBuilder embeds = mock(EmbedBuilder.class);
        Instant scheduledAt = Instant.parse("2026-08-30T12:00:00Z");
        JsonObject embed = new JsonObject();
        when(plugin.isApiAvailable()).thenReturn(true);
        when(plugin.discordConfig()).thenReturn(config);
        when(plugin.webhooks()).thenReturn(webhooks);
        when(config.sendWarnings()).thenReturn(true);
        when(webhooks.embeds()).thenReturn(embeds);
        when(embeds.buildWarning("resource", "resource_world", 10, scheduledAt))
                .thenReturn(embed);

        new ResetEventListener(plugin).onWarning(
                new ResourceWorldResetWarningEvent("resource", "resource_world", 10, scheduledAt));

        verify(embeds).buildWarning("resource", "resource_world", 10, scheduledAt);
        verify(webhooks).enqueue(
                org.mockito.ArgumentMatchers.eq(NotificationCategory.WARNING),
                startsWith("warning:resource:"),
                org.mockito.ArgumentMatchers.same(embed));
    }

    @Test
    void warningToggleAndDegradedModeSuppressDelivery() {
        RwrDiscordPlugin plugin = mock(RwrDiscordPlugin.class);
        DiscordConfig config = mock(DiscordConfig.class);
        WebhookService webhooks = mock(WebhookService.class);
        when(plugin.discordConfig()).thenReturn(config);
        when(plugin.webhooks()).thenReturn(webhooks);
        ResourceWorldResetWarningEvent event = new ResourceWorldResetWarningEvent(
                "resource", "resource_world", 5, Instant.EPOCH);

        new ResetEventListener(plugin).onWarning(event);

        verify(webhooks, never()).enqueue(any(), any(), any());
    }

    @Test
    void cancellationUsesItsOwnConfiguredCategory() {
        RwrDiscordPlugin plugin = mock(RwrDiscordPlugin.class);
        DiscordConfig config = mock(DiscordConfig.class);
        WebhookService webhooks = mock(WebhookService.class);
        EmbedBuilder embeds = mock(EmbedBuilder.class);
        JsonObject embed = new JsonObject();
        when(plugin.isApiAvailable()).thenReturn(true);
        when(plugin.discordConfig()).thenReturn(config);
        when(plugin.webhooks()).thenReturn(webhooks);
        when(config.sendCancellations()).thenReturn(true);
        when(webhooks.embeds()).thenReturn(embeds);
        when(embeds.buildTerminal(
                        NotificationCategory.CANCELLATION,
                        "operation",
                        "resource",
                        "resource_world",
                        ResetPhase.FAILED,
                        java.util.Optional.of(ResetFailureType.EVENT_CANCELLED),
                        FailureSafety.NOT_RETRYABLE,
                        "cancelled"))
                .thenReturn(embed);

        new ResetEventListener(plugin).onPostReset(new ResourceWorldPostResetEvent(
                "operation",
                "resource",
                "resource_world",
                ResetPhase.FAILED,
                ResetFailureType.EVENT_CANCELLED,
                FailureSafety.NOT_RETRYABLE,
                "cancelled"));

        verify(webhooks).enqueue(NotificationCategory.CANCELLATION, "operation", embed);
    }
}
