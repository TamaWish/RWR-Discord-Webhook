package com.lozaine.resourceworldresetter.discord.webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lozaine.resourceworldresetter.api.model.FailureSafety;
import com.lozaine.resourceworldresetter.api.model.ResetFailureType;
import com.lozaine.resourceworldresetter.api.model.ResetPhase;
import com.lozaine.resourceworldresetter.discord.config.DiscordConfig;
import com.lozaine.resourceworldresetter.discord.locale.LocaleService;
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

    public JsonObject buildWarning(String operationId, String worldId, String worldName) {
        return baseEmbed(
                WebhookEventCategory.WARNING,
                config.warningColor(),
                "embed.warning-title",
                "embed.warning-description",
                operationId,
                worldId,
                worldName,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public JsonObject buildTerminal(
            WebhookEventCategory category,
            String operationId,
            String worldId,
            String worldName,
            ResetPhase phase,
            Optional<ResetFailureType> failure,
            FailureSafety safety,
            String message) {
        int color =
                switch (category) {
                    case SUCCESS -> config.successColor();
                    case FAILURE -> config.failureColor();
                    case CANCELLATION -> config.cancellationColor();
                    case INTERRUPTED -> config.interruptedColor();
                    case WARNING -> config.warningColor();
                };
        String titleKey =
                switch (category) {
                    case SUCCESS -> "embed.success-title";
                    case FAILURE -> "embed.failure-title";
                    case CANCELLATION -> "embed.cancellation-title";
                    case INTERRUPTED -> "embed.interrupted-title";
                    case WARNING -> "embed.warning-title";
                };
        String descriptionKey =
                switch (category) {
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
                descriptionKey,
                operationId,
                worldId,
                worldName,
                Optional.of(phase),
                failure,
                Optional.ofNullable(safety),
                Optional.ofNullable(message));
    }

    private JsonObject baseEmbed(
            WebhookEventCategory category,
            int color,
            String titleKey,
            String descriptionKey,
            String operationId,
            String worldId,
            String worldName,
            Optional<ResetPhase> phase,
            Optional<ResetFailureType> failure,
            Optional<FailureSafety> safety,
            Optional<String> message) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", locale.format(titleKey));
        embed.addProperty(
                "description",
                locale.format(descriptionKey, "world_name", worldName, "world_id", worldId));
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonArray fields = new JsonArray();
        fields.add(field(locale.raw("embed.field-world-id"), worldId, true));
        fields.add(field(locale.raw("embed.field-world-name"), worldName, true));
        fields.add(field(locale.raw("embed.field-operation"), operationId, true));
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
        footer.addProperty("text", "RWR-Discord · " + category.name());
        embed.add("footer", footer);
        return embed;
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
