package com.lozaine.resourceworldresetter.discord.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.lozaine.resourceworldresetter.discord.webhook.WebhookService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DiscordConfigTest {
    @Test
    void loadAppliesDefaultsAndNormalizesLocale() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("locale", "en-us");
        yaml.set("webhook.url", "  ");
        DiscordConfig config = DiscordConfig.load(yaml);
        assertThat(config.locale()).isEqualTo("en_US");
        assertThat(config.hasWebhookUrl()).isFalse();
        assertThat(config.sendWarnings()).isTrue();
        assertThat(config.sendSuccess()).isTrue();
        assertThat(config.queueCapacity()).isEqualTo(WebhookService.DEFAULT_QUEUE_CAPACITY);
        assertThat(config.maxAttempts()).isEqualTo(WebhookService.DEFAULT_MAX_ATTEMPTS);
        assertThat(config.queueTtlMs()).isEqualTo(WebhookService.DEFAULT_TTL_MS);
    }

    @Test
    void loadRespectsEventToggles() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("events.warnings", false);
        yaml.set("events.cancellations", false);
        yaml.set("webhook.url", "https://discord.com/api/webhooks/1/token");
        DiscordConfig config = DiscordConfig.load(yaml);
        assertThat(config.sendWarnings()).isFalse();
        assertThat(config.sendCancellations()).isFalse();
        assertThat(config.sendFailures()).isTrue();
        assertThat(config.hasWebhookUrl()).isTrue();
    }

    @Test
    void loadCapsQueueAndAttempts() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("webhook.queue-capacity", 9999);
        yaml.set("webhook.max-attempts", 99);
        yaml.set("webhook.queue-ttl-hours", 48);
        DiscordConfig config = DiscordConfig.load(yaml);
        assertThat(config.queueCapacity()).isEqualTo(500);
        assertThat(config.maxAttempts()).isEqualTo(8);
        assertThat(config.queueTtlMs()).isEqualTo(WebhookService.DEFAULT_TTL_MS);
    }
}
