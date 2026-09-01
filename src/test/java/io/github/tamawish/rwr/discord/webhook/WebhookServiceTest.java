package io.github.tamawish.rwr.discord.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

class WebhookServiceTest {
    @Test
    void redactMasksWebhookToken() {
        String raw = "POST failed https://discord.com/api/webhooks/1234567890/super-secret-token ABC";
        String redacted = WebhookService.redact(raw);
        assertThat(redacted).contains("https://discord.com/api/webhooks/1234567890/[redacted]");
        assertThat(redacted).doesNotContain("super-secret-token");
    }

    @Test
    void redactLeavesNonWebhookTextAlone() {
        assertThat(WebhookService.redact("plain error")).isEqualTo("plain error");
        assertThat(WebhookService.redact(null)).isEmpty();
    }

    @Test
    void parseRetryAfterAcceptsSeconds() {
        assertThat(WebhookService.parseRetryAfterMs("5")).isEqualTo(5_000L);
        assertThat(WebhookService.parseRetryAfterMs("1.5")).isEqualTo(1_500L);
        assertThat(WebhookService.parseRetryAfterMs("")).isZero();
        assertThat(WebhookService.parseRetryAfterMs(null)).isZero();
    }

    @Test
    void parseRetryAfterAcceptsHttpDate() {
        long now = Instant.parse("2026-08-29T12:00:00Z").toEpochMilli();
        String header = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.ofEpochMilli(now + 420_000L).atZone(ZoneOffset.UTC));

        assertThat(WebhookService.parseRetryAfterMs(header, now)).isEqualTo(420_000L);
    }

    @Test
    void rateLimitDelayIsExactAndNotCapped() {
        DeliveryResult rateLimited =
                WebhookService.classifyHttpResponse(429, "600", System.currentTimeMillis());

        assertThat(rateLimited.kind()).isEqualTo(DeliveryResult.Kind.RETRYABLE);
        assertThat(rateLimited.retryAfterMs()).isEqualTo(600_000L);
        assertThat(WebhookService.retryDelayMs(rateLimited, 2)).isEqualTo(600_000L);
    }

    @Test
    void rateLimitHonorsZeroAndPastDateExactly() {
        long now = Instant.parse("2026-08-29T12:00:00Z").toEpochMilli();
        DeliveryResult zero = WebhookService.classifyHttpResponse(429, "0", now);
        String past = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.ofEpochMilli(now - 60_000L).atZone(ZoneOffset.UTC));
        DeliveryResult expiredDate = WebhookService.classifyHttpResponse(429, past, now);

        assertThat(WebhookService.retryDelayMs(zero, 2)).isZero();
        assertThat(WebhookService.retryDelayMs(expiredDate, 2)).isZero();
    }

    @Test
    void responsePolicyRetriesServerErrorsAndDropsClientErrors() {
        assertThat(WebhookService.classifyHttpResponse(204, null, 0L).kind())
                .isEqualTo(DeliveryResult.Kind.SUCCESS);
        assertThat(WebhookService.classifyHttpResponse(503, null, 0L).kind())
                .isEqualTo(DeliveryResult.Kind.RETRYABLE);
        assertThat(WebhookService.classifyHttpResponse(400, null, 0L).kind())
                .isEqualTo(DeliveryResult.Kind.PERMANENT);
    }

    @Test
    void ordinaryExplicitRetryDelayIsCapped() {
        DeliveryResult retryable = DeliveryResult.retryable(503, 600_000L, "HTTP 503");

        assertThat(WebhookService.retryDelayMs(retryable, 2))
                .isEqualTo(WebhookService.MAX_BACKOFF_MS);
    }

    @Test
    void computeBackoffRespectsCap() {
        for (int attempt = 1; attempt <= 20; attempt++) {
            long delay = WebhookService.computeBackoffMs(attempt);
            assertThat(delay).isBetween(0L, WebhookService.MAX_BACKOFF_MS);
        }
    }
}
