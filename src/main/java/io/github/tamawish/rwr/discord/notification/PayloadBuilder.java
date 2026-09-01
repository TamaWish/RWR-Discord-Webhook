package io.github.tamawish.rwr.discord.notification;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.tamawish.rwr.discord.config.DiscordConfig;
import java.util.Objects;

/** Assembles the Discord webhook JSON payload shared by webhook and future bot delivery. */
public final class PayloadBuilder {
    private final DiscordConfig config;
    private final MentionResolver mentions;
    private final Gson gson = new Gson();

    public PayloadBuilder(DiscordConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.mentions = new MentionResolver(config.mentions());
    }

    public String build(NotificationCategory category, JsonObject embed) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(embed, "embed");

        JsonObject root = new JsonObject();
        if (!config.username().isEmpty()) {
            root.addProperty("username", config.username());
        }
        if (!config.avatarUrl().isEmpty()) {
            root.addProperty("avatar_url", config.avatarUrl());
        }

        MentionResolver.ResolvedMentions resolved = mentions.resolve(category);
        if (!resolved.content().isEmpty()) {
            root.addProperty("content", resolved.content());
        }
        root.add("allowed_mentions", resolved.allowedMentions());

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        root.add("embeds", embeds);
        return gson.toJson(root);
    }
}
