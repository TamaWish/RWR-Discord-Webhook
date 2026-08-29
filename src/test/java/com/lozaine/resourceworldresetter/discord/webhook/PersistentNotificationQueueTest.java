package com.lozaine.resourceworldresetter.discord.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
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
                WebhookEventCategory.SUCCESS,
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
        assertThat(queue.offer(PendingNotification.create(WebhookEventCategory.SUCCESS, "a", "{}")))
                .isTrue();
        assertThat(queue.offer(PendingNotification.create(WebhookEventCategory.FAILURE, "b", "{}")))
                .isTrue();
        assertThat(queue.offer(PendingNotification.create(WebhookEventCategory.WARNING, "c", "{}")))
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
                WebhookEventCategory.SUCCESS,
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
}
