package io.github.tamawish.rwr.discord.webhook;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.tamawish.rwr.discord.RwrDiscordPlugin;
import io.github.tamawish.rwr.discord.config.DiscordConfig;
import io.github.tamawish.rwr.discord.locale.LocaleService;
import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import io.github.tamawish.rwr.discord.notification.PayloadBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
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
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final RwrDiscordPlugin plugin;
    private final DiscordConfig config;
    private final LocaleService locale;
    private final EmbedBuilder embeds;
    private final PayloadBuilder payloads;
    private final Gson gson = new Gson();
    private final PersistentNotificationQueue queue;
    private final ExecutorService worker;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean prepared = new AtomicBoolean();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final ConcurrentLinkedQueue<PendingNotification> admissions = new ConcurrentLinkedQueue<>();
    private final AtomicInteger admissionCount = new AtomicInteger();
    private final DeliveryStats stats;
    private final BooleanSupplier deliveryAllowed;
    private final Object wakeLock = new Object();
    private volatile long lastDeliveryEpochMs;
    private volatile HttpURLConnection activeConnection;

    public WebhookService(RwrDiscordPlugin plugin, DiscordConfig config, LocaleService locale) {
        this(plugin, config, locale, new DeliveryStats(), () -> true);
        start();
    }

    public WebhookService(
            RwrDiscordPlugin plugin, DiscordConfig config, LocaleService locale, DeliveryStats stats) {
        this(plugin, config, locale, stats, () -> true);
    }

    public WebhookService(
            RwrDiscordPlugin plugin,
            DiscordConfig config,
            LocaleService locale,
            DeliveryStats stats,
            BooleanSupplier deliveryAllowed) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.deliveryAllowed = Objects.requireNonNull(deliveryAllowed, "deliveryAllowed");
        this.embeds = new EmbedBuilder(locale, config);
        this.payloads = new PayloadBuilder(config);
        this.queue = new PersistentNotificationQueue(
                plugin.getDataFolder().toPath(),
                config.queueCapacity(),
                config.queueTtlMs(),
                plugin.getLogger(),
                false);
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rwr-discord-webhook");
            t.setDaemon(true);
            return t;
        });
    }

    /** Activates this prepared service after the previous worker has stopped and flushed. */
    public void prepare() {
        if (prepared.compareAndSet(false, true)) {
            queue.reloadFromDisk();
            queue.lastPersistenceError().ifPresent(detail -> {
                String safeDetail = redact(detail);
                stats.recordFailure("queue persistence: " + safeDetail);
                throw new IllegalStateException("Durable webhook queue is unavailable: " + safeDetail);
            });
        }
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            prepare();
            running.set(true);
            worker.submit(this::drainLoop);
        }
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
        return queue.size() + admissionCount.get();
    }

    /** Wakes the worker after the optional RWR API becomes available or unavailable. */
    public void availabilityChanged() {
        wakeWorker();
    }

    /**
     * Enqueues a pre-built embed for asynchronous delivery. Safe to call from any thread; never
     * performs HTTP on the caller thread.
     */
    public void enqueue(NotificationCategory category, String operationId, JsonObject embed) {
        if (!accepting.get() || !running.get()) {
            return;
        }
        if (!isConfigured()) {
            return;
        }

        String bodyJson = payloads.build(category, embed);
        PendingNotification notification = PendingNotification.create(category, operationId, bodyJson);
        int admitted = admissionCount.incrementAndGet();
        if (queue.size() + admitted > config.queueCapacity()) {
            admissionCount.decrementAndGet();
            plugin.getLogger()
                    .warning(locale.format(
                            "log.queue-full",
                            "category",
                            category.name(),
                            "operation",
                            operationId));
            return;
        }
        admissions.offer(notification);
        wakeWorker();
    }

    /**
     * Stops accepting new work, persists the queue, and waits briefly for the worker to finish the
     * current attempt. Remaining notifications stay on disk for the next start.
     */
    public void shutdown() {
        accepting.set(false);
        running.set(false);
        wakeWorker();
        recordFlushFailure();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(SHUTDOWN_DRAIN_MS, TimeUnit.MILLISECONDS)) {
                disconnectActiveRequest();
                worker.shutdownNow();
                worker.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            disconnectActiveRequest();
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            disconnectActiveRequest();
            recordFlushFailure();
        }
    }

    private void wakeWorker() {
        synchronized (wakeLock) {
            wakeLock.notifyAll();
        }
    }

    private void drainLoop() {
        while (running.get() || !admissions.isEmpty() || queue.size() > 0) {
            try {
                drainAdmissions();
                if (!isConfigured()) {
                    if (!running.get() && admissions.isEmpty()) {
                        break;
                    }
                    synchronized (wakeLock) {
                        wakeLock.wait(500L);
                    }
                    continue;
                }
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
                if (!deliveryAllowed.getAsBoolean()
                        && notification.category() != NotificationCategory.CONFIGURATION) {
                    if (!running.get()) {
                        break;
                    }
                    synchronized (wakeLock) {
                        wakeLock.wait(500L);
                    }
                    continue;
                }
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

    private void drainAdmissions() {
        PendingNotification notification;
        while ((notification = admissions.poll()) != null) {
            admissionCount.decrementAndGet();
            if (queue.offer(notification)) {
                stats.incrementQueue();
            } else {
                String detail = queue.lastPersistenceError().orElse("durable queue is full");
                stats.recordFailure("queue persistence: " + redact(detail));
                plugin.getLogger().warning("Webhook notification was rejected: " + redact(detail));
            }
        }
    }

    private void recordFlushFailure() {
        if (!queue.flush()) {
            String detail = queue.lastPersistenceError().orElse("unknown persistence failure");
            stats.recordFailure("queue persistence: " + redact(detail));
        }
    }

    private void recordPersistenceFailure() {
        String detail = queue.lastPersistenceError().orElse("unknown persistence failure");
        stats.recordFailure("queue persistence: " + redact(detail));
        try {
            Thread.sleep(1_000L);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
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
                if (!queue.remove(notification.id())) {
                    recordPersistenceFailure();
                    return;
                }
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
                if (!queue.remove(notification.id())) {
                    recordPersistenceFailure();
                    return;
                }
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
            if (!queue.remove(notification.id())) {
                recordPersistenceFailure();
                return;
            }
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

        long delayMs = retryDelayMs(result, nextAttempt);

        long now = System.currentTimeMillis();
        long nextAt = delayMs > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + delayMs;
        PendingNotification retry = new PendingNotification(
                notification.id(),
                notification.category(),
                notification.operationId(),
                notification.bodyJson(),
                notification.createdAtEpochMs(),
                nextAttempt,
                nextAt,
                detail);
        if (!queue.update(retry)) {
            recordPersistenceFailure();
            return;
        }

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

    static long retryDelayMs(DeliveryResult result, int attemptNumber) {
        if (result.httpStatus() == 429) {
            return result.retryAfterMs();
        }
        long ordinaryDelay = result.retryAfterMs() > 0L
                ? result.retryAfterMs()
                : computeBackoffMs(attemptNumber);
        return Math.min(ordinaryDelay, MAX_BACKOFF_MS);
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
        activeConnection = connection;
        try {
            connection.setConnectTimeout(config.timeoutMs());
            connection.setReadTimeout(config.timeoutMs());
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "RWR-Discord-Webhook/1.0");
            connection.setDoOutput(true);
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            int code = connection.getResponseCode();
            String retryAfterHeader = connection.getHeaderField("Retry-After");
            try (InputStream stream =
                    code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
                if (stream != null) {
                    byte[] buffer = new byte[4096];
                    int remaining = MAX_RESPONSE_BYTES;
                    while (remaining > 0) {
                        int read = stream.read(buffer, 0, Math.min(buffer.length, remaining));
                        if (read < 0) {
                            break;
                        }
                        remaining -= read;
                    }
                }
            } catch (IOException ignored) {
                // Response bodies are optional.
            }
            return classifyHttpResponse(code, retryAfterHeader, System.currentTimeMillis());
        } finally {
            if (activeConnection == connection) {
                activeConnection = null;
            }
            connection.disconnect();
        }
    }

    static DeliveryResult classifyHttpResponse(int code, String retryAfterHeader, long nowEpochMs) {
        if (code >= 200 && code < 300) {
            return DeliveryResult.success(code);
        }
        if (code == 429) {
            ParsedRetryAfter parsed = parseRetryAfter(retryAfterHeader, nowEpochMs);
            long retryMs = parsed.valid() ? parsed.delayMs() : 1_000L;
            return DeliveryResult.retryable(
                    429, retryMs, "HTTP 429 rate limited");
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
        return parseRetryAfterMs(header, System.currentTimeMillis());
    }

    static long parseRetryAfterMs(String header, long nowEpochMs) {
        return parseRetryAfter(header, nowEpochMs).delayMs();
    }

    private static ParsedRetryAfter parseRetryAfter(String header, long nowEpochMs) {
        if (header == null || header.isBlank()) {
            return new ParsedRetryAfter(false, 0L);
        }
        String value = header.trim();
        try {
            if (value.matches("\\d+(\\.\\d+)?")) {
                double seconds = Double.parseDouble(value);
                return new ParsedRetryAfter(true, Math.max(0L, Math.round(seconds * 1_000.0d)));
            }
        } catch (NumberFormatException ignored) {
            // Fall through.
        }
        try {
            long when = java.time.ZonedDateTime.parse(
                            value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli();
            return new ParsedRetryAfter(true, Math.max(0L, when - nowEpochMs));
        } catch (RuntimeException ignored) {
            return new ParsedRetryAfter(false, 0L);
        }
    }

    private record ParsedRetryAfter(boolean valid, long delayMs) {}

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

    private void disconnectActiveRequest() {
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }
}
