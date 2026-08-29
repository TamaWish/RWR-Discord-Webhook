package com.lozaine.resourceworldresetter.discord.locale;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Locale loader with English fallback.
 *
 * <p>Resolves keys from {@code locales/<code>.yml} in the data folder, then from the bundled
 * {@code locales/en_US.yml}, then from an empty string. Placeholders use {@code {name}} form.
 */
public final class LocaleService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    private static final String DEFAULT_LOCALE = "en_US";

    private final JavaPlugin plugin;
    private final Map<String, String> strings = new LinkedHashMap<>();
    private final Map<String, String> englishFallback = new LinkedHashMap<>();
    private String activeLocale = DEFAULT_LOCALE;

    public LocaleService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        loadBundledEnglish();
    }

    /** Reloads the active locale file, falling back to English for missing keys. */
    public void reload(String localeCode) {
        activeLocale = localeCode == null || localeCode.isBlank() ? DEFAULT_LOCALE : localeCode.trim();
        strings.clear();
        strings.putAll(englishFallback);

        File dataFile = new File(plugin.getDataFolder(), "locales/" + activeLocale + ".yml");
        if (!dataFile.exists() && DEFAULT_LOCALE.equals(activeLocale)) {
            plugin.saveResource("locales/en_US.yml", false);
        }
        if (dataFile.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            flatten(yaml, "", strings);
        } else if (!DEFAULT_LOCALE.equals(activeLocale)) {
            plugin.getLogger()
                    .warning("Locale file locales/" + activeLocale + ".yml not found; using English fallback.");
        }
    }

    private void loadBundledEnglish() {
        englishFallback.clear();
        try (InputStream in = plugin.getResource("locales/en_US.yml")) {
            if (in == null) {
                plugin.getLogger().warning("Bundled locales/en_US.yml is missing; locale fallbacks unavailable.");
                return;
            }
            YamlConfiguration yaml =
                    YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            flatten(yaml, "", englishFallback);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to read bundled English locale", ex);
        }
    }

    private static void flatten(ConfigurationSection section, String prefix, Map<String, String> target) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null) {
                    flatten(child, path, target);
                }
            } else {
                String value = section.getString(key);
                if (value != null) {
                    target.put(path, value);
                }
            }
        }
    }

    /** Returns the raw template for a key, or empty string when unknown. */
    public String raw(String key) {
        String value = strings.get(key);
        if (value != null) {
            return value;
        }
        return englishFallback.getOrDefault(key, "");
    }

    /**
     * Formats a locale key with alternating key/value placeholders.
     *
     * @param key locale key
     * @param placeholders alternating name/value pairs
     * @return formatted string with Discord mention markers stripped
     */
    public String format(String key, Object... placeholders) {
        String template = raw(key);
        if (template.isEmpty()) {
            return key;
        }
        Map<String, String> values = pairs(placeholders);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = values.getOrDefault(name, matcher.group(0));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return stripMentions(out.toString());
    }

    /**
     * Removes Discord mention syntax so translated/configured text cannot ping users or roles.
     */
    public static String stripMentions(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // <@123>, <@!123>, <@&123>, @everyone, @here
        return input
                .replaceAll("<@!?\\d+>", "[user]")
                .replaceAll("<@&\\d+>", "[role]")
                .replace("@everyone", "@\u200Beveryone")
                .replace("@here", "@\u200Bhere");
    }

    public String activeLocale() {
        return activeLocale;
    }

    private static Map<String, String> pairs(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be key/value pairs");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), String.valueOf(values[i + 1]));
        }
        return result;
    }
}
