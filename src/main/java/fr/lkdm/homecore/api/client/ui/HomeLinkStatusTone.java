package fr.lkdm.homecore.api.client.ui;

/** Visual status tones; accompany each tone with meaningful text or a tooltip. */
public enum HomeLinkStatusTone {
    /** Available or connected. */
    ONLINE,
    /** Attention needed or temporarily unreachable. */
    WARNING,
    /** Unavailable or failed. */
    OFFLINE,
    /** No operational status. */
    NEUTRAL
}
