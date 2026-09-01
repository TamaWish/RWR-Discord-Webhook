package io.github.tamawish.rwr.discord.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MentionSettingsTest {
    @Test
    void disabledByDefault() {
        MentionSettings settings = MentionSettings.load(new YamlConfiguration());

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.resolveRoleIds(NotificationCategory.WARNING)).isEmpty();
        assertThat(settings.userIds()).isEmpty();
    }

    @Test
    void resolvesAllAndCategoryRoles() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mentions.enabled", true);
        yaml.set("mentions.roles.all", "123456789012345678");
        yaml.set("mentions.roles.failures", "987654321098765432");

        MentionSettings settings = MentionSettings.load(yaml);

        assertThat(settings.resolveRoleIds(NotificationCategory.FAILURE))
                .containsExactly("123456789012345678", "987654321098765432");
        assertThat(settings.resolveRoleIds(NotificationCategory.SUCCESS))
                .containsExactly("123456789012345678");
    }

    @Test
    void rejectsInvalidSnowflake() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mentions.roles.all", "not-a-snowflake");

        assertThatThrownBy(() -> MentionSettings.load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snowflake");
    }
}
