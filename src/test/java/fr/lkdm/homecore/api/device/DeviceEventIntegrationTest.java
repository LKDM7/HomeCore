package fr.lkdm.homecore.api.device;

import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.event.DeviceEventBus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceEventIntegrationTest {
    @Test void metricChangeCanNotifySeveralConsumers() {
        var metric = DeviceMetric.builder(ResourceLocation.parse("homecore:temperature"),
                Component.literal("Temperature"), MetricTypes.DOUBLE, 22.5).build();
        try (var bus = new DeviceEventBus()) {
            var first = new AtomicInteger();
            var second = new AtomicInteger();
            bus.subscribe(event -> { assertEquals("23.0", event.data().get("temperature")); first.incrementAndGet(); });
            var listener = bus.subscribe(event -> second.incrementAndGet());
            if (metric.setValue(23.0)) bus.publish(new DeviceEvent(ResourceLocation.parse("homecore:temperature_changed"),
                    UUID.randomUUID(), Instant.now(), DeviceEvent.Severity.INFO, Map.of("temperature", metric.value().toString())));
            assertEquals(1, first.get());
            assertEquals(1, second.get());
            listener.close();
            assertEquals(1, bus.listenerCount());
        }
    }
}
