package fr.lkdm.homecore.api.energy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnergyBufferTest {
    @Test void isAnInputOnlyConsumerBoundedByCapacity() {
        var buffer = new EnergyBuffer(() -> 100, () -> { });
        assertEquals(EnergyRole.CONSUMER, buffer.role());
        assertEquals(EnergyPortType.INPUT, buffer.type());
        assertEquals(100, buffer.requested());
        assertEquals(60, buffer.insert(60, false));
        assertEquals(40, buffer.insert(70, true));
        assertEquals(60, buffer.stored());
        assertEquals(0, buffer.extract(10, false));
        assertEquals(0, buffer.available());
    }

    @Test void consumeIsAllOrNothing() {
        var buffer = new EnergyBuffer(() -> 100, () -> { });
        buffer.insert(30, false);
        assertFalse(buffer.consume(31));
        assertEquals(30, buffer.stored());
        assertTrue(buffer.consume(30));
        assertEquals(0, buffer.stored());
        assertTrue(buffer.consume(0));
    }

    @Test void periodicDrawIsExactOverAnyWindow() {
        for (int period : new int[] {1, 7, 20, 1200}) {
            for (long perPeriod : new long[] {1, 5, 20, 150, 1_000_003}) {
                for (long start : new long[] {0, 13, -40, 987_654_321L}) {
                    long total = 0;
                    for (int t = 0; t < period; t++) total += EnergyBuffer.shareOf(perPeriod, period, start + t);
                    assertEquals(perPeriod, total, "P=" + perPeriod + " n=" + period + " start=" + start);
                }
            }
        }
    }

    @Test void drawStopsWhenEmptyAndResumesWhenFed() {
        var buffer = new EnergyBuffer(() -> 1_000, () -> { });
        assertFalse(buffer.draw(10, 20, 0), "an empty machine is never powered, even on a free tick");
        buffer.insert(10, false);
        long used = 0;
        for (int t = 0; t < 20; t++) {
            long before = buffer.stored();
            assertTrue(buffer.draw(10, 20, t) || buffer.stored() == 0);
            used += before - buffer.stored();
        }
        assertEquals(10, used);
        assertTrue(buffer.draw(0, 20, 0), "a zero cost disables the requirement");
    }

    @Test void capacityShrinkClampsStoredAndPercent() {
        long[] cap = {200};
        var buffer = new EnergyBuffer(() -> cap[0], () -> { });
        buffer.insert(150, false);
        assertEquals(75, buffer.percent());
        cap[0] = 100;
        assertEquals(100, buffer.stored());
        assertEquals(0, buffer.insert(5, false));
        cap[0] = 0;
        assertEquals(0, buffer.percent());
    }

    @Test void notifiesOnlyOnRealChanges() {
        int[] changes = {0};
        var buffer = new EnergyBuffer(() -> 50, () -> changes[0]++);
        buffer.insert(10, true);
        buffer.consume(1);
        assertEquals(0, changes[0]);
        buffer.insert(10, false);
        buffer.consume(5);
        assertEquals(2, changes[0]);
    }
}
