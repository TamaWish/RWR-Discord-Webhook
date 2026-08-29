package com.lozaine.resourceworldresetter.discord.webhook;

import static org.assertj.core.api.Assertions.assertThat;

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
    void computeBackoffRespectsCap() {
        for (int attempt = 1; attempt <= 20; attempt++) {
            long delay = WebhookService.computeBackoffMs(attempt);
            assertThat(delay).isBetween(0L, WebhookService.MAX_BACKOFF_MS);
        }
    }
}
