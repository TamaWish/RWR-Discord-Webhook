package com.lozaine.resourceworldresetter.discord.webhook;

/** Categories of outbound webhook messages in v1. */
public enum WebhookEventCategory {
    WARNING,
    SUCCESS,
    FAILURE,
    CANCELLATION,
    INTERRUPTED
}
