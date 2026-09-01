package io.github.tamawish.rwr.discord.webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.tamawish.rwr.api.model.FailureSafety;
import io.github.tamawish.rwr.api.model.ResetFailureType;
import io.github.tamawish.rwr.api.model.ResetPhase;
import io.github.tamawish.rwr.discord.PluginLinks;
import io.github.tamawish.rwr.discord.config.DiscordConfig;
import io.github.tamawish.rwr.discord.locale.LocaleService;
import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import java.time.Instant;
import java.util.Optional;

/** Builds Discord embed JSON for RWR reset lifecycle events. */
public final class EmbedBuilder {
    private final LocaleService locale;
    private final DiscordConfig config;

    public EmbedBuilder(LocaleService locale, DiscordConfig config) {
        this.locale = locale;
        this.config = config;
    }

    /** Builds the notice sent after a configured webhook service starts. */
    public JsonObject buildConfiguration(boolean apiAvailable, String pluginVersion, String serverVersion) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", locale.format("embed.configuration-title"));
        embed.addProperty(
                "description",
                withLegalLinks(locale.format("embed.configuration-description")));
        embed.addProperty("color", config.successColor());
        embed.addProperty("timestamp", Instant.now().toString());

        JsonArray fields = new JsonArray();
        fields.add(field(
                locale.raw("embed.field-api-status"),
                locale.raw(apiAvailable ? "embed.api-available" : "embed.api-unavailable"),
                true));
        fields.add(field(locale.raw("embed.field-addon-version"), pluginVersion, true));
        fields.add(field(locale.raw("embed.field-server"), serverVersion, false));
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "RWR-Discord Webhook - CONFIGURATION");
        embed.add("footer", footer);
        return embed;
    }
    public JsonObject buildWarning(
            String worldId, String worldName, int minutesRemaining, Instant scheduledResetAt) {
        String remaining = locale.format("embed.remaining-minutes", "minutes", minutesRemaining);
        return baseEmbed(
                NotificationCategory.WARNING,
                config.warningColor(),
                "embed.warning-title",
                locale.format(
                        "embed.warning-description",
                        "world_name",
                        worldName,
                        "world_id",
                        worldId,
                        "remaining",
                        remaining,
                        "scheduled_at",
                        scheduledResetAt),
                Optional.empty(),
                worldId,
                worldName,
                Optional.of(remaining),
                Optional.of(scheduledResetAt),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public JsonObject buildTerminal(
            NotificationCategory category,
            String operationId,
            String worldId,
            String worldName,
            ResetPhase phase,
            Optional<ResetFailureType> failure,
            FailureSafety safety,
            String message) {
        int color =
                switch (category) {
                    case CONFIGURATION -> config.successColor();
                    case SUCCESS -> config.successColor();
                    case FAILURE -> config.failureColor();
                    case CANCELLATION -> config.cancellationColor();
                    case INTERRUPTED -> config.interruptedColor();
                    case WARNING -> config.warningColor();
                };
        String titleKey =
                switch (category) {
                    case CONFIGURATION -> "embed.configuration-title";
                    case SUCCESS -> "embed.success-title";
                    case FAILURE -> "embed.failure-title";
                    case CANCELLATION -> "embed.cancellation-title";
                    case INTERRUPTED -> "embed.interrupted-title";
                    case WARNING -> "embed.warning-title";
                };
        String descriptionKey =
                switch (category) {
                    case CONFIGURATION -> "embed.configuration-description";
                    case SUCCESS -> "embed.success-description";
                    case FAILURE -> "embed.failure-description";
                    case CANCELLATION -> "embed.cancellation-description";
                    case INTERRUPTED -> "embed.interrupted-description";
                    case WARNING -> "embed.warning-description";
                };
        return baseEmbed(
                category,
                color,
                titleKey,
                locale.format(descriptionKey, "world_name", worldName, "world_id", worldId),
                Optional.of(operationId),
                worldId,
                worldName,
                Optional.empty(),
                Optional.empty(),
                Optional.of(phase),
                failure,
                Optional.ofNullable(safety),
                Optional.ofNullable(message));
    }

    private JsonObject baseEmbed(
            NotificationCategory category,
            int color,
            String titleKey,
            String description,
            Optional<String> operationId,
            String worldId,
            String worldName,
            Optional<String> remaining,
            Optional<Instant> scheduledResetAt,
            Optional<ResetPhase> phase,
            Optional<ResetFailureType> failure,
            Optional<FailureSafety> safety,
            Optional<String> message) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", locale.format(titleKey));
        embed.addProperty("description", withLegalLinks(description));
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonArray fields = new JsonArray();
        fields.add(field(locale.raw("embed.field-world-id"), worldId, true));
        fields.add(field(locale.raw("embed.field-world-name"), worldName, true));
        operationId.ifPresent(
                id -> fields.add(field(locale.raw("embed.field-operation"), id, true)));
        remaining.ifPresent(
                value -> fields.add(field(locale.raw("embed.field-remaining"), value, true)));
        scheduledResetAt.ifPresent(
                value -> fields.add(field(locale.raw("embed.field-scheduled-at"), value.toString(), false)));
        phase.ifPresent(
                p -> fields.add(field(
                        locale.raw("embed.field-phase"),
                        locale.raw("embed.phase-" + p.name()),
                        true)));
        failure.ifPresent(
                f -> fields.add(field(
                        locale.raw("embed.field-failure"),
                        locale.raw("embed.failure-" + f.name()),
                        true)));
        safety.ifPresent(
                s -> fields.add(field(
                        locale.raw("embed.field-safety"),
                        locale.raw("embed.safety-" + s.name()),
                        true)));
        message.ifPresent(
                m -> fields.add(field(
                        locale.raw("embed.field-message"),
                        LocaleService.stripMentions(m),
                        false)));
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "RWR-Discord Webhook · " + category.name());
        embed.add("footer", footer);
        return embed;
    }

    private String legalLinksLine() {
        return locale.format(
                "embed.legal-links",
                "privacy_url",
                PluginLinks.PRIVACY_POLICY,
                "terms_url",
                PluginLinks.TERMS_OF_SERVICE);
    }

    private String withLegalLinks(String description) {
        return PluginLinks.appendLegalLinks(description, legalLinksLine());
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name == null || name.isBlank() ? "-" : name);
        field.addProperty(
                "value",
                value == null || value.isBlank()
                        ? "-"
                        : (value.length() > 1024 ? value.substring(0, 1021) + "..." : value));
        field.addProperty("inline", inline);
        return field;
    }
}
