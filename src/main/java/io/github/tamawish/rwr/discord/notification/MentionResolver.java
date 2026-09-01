package io.github.tamawish.rwr.discord.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.tamawish.rwr.discord.config.MentionSettings;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds mention content and {@code allowed_mentions} for a Discord payload. */
public final class MentionResolver {
    private final MentionSettings settings;

    public MentionResolver(MentionSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Resolved mention line and explicit allow-list for Discord delivery. */
    public record ResolvedMentions(String content, JsonObject allowedMentions) {}

    public ResolvedMentions resolve(NotificationCategory category) {
        Set<String> roleIds = settings.resolveRoleIds(category);
        List<String> userIds = settings.userIds();
        if (roleIds.isEmpty() && userIds.isEmpty()) {
            JsonObject allowed = new JsonObject();
            allowed.add("parse", new JsonArray());
            return new ResolvedMentions("", allowed);
        }

        StringBuilder content = new StringBuilder();
        for (String roleId : roleIds) {
            if (!content.isEmpty()) {
                content.append(' ');
            }
            content.append("<@&").append(roleId).append('>');
        }
        for (String userId : userIds) {
            if (!content.isEmpty()) {
                content.append(' ');
            }
            content.append('<').append('@').append(userId).append('>');
        }

        JsonObject allowed = new JsonObject();
        if (!roleIds.isEmpty()) {
            JsonArray roles = new JsonArray();
            roleIds.forEach(roles::add);
            allowed.add("roles", roles);
        }
        if (!userIds.isEmpty()) {
            JsonArray users = new JsonArray();
            userIds.forEach(users::add);
            allowed.add("users", users);
        }
        return new ResolvedMentions(content.toString(), allowed);
    }
}
