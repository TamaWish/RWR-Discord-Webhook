package io.github.tamawish.rwr.discord.webhook;

/** Outcome of a single HTTP delivery attempt. */
public final class DeliveryResult {
    public enum Kind {
        SUCCESS,
        /** Retry after the given delay (network, 5xx, or 429). */
        RETRYABLE,
        /** Permanent client error (4xx other than 429). */
        PERMANENT
    }

    private final Kind kind;
    private final int httpStatus;
    private final long retryAfterMs;
    private final String detail;

    private DeliveryResult(Kind kind, int httpStatus, long retryAfterMs, String detail) {
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.retryAfterMs = Math.max(0L, retryAfterMs);
        this.detail = detail == null ? "" : detail;
    }

    public static DeliveryResult success(int httpStatus) {
        return new DeliveryResult(Kind.SUCCESS, httpStatus, 0L, "OK");
    }

    public static DeliveryResult retryable(int httpStatus, long retryAfterMs, String detail) {
        return new DeliveryResult(Kind.RETRYABLE, httpStatus, retryAfterMs, detail);
    }

    public static DeliveryResult permanent(int httpStatus, String detail) {
        return new DeliveryResult(Kind.PERMANENT, httpStatus, 0L, detail);
    }

    public Kind kind() {
        return kind;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public long retryAfterMs() {
        return retryAfterMs;
    }

    public String detail() {
        return detail;
    }
}
