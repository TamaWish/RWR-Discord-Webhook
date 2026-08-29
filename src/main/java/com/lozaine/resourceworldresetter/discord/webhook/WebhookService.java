package com.lozaine.resourceworldresetter.discord.webhook;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lozaine.resourceworldresetter.discord.RwrDiscordPlugin;
import com.lozaine.resourceworldresetter.discord.config.DiscordConfig;
import com.lozaine.resourceworldresetter.discord.locale.LocaleService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Asynchronous Discord webhook delivery that never blocks a Bukkit or Folia server thread.
 *
 * <p>Reliability policy (v1):
 *
 * <ul>
 *   <li>bounded durable queue (default 500), 24-hour entry expiry
 *   <li>up to 8 delivery attempts
 *   <li>exponential backoff with jitter, capped at five minutes
 *   <li>exact {@code Retry-After} handling for HTTP 429
 *   <li>retries for network errors and HTTP 5xx; no retries for other HTTP 4xx
 *   <li>queue file stores payload JSON only — never the webhook secret
 *   <li>graceful drain on disable; pending work resumes after restart
 * </ul>
 */
public final class WebhookService {
    /** Hard reliability defaults (also exposed via config with these floors/caps). */
    public static final int DEFAULT_QUEUE_CAPACITY = 500;
    public static final int DEFAULT_MAX_ATTEMPTS = 8;
    public static final long DEFAULT_TTL_MS = TimeUnit.HOURS.toMillis(24);
    public static final long MAX_BACKOFF_MS = TimeUnit.MINUTES.toMillis(5);
    public static final long SHUTDOWN_DRAIN_MS = 8_000L;

    private static final Pattern WEBHOOK_SECRET = Pattern.compile(
            "(https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/)\\S+",
            Pattern.CASE_INSENSITIVE);

    private final RwrDiscordPlugin plugin;
    private final DiscordConfig config;
    private final LocaleService locale;
    private final EmbedBuilder embeds;
    private final Gson gson = new Gson();
    private final PersistentNotificationQueue queue;
    private final ExecutorService worker;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final DeliveryStats stats = new DeliveryStats();
    private final Object wakeLock = new Object();
    private volatile long lastDeliveryEpochMs;

    public WebhookService(RwrDiscordPlugin plugin, DiscordConfig config, LocaleService locale) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.embeds = new EmbedBuilder(locale, config);
        this.queue = new PersistentNotificationQueue(
                plugin.getDataFolder().toPath(),
                config.queueCapacity(),
                config.queueTtlMs(),
                plugin.getLogger());
        // Sync in-memory queue size into stats for status command.
        for (int i = 0; i < queue.size(); i++) {
            stats.incrementQueue();
        }
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rwr-discord-webhook");
            t.setDaemon(true);
            return t;
        });
        worker.submit(this::drainLoop);
    }

    public boolean isConfigured() {
        return config.hasWebhookUrl();
    }

    public DeliveryStats stats() {
        return stats;
    }

    public EmbedBuilder embeds() {
        return embeds;
    }

    public int pendingCount() {
        return queue.size();
    }

    /**
     * Enqueues a pre-built embed for asynchronous delivery. Safe to call from any thread; never
     * performs HTTP on the caller thread.
     */
    public void enqueue(WebhookEventCategory category, String operationId, JsonObject embed) {
        if (!running.get()) {
            return;
        }
        if (!isConfigured()) {
            return;
        }

        JsonObject root = new JsonObject();
        if (!config.username().isEmpty()) {
            root.addProperty("username", config.username());
        }
        if (!config.avatarUrl().isEmpty()) {
            root.addProperty("avatar_url", config.avatarUrl());
        }
        JsonObject allowed = new JsonObject();
        allowed.add("parse", new JsonArray());
        root.add("allowed_mentions", allowed);
        JsonArray embedArray = new JsonArray();
        embedArray.add(embed);
        root.add("embeds", embedArray);

        PendingNotification notification =
                PendingNotification.create(category, operationId, gson.toJson(root));
        if (!queue.offer(notification)) {
            plugin.getLogger()
                    .warning(locale.format(
                            "log.queue-full",
                            "category",
                            category.name(),
                            "operation",
                            operationId));
            return;
        }
        stats.incrementQueue();
        wakeWorker();
    }

    /**
     * Stops accepting new work, persists the queue, and waits briefly for the worker to finish the
     * current attempt. Remaining notifications stay on disk for the next start.
     */
    public void shutdown() {
        running.set(false);
        wakeWorker();
        queue.flush();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(SHUTDOWN_DRAIN_MS, TimeUnit.MILLISECONDS)) {
                worker.shutdownNow();
                worker.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
        queue.flush();
    }

    private void wakeWorker() {
        synchronized (wakeLock) {
            wakeLock.notifyAll();
        }
    }

    private void drainLoop() {
        while (running.get() || queue.size() > 0) {
            try {
                long now = System.currentTimeMillis();
                var ready = queue.peekReady(now);
                if (ready.isEmpty()) {
                    if (!running.get()) {
                        break;
                    }
                    synchronized (wakeLock) {
                        wakeLock.wait(500L);
                    }
                    continue;
                }
                PendingNotification notification = ready.get();
                if (!running.get() && !notification.isReady(System.currentTimeMillis())) {
                    // On shutdown only finish currently-due work; leave future retries for restart.
                    break;
                }
                processOne(notification);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Webhook worker error", ex);
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processOne(PendingNotification notification) {
        if (!isConfigured()) {
            // Keep on disk; do not burn attempts while misconfigured.
            return;
        }
        try {
            respectMinInterval();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }

        DeliveryResult result;
        try {
            result = post(notification.bodyJson());
        } catch (IOException ex) {
            result = DeliveryResult.retryable(0, 0L, "network: " + safeMessage(ex));
        } catch (RuntimeException ex) {
            result = DeliveryResult.retryable(0, 0L, "error: " + safeMessage(ex));
        }

        switch (result.kind()) {
            case SUCCESS -> {
                queue.remove(notification.id());
                stats.decrementQueue();
                stats.recordSuccess();
                plugin.getLogger()
                        .fine(locale.format(
                                "log.delivery-success",
                                "category",
                                notification.category().name(),
                                "operation",
                                notification.operationId()));
            }
            case PERMANENT -> {
                queue.remove(notification.id());
                stats.decrementQueue();
                String detail = redact(result.detail());
                stats.recordFailure(detail);
                plugin.getLogger()
                        .warning(locale.format(
                                "log.delivery-failed",
                                "category",
                                notification.category().name(),
                                "detail",
                                detail));
            }
            case RETRYABLE -> handleRetryable(notification, result);
        }
    }

    private void handleRetryable(PendingNotification notification, DeliveryResult result) {
        int nextAttempt = notification.attempts() + 1;
        stats.recordRetry();
        String detail = redact(result.detail());

        if (nextAttempt >= config.maxAttempts()) {
            queue.remove(notification.id());
            stats.decrementQueue();
            stats.recordFailure(detail);
            plugin.getLogger()
                    .warning(locale.format(
                            "log.delivery-failed",
                            "category",
                            notification.category().name(),
                            "detail",
                            detail));
            return;
        }

        long delayMs;
        if (result.httpStatus() == 429 && result.retryAfterMs() > 0L) {
            // Exact Retry-After for Discord rate limits.
            delayMs = result.retryAfterMs();
        } else if (result.retryAfterMs() > 0L) {
            delayMs = result.retryAfterMs();
        } else {
            delayMs = computeBackoffMs(nextAttempt);
        }
        delayMs = Math.min(delayMs, MAX_BACKOFF_MS);

        long nextAt = System.currentTimeMillis() + delayMs;
        notification.markAttempt(nextAttempt, nextAt, detail);
        queue.update(notification);

        // Sleep off-thread so we honour Retry-After without busy-spinning.
        try {
            Thread.sleep(Math.min(delayMs, 1_000L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Exponential backoff with full jitter: random in {@code [0, min(cap, base * 2^(attempt-1))]}.
     */
    static long computeBackoffMs(int attemptNumber) {
        int attempt = Math.max(1, attemptNumber);
        long exp = 1L << Math.min(attempt - 1, 16);
        long window = Math.min(MAX_BACKOFF_MS, 1_000L * exp);
        if (window <= 0L) {
            return 1_000L;
        }
        return ThreadLocalRandom.current().nextLong(window + 1L);
    }

    private void respectMinInterval() throws InterruptedException {
        int minSeconds = config.minIntervalSeconds();
        if (minSeconds <= 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - lastDeliveryEpochMs;
        long wait = (minSeconds * 1_000L) - elapsed;
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastDeliveryEpochMs = System.currentTimeMillis();
    }

    private DeliveryResult post(String jsonBody) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(config.webhookUrl()).toURL().openConnection();
        connection.setConnectTimeout(config.timeoutMs());
        connection.setReadTimeout(config.timeoutMs());
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("User-Agent", "RWR-Discord/1.0");
        connection.setDoOutput(true);
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }

        int code = connection.getResponseCode();
        String retryAfterHeader = connection.getHeaderField("Retry-After");
        // Consume body to allow connection reuse / clean close.
        try (InputStream stream =
                code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            if (stream != null) {
                stream.readAllBytes();
            }
        } catch (IOException ignored) {
            // Body optional.
        }
        connection.disconnect();

        if (code >= 200 && code < 300) {
            return DeliveryResult.success(code);
        }
        if (code == 429) {
            long retryMs = parseRetryAfterMs(retryAfterHeader);
            if (retryMs <= 0L) {
                retryMs = 1_000L;
            }
            retryMs = Math.min(retryMs, MAX_BACKOFF_MS);
            return DeliveryResult.retryable(429, retryMs, "HTTP 429 rate limited");
        }
        if (code >= 500 && code <= 599) {
            return DeliveryResult.retryable(code, 0L, "HTTP " + code);
        }
        if (code >= 400 && code <= 499) {
            return DeliveryResult.permanent(code, "HTTP " + code);
        }
        return DeliveryResult.retryable(code, 0L, "HTTP " + code);
    }

    /**
     * Parses Discord {@code Retry-After} which may be seconds (integer/decimal) or an HTTP-date.
     * Returns milliseconds, or 0 when unknown.
     */
    static long parseRetryAfterMs(String header) {
        if (header == null || header.isBlank()) {
            return 0L;
        }
        String value = header.trim();
        try {
            if (value.matches("\\d+(\\.\\d+)?")) {
                double seconds = Double.parseDouble(value);
                return Math.max(0L, Math.round(seconds * 1_000.0d));
            }
        } catch (NumberFormatException ignored) {
            // Fall through.
        }
        try {
            long when = java.time.ZonedDateTime.parse(
                            value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli();
            return Math.max(0L, when - System.currentTimeMillis());
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }

    /** Redacts webhook URLs and tokens from log-facing text. */
    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return WEBHOOK_SECRET.matcher(input).replaceAll("$1[redacted]");
    }
}
