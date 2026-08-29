package com.lozaine.resourceworldresetter.discord.config;

import com.lozaine.resourceworldresetter.discord.webhook.WebhookService;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable snapshot of RWR-Discord settings loaded from the add-on data folder. */
public final class DiscordConfig {
    private final String locale;
    private final String webhookUrl;
    private final String username;
    private final String avatarUrl;
    private final int timeoutMs;
    private final int queueCapacity;
    private final int maxAttempts;
    private final long queueTtlMs;
    private final int minIntervalSeconds;
    private final boolean sendWarnings;
    private final boolean sendSuccess;
    private final boolean sendFailures;
    private final boolean sendCancellations;
    private final boolean sendInterrupted;
    private final int warningColor;
    private final int successColor;
    private final int failureColor;
    private final int cancellationColor;
    private final int interruptedColor;

    private DiscordConfig(
            String locale,
            String webhookUrl,
            String username,
            String avatarUrl,
            int timeoutMs,
            int queueCapacity,
            int maxAttempts,
            long queueTtlMs,
            int minIntervalSeconds,
            boolean sendWarnings,
            boolean sendSuccess,
            boolean sendFailures,
            boolean sendCancellations,
            boolean sendInterrupted,
            int warningColor,
            int successColor,
            int failureColor,
            int cancellationColor,
            int interruptedColor) {
        this.locale = locale;
        this.webhookUrl = webhookUrl;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.timeoutMs = timeoutMs;
        this.queueCapacity = queueCapacity;
        this.maxAttempts = maxAttempts;
        this.queueTtlMs = queueTtlMs;
        this.minIntervalSeconds = minIntervalSeconds;
        this.sendWarnings = sendWarnings;
        this.sendSuccess = sendSuccess;
        this.sendFailures = sendFailures;
        this.sendCancellations = sendCancellations;
        this.sendInterrupted = sendInterrupted;
        this.warningColor = warningColor;
        this.successColor = successColor;
        this.failureColor = failureColor;
        this.cancellationColor = cancellationColor;
        this.interruptedColor = interruptedColor;
    }

    /** Loads and validates configuration from the plugin config file. */
    public static DiscordConfig load(FileConfiguration yaml) {
        Objects.requireNonNull(yaml, "yaml");
        String locale = normalizeLocale(yaml.getString("locale", "en_US"));
        String url = blankToEmpty(yaml.getString("webhook.url", ""));
        String username = blankToEmpty(yaml.getString("webhook.username", "RWR"));
        String avatar = blankToEmpty(yaml.getString("webhook.avatar-url", ""));
        int timeoutMs = Math.max(1_000, yaml.getInt("webhook.timeout-ms", 8_000));
        int queueCapacity = clamp(
                yaml.getInt("webhook.queue-capacity", WebhookService.DEFAULT_QUEUE_CAPACITY),
                1,
                WebhookService.DEFAULT_QUEUE_CAPACITY);
        // Prefer max-attempts; accept legacy max-retries (+1) for older configs.
        int maxAttempts;
        if (yaml.contains("webhook.max-attempts")) {
            maxAttempts = clamp(yaml.getInt("webhook.max-attempts"), 1, WebhookService.DEFAULT_MAX_ATTEMPTS);
        } else if (yaml.contains("webhook.max-retries")) {
            maxAttempts = clamp(yaml.getInt("webhook.max-retries") + 1, 1, WebhookService.DEFAULT_MAX_ATTEMPTS);
        } else {
            maxAttempts = WebhookService.DEFAULT_MAX_ATTEMPTS;
        }
        long ttlHours = Math.max(1L, yaml.getLong("webhook.queue-ttl-hours", 24L));
        long queueTtlMs = Math.min(ttlHours, 24L) * TimeUnit.HOURS.toMillis(1);
        int minInterval = Math.max(0, yaml.getInt("webhook.min-interval-seconds", 1));

        boolean warnings = yaml.getBoolean("events.warnings", true);
        boolean success = yaml.getBoolean("events.success", true);
        boolean failures = yaml.getBoolean("events.failures", true);
        boolean cancellations = yaml.getBoolean("events.cancellations", true);
        boolean interrupted = yaml.getBoolean("events.interrupted", true);

        int warningColor = yaml.getInt("embeds.warning-color", 0xFFFF00);
        int successColor = yaml.getInt("embeds.success-color", 0x57F287);
        int failureColor = yaml.getInt("embeds.failure-color", 0xED4245);
        int cancellationColor = yaml.getInt("embeds.cancellation-color", 0xE67E22);
        int interruptedColor = yaml.getInt("embeds.interrupted-color", 0x9B59B6);

        return new DiscordConfig(
                locale,
                url,
                username,
                avatar,
                timeoutMs,
                queueCapacity,
                maxAttempts,
                queueTtlMs,
                minInterval,
                warnings,
                success,
                failures,
                cancellations,
                interrupted,
                warningColor,
                successColor,
                failureColor,
                cancellationColor,
                interruptedColor);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeLocale(String raw) {
        if (raw == null || raw.isBlank()) {
            return "en_US";
        }
        String cleaned = raw.trim().replace('-', '_');
        int underscore = cleaned.indexOf('_');
        if (underscore > 0 && underscore < cleaned.length() - 1) {
            return cleaned.substring(0, underscore).toLowerCase(Locale.ROOT)
                    + "_"
                    + cleaned.substring(underscore + 1).toUpperCase(Locale.ROOT);
        }
        return cleaned;
    }

    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    public String locale() {
        return locale;
    }

    public String webhookUrl() {
        return webhookUrl;
    }

    public boolean hasWebhookUrl() {
        return !webhookUrl.isEmpty();
    }

    public String username() {
        return username;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    /** Total delivery attempts per notification (including the first try). */
    public int maxAttempts() {
        return maxAttempts;
    }

    /** @deprecated use {@link #maxAttempts()} */
    @Deprecated
    public int maxRetries() {
        return Math.max(0, maxAttempts - 1);
    }

    public long queueTtlMs() {
        return queueTtlMs;
    }

    public int minIntervalSeconds() {
        return minIntervalSeconds;
    }

    public boolean sendWarnings() {
        return sendWarnings;
    }

    public boolean sendSuccess() {
        return sendSuccess;
    }

    public boolean sendFailures() {
        return sendFailures;
    }

    public boolean sendCancellations() {
        return sendCancellations;
    }

    public boolean sendInterrupted() {
        return sendInterrupted;
    }

    public int warningColor() {
        return warningColor;
    }

    public int successColor() {
        return successColor;
    }

    public int failureColor() {
        return failureColor;
    }

    public int cancellationColor() {
        return cancellationColor;
    }

    public int interruptedColor() {
        return interruptedColor;
    }
}
