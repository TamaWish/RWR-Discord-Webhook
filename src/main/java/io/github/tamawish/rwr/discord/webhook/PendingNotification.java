package io.github.tamawish.rwr.discord.webhook;

import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import java.util.Objects;
import java.util.UUID;

/**
 * One outbound Discord webhook notification held in the durable queue.
 *
 * <p>The JSON body is the Discord payload only. The webhook URL/secret is never stored here or in
 * the queue file; it is always read from live configuration at delivery time.
 */
public final class PendingNotification {
    private final String id;
    private final NotificationCategory category;
    private final String operationId;
    private final String bodyJson;
    private final long createdAtEpochMs;
    private int attempts;
    private long nextAttemptEpochMs;
    private String lastError;

    public PendingNotification(
            String id,
            NotificationCategory category,
            String operationId,
            String bodyJson,
            long createdAtEpochMs,
            int attempts,
            long nextAttemptEpochMs,
            String lastError) {
        this.id = Objects.requireNonNull(id, "id");
        this.category = Objects.requireNonNull(category, "category");
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.bodyJson = Objects.requireNonNull(bodyJson, "bodyJson");
        this.createdAtEpochMs = createdAtEpochMs;
        this.attempts = Math.max(0, attempts);
        this.nextAttemptEpochMs = nextAttemptEpochMs;
        this.lastError = lastError == null ? "" : lastError;
    }

    public static PendingNotification create(
            NotificationCategory category, String operationId, String bodyJson) {
        long now = System.currentTimeMillis();
        return new PendingNotification(
                UUID.randomUUID().toString(),
                category,
                operationId,
                bodyJson,
                now,
                0,
                now,
                "");
    }

    public String id() {
        return id;
    }

    public NotificationCategory category() {
        return category;
    }

    public String operationId() {
        return operationId;
    }

    public String bodyJson() {
        return bodyJson;
    }

    public long createdAtEpochMs() {
        return createdAtEpochMs;
    }

    public int attempts() {
        return attempts;
    }

    public long nextAttemptEpochMs() {
        return nextAttemptEpochMs;
    }

    public String lastError() {
        return lastError;
    }

    public void markAttempt(int attemptNumber, long nextAttemptEpochMs, String error) {
        this.attempts = attemptNumber;
        this.nextAttemptEpochMs = nextAttemptEpochMs;
        this.lastError = error == null ? "" : error;
    }

    public boolean isExpired(long nowEpochMs, long ttlMs) {
        return nowEpochMs - createdAtEpochMs >= ttlMs;
    }

    public boolean isReady(long nowEpochMs) {
        return nowEpochMs >= nextAttemptEpochMs;
    }
}
