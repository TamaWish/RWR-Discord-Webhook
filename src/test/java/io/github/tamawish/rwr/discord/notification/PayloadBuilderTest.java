package io.github.tamawish.rwr.discord.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.tamawish.rwr.discord.config.DiscordConfig;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PayloadBuilderTest {
    @Test
    void disablesMentionsWhenNotConfigured() {
        DiscordConfig config = configWithMentions(false, null, null);
        PayloadBuilder builder = new PayloadBuilder(config);
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Reset warning");

        String json = builder.build(NotificationCategory.WARNING, embed);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertThat(root.has("content")).isFalse();
        assertThat(root.getAsJsonObject("allowed_mentions").getAsJsonArray("parse"))
                .isEmpty();
        assertThat(root.getAsJsonArray("embeds")).hasSize(1);
        assertThat(root.get("username").getAsString()).isEqualTo("RWR");
    }

    @Test
    void includesConfiguredRoleMentions() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mentions.enabled", true);
        yaml.set("mentions.roles.all", "123456789012345678");
        yaml.set("mentions.roles.failures", "987654321098765432");
        yaml.set("webhook.url", "https://discord.com/api/webhooks/1/token");
        DiscordConfig config = DiscordConfig.load(yaml);
        PayloadBuilder builder = new PayloadBuilder(config);
        JsonObject embed = new JsonObject();

        String json = builder.build(NotificationCategory.FAILURE, embed);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertThat(root.get("content").getAsString())
                .isEqualTo("<@&123456789012345678> <@&987654321098765432>");
        JsonArray roles = root.getAsJsonObject("allowed_mentions").getAsJsonArray("roles");
        assertThat(roles).hasSize(2);
        assertThat(roles.get(0).getAsString()).isEqualTo("123456789012345678");
        assertThat(roles.get(1).getAsString()).isEqualTo("987654321098765432");
    }

    @Test
    void includesConfiguredUserMentions() {
        DiscordConfig config = configWithMentions(true, "123456789012345678", "555555555555555555");
        PayloadBuilder builder = new PayloadBuilder(config);
        JsonObject embed = new JsonObject();

        String json = builder.build(NotificationCategory.WARNING, embed);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertThat(root.get("content").getAsString())
                .isEqualTo("<@&123456789012345678> <@555555555555555555>");
        assertThat(root.getAsJsonObject("allowed_mentions").getAsJsonArray("users"))
                .hasSize(1);
    }

    private static DiscordConfig configWithMentions(boolean enabled, String allRole, String userId) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mentions.enabled", enabled);
        if (allRole != null) {
            yaml.set("mentions.roles.all", allRole);
        }
        if (userId != null) {
            yaml.set("mentions.users", List.of(userId));
        }
        yaml.set("webhook.url", "https://discord.com/api/webhooks/1/token");
        return DiscordConfig.load(yaml);
    }
}
