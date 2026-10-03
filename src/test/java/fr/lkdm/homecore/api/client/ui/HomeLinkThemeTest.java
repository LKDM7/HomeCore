package fr.lkdm.homecore.api.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HomeLinkThemeTest {
    @Test
    void preservesTheOfficialPaletteAndControlGeometry() {
        assertEquals(0xFF303234, HomeLinkTheme.BACKGROUND);
        assertEquals(0xFF45474A, HomeLinkTheme.HEADER);
        assertEquals(0xFF252729, HomeLinkTheme.SURFACE);
        assertEquals(0xFF4A4D50, HomeLinkTheme.HOVER);
        assertEquals(0xFF626568, HomeLinkTheme.LINE);
        assertEquals(0xFFD2B181, HomeLinkTheme.ACCENT);
        assertEquals(0xFFE7E5E0, HomeLinkTheme.TEXT);
        assertEquals(0xFFAFB1AD, HomeLinkTheme.MUTED);
        assertEquals(0xFFA1BD92, HomeLinkTheme.ONLINE);
        assertEquals(0xFFD3B16F, HomeLinkTheme.WARNING);
        assertEquals(0xFFD19A8F, HomeLinkTheme.OFFLINE);
        assertEquals(18, HomeLinkTheme.CONTROL_HEIGHT);
        assertEquals(29, HomeLinkTheme.HEADER_HEIGHT);
        assertEquals(29, HomeLinkTheme.FOOTER_HEIGHT);
        assertEquals(12, HomeLinkTheme.CONTENT_PADDING);
        assertEquals(8, HomeLinkTheme.OUTER_MARGIN);
        assertEquals(520, HomeLinkTheme.DEFAULT_MAX_WIDTH);
        assertEquals(340, HomeLinkTheme.DEFAULT_MAX_HEIGHT);
    }

    @Test
    void explicitTonesCoverAllVisualStates() {
        assertEquals(HomeLinkTheme.ONLINE, HomeLinkTheme.statusColor(HomeLinkStatusTone.ONLINE));
        assertEquals(HomeLinkTheme.WARNING, HomeLinkTheme.statusColor(HomeLinkStatusTone.WARNING));
        assertEquals(HomeLinkTheme.OFFLINE, HomeLinkTheme.statusColor(HomeLinkStatusTone.OFFLINE));
        assertEquals(HomeLinkTheme.MUTED, HomeLinkTheme.statusColor(HomeLinkStatusTone.NEUTRAL));
        assertEquals(HomeLinkTheme.MUTED, HomeLinkTheme.statusColor((HomeLinkStatusTone) null));
    }

    @Test
    void legacyIdentifiersKeepTheirStatusAndUnknownValuesAreNeutral() {
        for (String status : new String[] {"ONLINE", "CONNECTED"})
            assertEquals(HomeLinkTheme.ONLINE, HomeLinkTheme.statusColor(status), status);
        for (String status : new String[] {"WARNING", "UNREACHABLE"})
            assertEquals(HomeLinkTheme.WARNING, HomeLinkTheme.statusColor(status), status);
        for (String status : new String[] {"OFFLINE", "ERROR", "CRITICAL"})
            assertEquals(HomeLinkTheme.OFFLINE, HomeLinkTheme.statusColor(status), status);
        for (String status : new String[] {"", "NEUTRAL", "future_state", "online", " ONLINE "})
            assertEquals(HomeLinkTheme.MUTED, HomeLinkTheme.statusColor(status), status);
        assertEquals(HomeLinkTheme.MUTED, HomeLinkTheme.statusColor((String) null));
    }
}
