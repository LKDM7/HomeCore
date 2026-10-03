package fr.lkdm.homecore.api.client.ui;

/** Shared visual tokens. Contains no screen state or operational status logic. */
public final class HomeLinkTheme {
    /** Graphite window background. */
    public static final int BACKGROUND = 0xFF303234;
    /** Steel header surface. */
    public static final int HEADER = 0xFF45474A;
    /** Recessed panel surface. */
    public static final int SURFACE = 0xFF252729;
    /** Highlighted surface. */
    public static final int HOVER = 0xFF4A4D50;
    /** Borders and dividers. */
    public static final int LINE = 0xFF626568;
    /** Copper selection and keyboard focus. */
    public static final int ACCENT = 0xFFD2B181;
    /** Primary text. */
    public static final int TEXT = 0xFFE7E5E0;
    /** Secondary text and neutral status. */
    public static final int MUTED = 0xFFAFB1AD;
    /** Available status. */
    public static final int ONLINE = 0xFFA1BD92;
    /** Attention status. */
    public static final int WARNING = 0xFFD3B16F;
    /** Failure status. */
    public static final int OFFLINE = 0xFFD19A8F;
    /** Recommended button and text field height, in GUI pixels. */
    public static final int CONTROL_HEIGHT = 18;
    /** Default header height. */
    public static final int HEADER_HEIGHT = 29;
    /** Default footer height. */
    public static final int FOOTER_HEIGHT = 29;
    /** Content inset. */
    public static final int CONTENT_PADDING = 12;
    /** Minimum outer margin when space permits. */
    public static final int OUTER_MARGIN = 8;
    /** Default preferred window width, not an inventory constraint. */
    public static final int DEFAULT_MAX_WIDTH = 520;
    /** Default preferred window height. */
    public static final int DEFAULT_MAX_HEIGHT = 340;

    private HomeLinkTheme() { }

    /** Resolves an explicit visual tone.
     * @param tone visual tone; null is neutral
     * @return opaque ARGB color
     */
    public static int statusColor(HomeLinkStatusTone tone) {
        if (tone == null) return MUTED;
        return switch (tone) {
            case ONLINE -> ONLINE;
            case WARNING -> WARNING;
            case OFFLINE -> OFFLINE;
            case NEUTRAL -> MUTED;
        };
    }

    /** Compatibility mapping for existing generic HomeLink status identifiers.
     * Domain-specific statuses belong to consumers. Prefer explicit tones in new screens.
     * @param status case-sensitive status identifier; null is neutral
     * @return opaque ARGB color
     */
    public static int statusColor(String status) {
        if (status == null) return MUTED;
        return switch (status) {
            case "ONLINE", "CONNECTED" -> ONLINE;
            case "WARNING", "UNREACHABLE" -> WARNING;
            case "OFFLINE", "ERROR", "CRITICAL" -> OFFLINE;
            default -> MUTED;
        };
    }
}
