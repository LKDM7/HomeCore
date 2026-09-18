package fr.lkdm.homecore.api.metric;

import java.math.BigDecimal;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetricTest {
    private static <T> DeviceMetric.Builder<T> metric(MetricType<T> type, T initial) {
        return DeviceMetric.builder(ResourceLocation.fromNamespaceAndPath("test", "metric"), Component.literal("Metric"), type, initial);
    }
    @Test void rangeIncludesEndpointsAndRejectsInvalidUpdatesAtomically() {
        var metric = metric(MetricTypes.DOUBLE, 0.0).range(0, 100, 0.5).build();
        assertTrue(metric.setValue(100.0));
        assertFalse(metric.setValue(100.0));
        assertThrows(IllegalArgumentException.class, () -> metric.setValue(100.5));
        assertThrows(IllegalArgumentException.class, () -> metric.setValue(0.25));
        assertEquals(new DeviceMetric.Snapshot<>(100.0, 1), metric.snapshot());
    }
    @Test void rejectsNonfiniteAndWrongTypes() {
        assertThrows(IllegalArgumentException.class, () -> metric(MetricTypes.DOUBLE, Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> metric(MetricTypes.DOUBLE, Double.POSITIVE_INFINITY).build());
        assertThrows(IllegalArgumentException.class, () -> MetricTypes.INTEGER.validate("1"));
        assertThrows(IllegalArgumentException.class, () -> MetricTypes.INTEGER.validate(null));
        assertThrows(IllegalArgumentException.class, () -> metric(MetricTypes.STRING, "test").range(0, 1).build());
        assertThrows(IllegalArgumentException.class, () -> new MetricRange(10, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MetricRange(0, 10, -1));
    }
    @Test void longBoundsDoNotLosePrecision() {
        var range = new MetricRange(BigDecimal.valueOf(Long.MAX_VALUE - 2), BigDecimal.valueOf(Long.MAX_VALUE), BigDecimal.valueOf(2));
        var metric = metric(MetricTypes.LONG, Long.MAX_VALUE - 2).range(range).build();
        assertThrows(IllegalArgumentException.class, () -> metric.setValue(Long.MAX_VALUE - 1));
        assertTrue(metric.setValue(Long.MAX_VALUE));
    }
    @Test void validatesSpecialValues() {
        assertThrows(IllegalArgumentException.class, () -> new Percentage(101));
        assertThrows(IllegalArgumentException.class, () -> new Percentage(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Duration(-1));
        assertThrows(IllegalArgumentException.class, () -> new Energy(11, 10));
        assertThrows(IllegalArgumentException.class, () -> new ItemValue(ResourceLocation.parse("minecraft:stone"), -1));
        assertThrows(IllegalArgumentException.class, () -> new FluidValue(ResourceLocation.parse("minecraft:water"), -1));
        assertEquals(new Energy(4500, 10000), metric(MetricTypes.ENERGY, new Energy(4500, 10000)).build().value());
        assertEquals(new Percentage(73), metric(MetricTypes.PERCENTAGE, new Percentage(73)).build().value());
    }
    private enum State { READY, IDLE }
    @Test void supportsEnumsAndExtensibleUnits() {
        var type = MetricTypes.enumeration(ResourceLocation.parse("test:state"), State.class);
        var unit = new Unit(ResourceLocation.parse("test:custom"), "custom");
        var metric = metric(type, State.READY).unit(unit).updatePolicy(UpdatePolicy.ON_CHANGE).build();
        assertEquals(State.READY, metric.value());
        assertEquals(unit, metric.unit());
        assertEquals(UpdatePolicy.ON_CHANGE, metric.updatePolicy());
    }
}
