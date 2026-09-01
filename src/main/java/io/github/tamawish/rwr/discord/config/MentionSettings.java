package io.github.tamawish.rwr.discord.config;

import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Configured Discord role and user mentions applied to outbound notifications. */
public final class MentionSettings {
    private static final Pattern SNOWFLAKE = Pattern.compile("\\d{17,20}");

    private final boolean enabled;
    private final String allRoleId;
    private final Map<NotificationCategory, String> roleByCategory;
    private final List<String> userIds;

    private MentionSettings(
            boolean enabled,
            String allRoleId,
            Map<NotificationCategory, String> roleByCategory,
            List<String> userIds) {
        this.enabled = enabled;
        this.allRoleId = allRoleId;
        this.roleByCategory = Map.copyOf(roleByCategory);
        this.userIds = List.copyOf(userIds);
    }

    /** Loads mention settings from the plugin config file. */
    public static MentionSettings load(FileConfiguration yaml) {
        Objects.requireNonNull(yaml, "yaml");
        boolean enabled = yaml.getBoolean("mentions.enabled", false);
        ConfigurationSection roles = yaml.getConfigurationSection("mentions.roles");
        String allRoleId = readRoleId(roles, "all");
        Map<NotificationCategory, String> roleByCategory = new EnumMap<>(NotificationCategory.class);
        for (NotificationCategory category : NotificationCategory.values()) {
            roleByCategory.put(category, readRoleId(roles, configKey(category)));
        }
        List<String> userIds = readUserIds(yaml.getStringList("mentions.users"));
        return new MentionSettings(enabled, allRoleId, roleByCategory, userIds);
    }

    public boolean enabled() {
        return enabled;
    }

    /**
     * Role IDs to mention for the given category.
     *
     * <p>When enabled, {@code mentions.roles.all} is always included first, followed by any
     * category-specific role. Empty IDs are ignored and duplicates are removed.
     */
    public Set<String> resolveRoleIds(NotificationCategory category) {
        if (!enabled) {
            return Set.of();
        }
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        if (!allRoleId.isEmpty()) {
            resolved.add(allRoleId);
        }
        String categoryRole = roleByCategory.getOrDefault(category, "");
        if (!categoryRole.isEmpty()) {
            resolved.add(categoryRole);
        }
        return Collections.unmodifiableSet(resolved);
    }

    public List<String> userIds() {
        return enabled ? userIds : List.of();
    }

    private static String configKey(NotificationCategory category) {
        return switch (category) {
            case CONFIGURATION -> "configuration";
            case WARNING -> "warnings";
            case SUCCESS -> "success";
            case FAILURE -> "failures";
            case CANCELLATION -> "cancellations";
            case INTERRUPTED -> "interrupted";
        };
    }

    private static String readRoleId(ConfigurationSection roles, String key) {
        if (roles == null) {
            return "";
        }
        return validateSnowflake(roles.getString(key, ""), "mentions.roles." + key);
    }

    private static List<String> readUserIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>();
        for (int index = 0; index < raw.size(); index++) {
            String value = validateSnowflake(raw.get(index), "mentions.users[" + index + "]");
            if (!value.isEmpty()) {
                resolved.add(value);
            }
        }
        return resolved;
    }

    private static String validateSnowflake(String value, String path) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (!SNOWFLAKE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(path + " must be a numeric Discord snowflake ID");
        }
        return trimmed;
    }
}
