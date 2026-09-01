package io.github.tamawish.rwr.discord.webhook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded durable notification queue.
 *
 * <p>Persists payload JSON only — never the webhook URL or token. Oldest entries are dropped when
 * capacity is exceeded; entries older than the TTL are discarded on load and during drain.
 */
public final class PersistentNotificationQueue {
    private static final String QUEUE_FILE = "pending-webhooks.json";
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_QUEUE_FILE_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_PAYLOAD_CHARS = 256 * 1024;

    private final Path file;
    private final int capacity;
    private final long ttlMs;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final ReentrantLock lock = new ReentrantLock();
    private final List<PendingNotification> items = new ArrayList<>();
    private volatile String lastPersistenceError = "";

    public PersistentNotificationQueue(Path dataFolder, int capacity, long ttlMs, Logger logger) {
        this(dataFolder, capacity, ttlMs, logger, true);
    }

    PersistentNotificationQueue(
            Path dataFolder, int capacity, long ttlMs, Logger logger, boolean loadImmediately) {
        this.file = Objects.requireNonNull(dataFolder, "dataFolder").resolve(QUEUE_FILE);
        this.capacity = Math.max(1, capacity);
        this.ttlMs = Math.max(1L, ttlMs);
        this.logger = Objects.requireNonNull(logger, "logger");
        if (loadImmediately) {
            load();
        }
    }

    /** Attempts to enqueue. Returns false when the queue is at capacity after expiry cleanup. */
    public boolean offer(PendingNotification notification) {
        lock.lock();
        try {
            purgeExpiredLocked(System.currentTimeMillis());
            if (items.size() >= capacity) {
                return false;
            }
            items.add(notification);
            if (persistLocked()) {
                return true;
            }
            items.remove(notification);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the next ready notification without removing it, ordered by {@code nextAttemptEpochMs}.
     */
    public Optional<PendingNotification> peekReady(long nowEpochMs) {
        lock.lock();
        try {
            purgeExpiredLocked(nowEpochMs);
            return items.stream()
                    .filter(n -> n.isReady(nowEpochMs))
                    .min(Comparator.comparingLong(PendingNotification::nextAttemptEpochMs));
        } finally {
            lock.unlock();
        }
    }

    /** Removes a notification by id after successful delivery or permanent failure. */
    public boolean remove(String id) {
        lock.lock();
        try {
            for (int i = 0; i < items.size(); i++) {
                PendingNotification removed = items.get(i);
                if (removed.id().equals(id)) {
                    items.remove(i);
                    if (!persistLocked()) {
                        items.add(i, removed);
                    }
                    return !items.contains(removed);
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** Updates retry metadata for an existing notification and persists. */
    public boolean update(PendingNotification notification) {
        lock.lock();
        try {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id().equals(notification.id())) {
                    PendingNotification previous = items.get(i);
                    items.set(i, notification);
                    if (!persistLocked()) {
                        items.set(i, previous);
                        return false;
                    }
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return items.size();
        } finally {
            lock.unlock();
        }
    }

    /** Forces a persist of the current in-memory state (e.g. during graceful shutdown). */
    public boolean flush() {
        lock.lock();
        try {
            return persistLocked();
        } finally {
            lock.unlock();
        }
    }

    public Optional<String> lastPersistenceError() {
        return lastPersistenceError.isBlank() ? Optional.empty() : Optional.of(lastPersistenceError);
    }

    /** Replaces the in-memory snapshot with the latest durable state. */
    void reloadFromDisk() {
        load();
    }

    private void purgeExpiredLocked(long nowEpochMs) {
        Iterator<PendingNotification> it = items.iterator();
        int purged = 0;
        while (it.hasNext()) {
            if (it.next().isExpired(nowEpochMs, ttlMs)) {
                it.remove();
                purged++;
            }
        }
        if (purged > 0) {
            logger.info("Purged " + purged + " expired webhook notification(s) from durable queue");
            persistLocked();
        }
    }

    private void load() {
        lock.lock();
        try {
            items.clear();
            if (!Files.isRegularFile(file)) {
                lastPersistenceError = "";
                return;
            }
            if (Files.size(file) > MAX_QUEUE_FILE_BYTES) {
                throw new IOException(QUEUE_FILE + " exceeds " + MAX_QUEUE_FILE_BYTES + " bytes");
            }
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                lastPersistenceError = "";
                return;
            }
            // Reject files that accidentally contain a webhook URL/secret.
            if (raw.toLowerCase().contains("/api/webhooks/")) {
                logger.severe(
                        "pending-webhooks.json appears to contain a webhook URL; refusing to load. "
                                + "Delete the file if it was written by a third-party tool.");
                lastPersistenceError = "queue file contained a webhook URL pattern";
                return;
            }
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray array = root.has("notifications") ? root.getAsJsonArray("notifications") : new JsonArray();
            long now = System.currentTimeMillis();
            int loaded = 0;
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                PendingNotification notification = fromJson(element.getAsJsonObject());
                if (notification == null) {
                    continue;
                }
                if (notification.isExpired(now, ttlMs)) {
                    continue;
                }
                items.add(notification);
                loaded++;
                if (items.size() >= capacity) {
                    break;
                }
            }
            if (loaded > 0) {
                logger.info("Resumed " + loaded + " pending webhook notification(s) from durable queue");
            }
            persistLocked();
        } catch (IOException | RuntimeException ex) {
            logger.log(Level.WARNING, "Failed to load durable webhook queue; starting empty", ex);
            items.clear();
            lastPersistenceError = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private boolean persistLocked() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", FORMAT_VERSION);
            root.addProperty("savedAt", System.currentTimeMillis());
            JsonArray array = new JsonArray();
            for (PendingNotification notification : items) {
                array.add(toJson(notification));
            }
            root.add("notifications", array);
            String json = gson.toJson(root);
            // Defense in depth: never write a webhook secret even if body somehow contained one.
            if (json.toLowerCase().contains("/api/webhooks/")) {
                logger.severe("Refusing to persist queue: payload unexpectedly contains webhook URL pattern");
                lastPersistenceError = "queue payload contained a webhook URL pattern";
                return false;
            }
            Path temp = file.resolveSibling(QUEUE_FILE + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            lastPersistenceError = "";
            return true;
        } catch (IOException ex) {
            lastPersistenceError = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            logger.log(Level.WARNING, "Failed to persist durable webhook queue", ex);
            return false;
        }
    }

    private static JsonObject toJson(PendingNotification n) {
        JsonObject o = new JsonObject();
        o.addProperty("id", n.id());
        o.addProperty("category", n.category().name());
        o.addProperty("operationId", n.operationId());
        o.addProperty("body", n.bodyJson());
        o.addProperty("createdAt", n.createdAtEpochMs());
        o.addProperty("attempts", n.attempts());
        o.addProperty("nextAttemptAt", n.nextAttemptEpochMs());
        o.addProperty("lastError", n.lastError());
        return o;
    }

    private static PendingNotification fromJson(JsonObject o) {
        try {
            String id = o.get("id").getAsString();
            NotificationCategory category = NotificationCategory.valueOf(o.get("category").getAsString());
            String operationId = o.get("operationId").getAsString();
            String body = o.get("body").getAsString();
            if (body.length() > MAX_PAYLOAD_CHARS) {
                return null;
            }
            long createdAt = o.get("createdAt").getAsLong();
            int attempts = o.has("attempts") ? o.get("attempts").getAsInt() : 0;
            long nextAttempt = o.has("nextAttemptAt") ? o.get("nextAttemptAt").getAsLong() : createdAt;
            String lastError = o.has("lastError") ? o.get("lastError").getAsString() : "";
            return new PendingNotification(
                    id, category, operationId, body, createdAt, attempts, nextAttempt, lastError);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
