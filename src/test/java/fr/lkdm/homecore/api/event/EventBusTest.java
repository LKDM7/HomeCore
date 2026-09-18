package fr.lkdm.homecore.api.event;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EventBusTest {
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("test", "change");

    @Test void multipleListenersReceiveInOrderAndCloseIsIdempotent() {
        try (var bus = new DeviceEventBus()) {
            List<String> received = new ArrayList<>();
            var first = bus.subscribe(event -> received.add("first"));
            bus.subscribe(event -> received.add("second"));
            bus.publish(event());
            assertEquals(List.of("first", "second"), received);
            first.close();
            first.close();
            bus.publish(event());
            assertEquals(List.of("first", "second", "second"), received);
            assertEquals(1, bus.listenerCount());
        }
    }

    @Test void selfRemovalAndListenerFailureDoNotBlockOtherCallbacks() {
        try (var bus = new DeviceEventBus()) {
            var self = new AtomicReference<DeviceEventBus.Subscription>();
            AtomicInteger calls = new AtomicInteger();
            self.set(bus.subscribe(event -> self.get().close()));
            bus.subscribe(event -> { throw new IllegalStateException("test callback failure"); });
            bus.subscribe(event -> calls.incrementAndGet());
            bus.publish(event());
            bus.publish(event());
            assertEquals(2, calls.get());
            assertEquals(2, bus.listenerCount());
        }
    }

    @Test void typedSubscriptionFiltersAndNewListenersWaitUntilNextPublish() {
        try (var bus = new DeviceEventBus()) {
            AtomicInteger filtered = new AtomicInteger();
            AtomicInteger late = new AtomicInteger();
            bus.subscribe(new DeviceEventType(ResourceLocation.fromNamespaceAndPath("test", "other"), Component.empty()),
                    event -> filtered.incrementAndGet());
            bus.subscribe(event -> bus.subscribe(added -> late.incrementAndGet()));
            bus.publish(event());
            assertEquals(0, filtered.get());
            assertEquals(0, late.get());
            bus.publish(event());
            assertEquals(1, late.get());
        }
    }

    @Test void clearAllowsReuseButCloseRejectsNewWork() {
        var bus = new DeviceEventBus();
        bus.subscribe(event -> fail("removed callback"));
        bus.clear();
        assertEquals(0, bus.listenerCount());
        AtomicInteger calls = new AtomicInteger();
        bus.subscribe(event -> calls.incrementAndGet());
        bus.publish(event());
        assertEquals(1, calls.get());
        bus.close();
        bus.close();
        assertEquals(0, bus.listenerCount());
        assertThrows(IllegalStateException.class, () -> bus.publish(event()));
        assertThrows(IllegalStateException.class, () -> bus.subscribe(event -> { }));
    }

    @Test void dataIsDefensivelyCopiedAndBounded() {
        Map<String, String> data = new HashMap<>();
        data.put("temperature", "22.5");
        var event = new DeviceEvent(TYPE, UUID.randomUUID(), Instant.now(), DeviceEvent.Severity.INFO, data);
        data.put("temperature", "99");
        assertEquals("22.5", event.data().get("temperature"));
        assertThrows(UnsupportedOperationException.class, () -> event.data().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new DeviceEvent(TYPE, UUID.randomUUID(), Instant.now(),
                DeviceEvent.Severity.INFO, Map.of("", "bad")));
        assertThrows(IllegalArgumentException.class, () -> new DeviceEvent(TYPE, UUID.randomUUID(), Instant.now(),
                DeviceEvent.Severity.INFO, Map.of("x", "a".repeat(4097))));
    }

    private static DeviceEvent event() {
        return new DeviceEvent(TYPE, UUID.randomUUID(), Instant.now(), DeviceEvent.Severity.INFO, Map.of("value", "22.5"));
    }
}
