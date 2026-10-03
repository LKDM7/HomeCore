package fr.lkdm.homecore.api.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HomeLinkScreenLayoutTest {
    @Test
    void centersTheReferenceSurfaceAndReservesItsContent() {
        var layout = HomeLinkScreenLayout.fit(640, 360, 520, 340);
        assertEquals(new HomeLinkScreenLayout(60, 10, 520, 340), layout);
        assertEquals(72, layout.contentX());
        assertEquals(51, layout.contentY());
        assertEquals(496, layout.contentWidth());
        assertEquals(258, layout.contentHeight());
        assertEquals(10, layout.headerY());
        assertEquals(29, layout.headerHeight());
        assertEquals(321, layout.footerY());
        assertEquals(29, layout.footerHeight());
    }

    @Test
    void shrinksAtLargeGuiScaleWithoutChangingThePreferredSize() {
        var layout = HomeLinkScreenLayout.fit(320, 240, 520, 340);
        assertEquals(new HomeLinkScreenLayout(8, 8, 304, 224), layout);
        assertEquals(280, layout.contentWidth());
        assertEquals(142, layout.contentHeight());
        assertEquals(new HomeLinkScreenLayout(232, 75, 176, 210),
                HomeLinkScreenLayout.fit(640, 360, 176, 210));
    }

    @Test
    void everyTinyViewportHasContainedNonnegativeRegions() {
        for (int width = 0; width <= 80; width++) {
            for (int height = 0; height <= 80; height++) {
                var layout = HomeLinkScreenLayout.fit(width, height, 520, 340);
                String viewport = width + "x" + height;
                assertTrue(layout.x() >= 0 && layout.y() >= 0, viewport);
                assertTrue(layout.width() >= 0 && layout.height() >= 0, viewport);
                assertTrue(layout.x() + layout.width() <= width, viewport);
                assertTrue(layout.y() + layout.height() <= height, viewport);
                assertTrue(Math.abs(layout.x() - (width - layout.x() - layout.width())) <= 1, viewport);
                assertTrue(Math.abs(layout.y() - (height - layout.y() - layout.height())) <= 1, viewport);
                assertTrue(layout.contentWidth() >= 0 && layout.contentHeight() >= 0, viewport);
                assertTrue(layout.contentX() >= layout.x(), viewport);
                assertTrue(layout.contentX() + layout.contentWidth() <= layout.x() + layout.width(), viewport);
                assertTrue(layout.contentY() >= layout.headerY() + layout.headerHeight(), viewport);
                assertTrue(layout.contentY() + layout.contentHeight() <= layout.footerY(), viewport);
                assertTrue(layout.footerY() + layout.footerHeight() == layout.y() + layout.height(), viewport);
                if (width >= 16) assertTrue(layout.x() >= 8 && width - layout.x() - layout.width() >= 8, viewport);
                if (height >= 16) assertTrue(layout.y() >= 8 && height - layout.y() - layout.height() >= 8, viewport);
            }
        }
    }

    @Test
    void emptyPreferencesAndLargeViewportsRemainValid() {
        assertEquals(new HomeLinkScreenLayout(160, 120, 0, 0), HomeLinkScreenLayout.fit(320, 240, 0, 0));
        var layout = HomeLinkScreenLayout.fit(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(8, layout.x());
        assertEquals(8, layout.y());
        assertTrue(layout.contentX() + layout.contentWidth() <= Integer.MAX_VALUE);
        assertTrue(layout.footerY() + layout.footerHeight() <= Integer.MAX_VALUE);
    }

    @Test
    void rejectsNegativeInputsAndSurfaceDimensions() {
        assertThrows(IllegalArgumentException.class, () -> HomeLinkScreenLayout.fit(-1, 240, 520, 340));
        assertThrows(IllegalArgumentException.class, () -> HomeLinkScreenLayout.fit(320, -1, 520, 340));
        assertThrows(IllegalArgumentException.class, () -> HomeLinkScreenLayout.fit(320, 240, -1, 340));
        assertThrows(IllegalArgumentException.class, () -> HomeLinkScreenLayout.fit(320, 240, 520, -1));
        assertThrows(IllegalArgumentException.class, () -> new HomeLinkScreenLayout(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new HomeLinkScreenLayout(0, 0, 1, -1));
    }
}
