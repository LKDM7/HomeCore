package fr.lkdm.homecore.api.registry;

import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceRegistryTest {
    private static final ResourceLocation TYPE = ResourceLocation.parse("test:machine");

    private static class Device implements DashboardDevice {
        private UUID id = UUID.randomUUID();
        private boolean valid = true;
        private final ResourceLocation type;
        Device(ResourceLocation type) { this.type = type; }
        @Override public UUID id() { return id; }
        @Override public ResourceLocation deviceType() { return type; }
        @Override public Component displayName() { return Component.literal("Device"); }
        @Override public DeviceStatus status() { return DeviceStatus.ONLINE; }
        @Override public boolean isValid() { return valid; }
    }

    @Test void registerLookupAndUnregister() {
        var registry = new DeviceRegistry();
        var device = new Device(TYPE);
        registry.register(device);
        assertSame(device, registry.get(device.id()).orElseThrow());
        assertSame(device, registry.unregister(device.id()).orElseThrow());
        assertTrue(registry.get(device.id()).isEmpty());
        assertTrue(registry.unregister(device.id()).isEmpty());
    }

    @Test void rejectsEveryDuplicateWithoutReplacingOriginal() {
        var registry = new DeviceRegistry();
        var original = new Device(TYPE);
        var duplicate = new Device(TYPE);
        duplicate.id = original.id;
        registry.register(original);
        assertThrows(IllegalArgumentException.class, () -> registry.register(original));
        assertThrows(IllegalArgumentException.class, () -> registry.register(duplicate));
        assertSame(original, registry.get(original.id).orElseThrow());
    }

    @Test void findByTypeAndSnapshotsAreImmutableAndOrdered() {
        var registry = new DeviceRegistry();
        var first = new Device(TYPE);
        var other = new Device(ResourceLocation.parse("test:other"));
        var last = new Device(TYPE);
        registry.register(first); registry.register(other); registry.register(last);
        var snapshot = registry.getAll();
        assertEquals(List.of(first, other, last), snapshot);
        assertEquals(List.of(first, last), registry.findByType(TYPE));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        registry.clear();
        assertTrue(registry.getAll().isEmpty());
        assertEquals(3, snapshot.size());
    }

    @Test void invalidDevicesAreRejectedOrRemovedLazily() {
        var registry = new DeviceRegistry();
        var device = new Device(TYPE);
        device.valid = false;
        assertThrows(IllegalArgumentException.class, () -> registry.register(device));
        device.valid = true;
        registry.register(device);
        device.valid = false;
        assertTrue(registry.get(device.id).isEmpty());
        assertTrue(registry.unregister(device.id).isEmpty());
        device.valid = true;
        registry.register(device);
        device.valid = false;
        assertTrue(registry.findByType(TYPE).isEmpty());
    }

    @Test void changedIdentityDoesNotLeaveGhostEntries() {
        var registry = new DeviceRegistry();
        var device = new Device(TYPE);
        registry.register(device);
        UUID oldId = device.id;
        device.id = UUID.randomUUID();
        assertTrue(registry.get(oldId).isEmpty());
        assertTrue(registry.get(device.id).isEmpty());
    }

    @Test void rejectsMalformedSchemaAndNullProperties() {
        var registry = new DeviceRegistry();
        assertThrows(NullPointerException.class, () -> registry.register(new Device(null)));
        var metric = DeviceMetric.builder(ResourceLocation.parse("test:value"), Component.literal("Value"), MetricTypes.INTEGER, 1).build();
        Device malformed = new Device(TYPE) {
            @Override public List<DeviceMetric<?>> metrics() { return List.of(metric, metric); }
        };
        assertThrows(IllegalArgumentException.class, () -> registry.register(malformed));
        assertTrue(registry.getAll().isEmpty());
    }
}
