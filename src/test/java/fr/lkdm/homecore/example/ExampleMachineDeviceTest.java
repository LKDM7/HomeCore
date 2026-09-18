package fr.lkdm.homecore.example;

import static org.junit.jupiter.api.Assertions.*;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.Unit;
import fr.lkdm.homecore.api.capability.DeviceCapability;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.event.DeviceEventBus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.Energy;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import fr.lkdm.homecore.api.security.ActionExecutor;
import fr.lkdm.homecore.api.security.PermissionValidator;
import fr.lkdm.homecore.api.security.RateLimiter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ExampleMachineDeviceTest {
    @Test void defaultSourceExposesCompleteSchemaAndTypedCapabilityWithoutWorld() {
        UUID id = UUID.randomUUID();
        try (var events = new DeviceEventBus()) {
            var device = new ExampleMachineDevice(new ExampleMachineDevice.MemoryMachine(id), events);
            assertEquals(id, device.id());
            assertEquals("Debug Dashboard Device", device.displayName().getString());
            assertEquals(DeviceStatus.ONLINE, device.status());
            assertTrue(device.position().isEmpty());
            assertTrue(device.dimension().isEmpty());
            assertEquals(22.5, metric(device, "temperature").value());
            assertEquals(new Energy(4500, 10000), metric(device, "energy").value());
            assertEquals(true, metric(device, "enabled").value());
            assertEquals(new Percentage(73), metric(device, "progress").value());
            assertEquals(42, metric(device, "counter").value());
            assertEquals(5, device.schema().metrics().size());
            assertEquals(3, device.schema().actions().size());
            assertTrue(device.schema().events().contains(ExampleMachineDevice.TEST_EVENT));
            assertEquals("Example Machine", device.capability(ExampleMachineDevice.MACHINE_INFO).orElseThrow().model());
            assertTrue(device.capabilities().contains(ExampleMachineDevice.MACHINE_INFO.id()));
            assertTrue(device.capability(new DeviceCapability<>(ExampleMachineDevice.MACHINE_INFO.id(), String.class)).isEmpty());
        }
    }

    @Test void securedActionsChangeSourceMetricsAndEmitOneEventEach() {
        var source = new ExampleMachineDevice.MemoryMachine(UUID.randomUUID());
        try (var events = new DeviceEventBus()) {
            var device = new ExampleMachineDevice(source, events);
            var devices = new DeviceRegistry();
            devices.register(device);
            var networks = new HomeNetworkManager();
            UUID owner = UUID.randomUUID();
            var network = networks.createNetwork("Example", owner);
            networks.addDevice(network.id(), device.id());
            var executor = new ActionExecutor(devices, networks, new PermissionValidator(), new RateLimiter());
            List<DeviceEvent> received = new ArrayList<>();
            events.subscribe(received::add);
            assertEquals(ActionResult.Code.SUCCESS, executor.execute(owner, network.id(), device.id(), id("toggle_enabled"), Unit.INSTANCE).code());
            assertFalse(source.enabled());
            assertEquals(false, metric(device, "enabled").value());
            assertEquals(ActionResult.Code.SUCCESS, executor.execute(owner, network.id(), device.id(), id("set_progress"), 85.0).code());
            assertEquals(new Percentage(85), source.progress());
            assertEquals(new Percentage(85), metric(device, "progress").value());
            assertEquals(ActionResult.Code.SUCCESS, executor.execute(owner, network.id(), device.id(), id("reset_counter"), Unit.INSTANCE).code());
            assertEquals(0, source.counter());
            assertEquals(0, metric(device, "counter").value());
            assertEquals(3, received.size());
            assertTrue(received.stream().allMatch(event -> event.source().equals(device.id())
                    && event.type().equals(ExampleMachineDevice.TEST_EVENT)));
            assertEquals("85.0", received.getLast().data().get("progress"));
            assertEquals("0", received.getLast().data().get("counter"));
            assertFalse(device.refresh());
            assertEquals(3, received.size());
        }
    }

    @Test void invalidInputNeverChangesSourceAndRemovedSourceStopsRefreshing() {
        var source = new ExampleMachineDevice.MemoryMachine(UUID.randomUUID());
        try (var events = new DeviceEventBus()) {
            var device = new ExampleMachineDevice(source, events);
            List<DeviceEvent> received = new ArrayList<>();
            events.subscribe(received::add);
            var action = device.actions().stream().filter(value -> value.id().equals(id("set_progress"))).findFirst().orElseThrow();
            var context = new fr.lkdm.homecore.api.action.ActionContext(UUID.randomUUID(), UUID.randomUUID(), device.id());
            for (Object value : List.of(-1.0, 101.0, 25.5, Double.NaN, "50")) {
                assertEquals(ActionResult.Code.INVALID_PARAMETER, action.execute(context, value).code());
            }
            assertEquals(new Percentage(73), source.progress());
            assertTrue(received.isEmpty());
            var devices = new DeviceRegistry();
            devices.register(device);
            source.invalidate();
            assertFalse(device.isValid());
            assertEquals(DeviceStatus.OFFLINE, device.status());
            assertFalse(device.refresh());
            assertTrue(devices.get(device.id()).isEmpty());
        }
    }

    private static DeviceMetric<?> metric(ExampleMachineDevice device, String path) {
        return device.metrics().stream().filter(metric -> metric.id().equals(id(path))).findFirst().orElseThrow();
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("homecore", path); }
}
