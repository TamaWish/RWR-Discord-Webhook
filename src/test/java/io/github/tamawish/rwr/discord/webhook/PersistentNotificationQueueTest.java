package io.github.tamawish.rwr.discord.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tamawish.rwr.discord.notification.NotificationCategory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.RandomAccessFile;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistentNotificationQueueTest {
    @TempDir
    Path temp;

    @Test
    void persistsWithoutWebhookSecretAndResumes() throws Exception {
        Logger logger = Logger.getLogger("test");
        PersistentNotificationQueue queue =
                new PersistentNotificationQueue(temp, 500, WebhookService.DEFAULT_TTL_MS, logger);
        PendingNotification notification = PendingNotification.create(
                NotificationCategory.SUCCESS,
                "op-1",
                "{\"content\":\"hello\"}");
        assertThat(queue.offer(notification)).isTrue();
        assertThat(queue.size()).isEqualTo(1);
        queue.flush();

        String onDisk = Files.readString(temp.resolve("pending-webhooks.json"));
        assertThat(onDisk).doesNotContain("/api/webhooks/");
        assertThat(onDisk).contains("op-1");
        assertThat(onDisk).contains("hello");

        PersistentNotificationQueue resumed =
                new PersistentNotificationQueue(temp, 500, WebhookService.DEFAULT_TTL_MS, logger);
        assertThat(resumed.size()).isEqualTo(1);
        assertThat(resumed.peekReady(System.currentTimeMillis())).isPresent();
    }

    @Test
    void rejectsWhenAtCapacity() {
        Logger logger = Logger.getLogger("test");
        PersistentNotificationQueue queue =
                new PersistentNotificationQueue(temp, 2, WebhookService.DEFAULT_TTL_MS, logger);
        assertThat(queue.offer(PendingNotification.create(NotificationCategory.SUCCESS, "a", "{}")))
                .isTrue();
        assertThat(queue.offer(PendingNotification.create(NotificationCategory.FAILURE, "b", "{}")))
                .isTrue();
        assertThat(queue.offer(PendingNotification.create(NotificationCategory.WARNING, "c", "{}")))
                .isFalse();
        assertThat(queue.size()).isEqualTo(2);
    }

    @Test
    void purgesExpiredEntries() {
        Logger logger = Logger.getLogger("test");
        long ttl = 1_000L;
        PersistentNotificationQueue queue = new PersistentNotificationQueue(temp, 10, ttl, logger);
        PendingNotification old = new PendingNotification(
                "old",
                NotificationCategory.SUCCESS,
                "op",
                "{}",
                System.currentTimeMillis() - 60_000L,
                0,
                System.currentTimeMillis() - 60_000L,
                "");
        assertThat(queue.offer(old)).isTrue();
        // peekReady triggers purge
        assertThat(queue.peekReady(System.currentTimeMillis())).isEmpty();
        assertThat(queue.size()).isZero();
    }

    @Test
    void futureRetryRemainsDurableAcrossRestart() {
        Logger logger = Logger.getLogger("test");
        long now = System.currentTimeMillis();
        PendingNotification future = new PendingNotification(
                "future",
                NotificationCategory.WARNING,
                "warning:resource:123:10",
                "{}",
                now,
                1,
                now + 600_000L,
                "HTTP 429 rate limited");
        PersistentNotificationQueue queue =
                new PersistentNotificationQueue(temp, 10, WebhookService.DEFAULT_TTL_MS, logger);
        assertThat(queue.offer(future)).isTrue();
        queue.flush();

        PersistentNotificationQueue resumed =
                new PersistentNotificationQueue(temp, 10, WebhookService.DEFAULT_TTL_MS, logger);

        assertThat(resumed.size()).isEqualTo(1);
        assertThat(resumed.peekReady(now)).isEmpty();
        assertThat(resumed.peekReady(now + 600_000L)).isPresent();
    }

    @Test
    void oversizedQueueFileIsRejectedWithoutReadingIt() throws Exception {
        Path file = temp.resolve("pending-webhooks.json");
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength(8L * 1024L * 1024L + 1L);
        }

        PersistentNotificationQueue queue = new PersistentNotificationQueue(
                temp, 10, WebhookService.DEFAULT_TTL_MS, Logger.getLogger("test"));

        assertThat(queue.size()).isZero();
        assertThat(queue.lastPersistenceError()).isPresent();
    }
}
