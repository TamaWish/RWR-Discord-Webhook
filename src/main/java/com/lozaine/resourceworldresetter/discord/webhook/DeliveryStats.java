package com.lozaine.resourceworldresetter.discord.webhook;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe delivery statistics for status reporting. */
public final class DeliveryStats {
    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicInteger sessionRetries = new AtomicInteger();
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailure = new AtomicReference<>();
    private final AtomicReference<String> lastFailureDetail = new AtomicReference<>();

    void incrementQueue() {
        queueSize.incrementAndGet();
    }

    void decrementQueue() {
        queueSize.updateAndGet(v -> Math.max(0, v - 1));
    }

    void recordSuccess() {
        lastSuccess.set(Instant.now());
    }

    void recordFailure(String detail) {
        lastFailure.set(Instant.now());
        lastFailureDetail.set(detail == null ? "" : detail);
    }

    void recordRetry() {
        sessionRetries.incrementAndGet();
    }

    public int queueSize() {
        return queueSize.get();
    }

    public int sessionRetries() {
        return sessionRetries.get();
    }

    public Optional<Instant> lastSuccess() {
        return Optional.ofNullable(lastSuccess.get());
    }

    public Optional<Instant> lastFailure() {
        return Optional.ofNullable(lastFailure.get());
    }

    public Optional<String> lastFailureDetail() {
        return Optional.ofNullable(lastFailureDetail.get()).filter(s -> !s.isEmpty());
    }
}
