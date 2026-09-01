package io.github.tamawish.rwr.discord.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.tamawish.rwr.api.model.FailureSafety;
import io.github.tamawish.rwr.api.model.ResetFailureType;
import io.github.tamawish.rwr.api.model.ResetPhase;
import io.github.tamawish.rwr.discord.PluginLinks;
import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import io.github.tamawish.rwr.discord.config.DiscordConfig;
import io.github.tamawish.rwr.discord.locale.LocaleService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedBuilderTest {
    private final LocaleService locale = mock(LocaleService.class);
    private final DiscordConfig config = mock(DiscordConfig.class);
    private final EmbedBuilder embeds = new EmbedBuilder(locale, config);

    @BeforeEach
    void setUpLabels() {
        when(locale.raw(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void warningContainsScheduleFieldsAndNoOperationField() {
        Instant scheduledAt = Instant.parse("2026-08-30T12:00:00Z");
        when(locale.format("embed.warning-title")).thenReturn("Reset warning");
        when(locale.format("embed.remaining-minutes", "minutes", 10)).thenReturn("10 minutes");
        when(locale.format(
                        "embed.warning-description",
                        "world_name",
                        "resource_world",
                        "world_id",
                        "resource",
                        "remaining",
                        "10 minutes",
                        "scheduled_at",
                        scheduledAt))
                .thenReturn("Scheduled warning");
        when(locale.format(
                        "embed.legal-links",
                        "privacy_url",
                        PluginLinks.PRIVACY_POLICY,
                        "terms_url",
                        PluginLinks.TERMS_OF_SERVICE))
                .thenReturn("[Privacy Policy](privacy) · [Terms of Service](terms)");

        JsonObject embed = embeds.buildWarning("resource", "resource_world", 10, scheduledAt);

        assertThat(embed.get("description").getAsString())
                .contains("Scheduled warning")
                .contains("[Privacy Policy](privacy)");

        JsonArray fields = embed.getAsJsonArray("fields");
        assertThat(fieldNames(fields))
                .contains("embed.field-world-id", "embed.field-world-name", "embed.field-remaining", "embed.field-scheduled-at")
                .doesNotContain("embed.field-operation");
        assertThat(fieldValue(fields, "embed.field-scheduled-at")).isEqualTo(scheduledAt.toString());
    }

    @Test
    void configurationNoticeReportsApiAddonAndServer() {
        when(locale.format("embed.configuration-title")).thenReturn("Configured");
        when(locale.format("embed.configuration-description")).thenReturn("Ready");
        when(locale.format(
                        "embed.legal-links",
                        "privacy_url",
                        PluginLinks.PRIVACY_POLICY,
                        "terms_url",
                        PluginLinks.TERMS_OF_SERVICE))
                .thenReturn("[Privacy Policy](privacy) · [Terms of Service](terms)");

        JsonObject embed = embeds.buildConfiguration(true, "1.0.0", "Folia 1.21.8");

        assertThat(embed.get("description").getAsString())
                .isEqualTo("Ready\n\n[Privacy Policy](privacy) · [Terms of Service](terms)");
        JsonArray fields = embed.getAsJsonArray("fields");
        assertThat(fieldNames(fields)).contains(
                "embed.field-api-status", "embed.field-addon-version", "embed.field-server");
        assertThat(fieldValue(fields, "embed.field-api-status")).isEqualTo("embed.api-available");
        assertThat(fieldValue(fields, "embed.field-addon-version")).isEqualTo("1.0.0");
        assertThat(fieldValue(fields, "embed.field-server")).isEqualTo("Folia 1.21.8");
    }
    @Test
    void terminalRetainsOperationAndDiagnosticFields() {
        when(locale.format("embed.failure-title")).thenReturn("Reset failed");
        when(locale.format(
                        "embed.failure-description",
                        "world_name",
                        "resource_world",
                        "world_id",
                        "resource"))
                .thenReturn("Failed");

        JsonObject embed = embeds.buildTerminal(
                NotificationCategory.FAILURE,
                "operation-1",
                "resource",
                "resource_world",
                ResetPhase.FAILED,
                Optional.of(ResetFailureType.VERIFICATION_FAILED),
                FailureSafety.AMBIGUOUS_REVIEW_REQUIRED,
                "verification detail");

        assertThat(fieldNames(embed.getAsJsonArray("fields")))
                .contains(
                        "embed.field-operation",
                        "embed.field-phase",
                        "embed.field-failure",
                        "embed.field-safety",
                        "embed.field-message");
    }

    private static java.util.List<String> fieldNames(JsonArray fields) {
        return fields.asList().stream()
                .map(element -> element.getAsJsonObject().get("name").getAsString())
                .toList();
    }

    private static String fieldValue(JsonArray fields, String name) {
        return fields.asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(field -> field.get("name").getAsString().equals(name))
                .findFirst()
                .orElseThrow()
                .get("value")
                .getAsString();
    }
}
