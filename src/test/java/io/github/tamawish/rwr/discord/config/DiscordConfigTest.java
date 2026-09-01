package io.github.tamawish.rwr.discord.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import io.github.tamawish.rwr.discord.webhook.WebhookService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DiscordConfigTest {
    @Test
    void rejectsNonDiscordWebhookUrl() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("webhook.url", "https://example.com/api/webhooks/1/token");

        assertThatThrownBy(() -> DiscordConfig.load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Discord incoming webhook");
    }

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
    void loadsUnderscoreAvatarUrlKey() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("webhook.avatar_url", "https://files.catbox.moe/6pm9fu.png");

        assertThat(DiscordConfig.load(yaml).avatarUrl())
                .isEqualTo("https://files.catbox.moe/6pm9fu.png");
    }
    @Test
    void loadIncludesMentionSettings() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mentions.enabled", true);
        yaml.set("mentions.roles.all", "123456789012345678");

        DiscordConfig config = DiscordConfig.load(yaml);

        assertThat(config.mentions().enabled()).isTrue();
        assertThat(config.mentions().resolveRoleIds(NotificationCategory.WARNING))
                .containsExactly("123456789012345678");
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
