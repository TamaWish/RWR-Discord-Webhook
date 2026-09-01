package io.github.tamawish.rwr.discord.notification;

/** Categories of outbound Discord notifications shared by webhook and future bot delivery. */
public enum NotificationCategory {
    CONFIGURATION,
    WARNING,
    SUCCESS,
    FAILURE,
    CANCELLATION,
    INTERRUPTED
}
