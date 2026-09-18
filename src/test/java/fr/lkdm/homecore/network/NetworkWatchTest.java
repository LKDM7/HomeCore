package fr.lkdm.homecore.network;

import static org.junit.jupiter.api.Assertions.*;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.event.DeviceEventBus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import fr.lkdm.homecore.api.security.ActionExecutor;
import fr.lkdm.homecore.api.security.PermissionValidator;
import fr.lkdm.homecore.api.security.RateLimiter;
import fr.lkdm.homecore.api.transport.HomeCorePayloads.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NetworkWatchTest {
    private static final ResourceLocation EVENT = ResourceLocation.parse("test:event");
    private final UUID owner = UUID.randomUUID();
    private final DeviceRegistry devices = new DeviceRegistry();
    private final HomeNetworkManager networks = new HomeNetworkManager();
    private final HomeNetwork home = networks.createNetwork("Watch", owner);
    private final PermissionValidator permissions = new PermissionValidator();
    private final DeviceEventBus events = new DeviceEventBus();
    private final AtomicLong clock = new AtomicLong();
    private final List<CustomPacketPayload> sent = new ArrayList<>();
    private int nextDevice;

    @Test void hundredDevicesUseOneWatchAndOnlyChangedMetricDeltas() {
        List<Device> fixtures = new ArrayList<>();
        for (int index = 0; index < 100; index++) fixtures.add(register(4));
        try (var sync = sync()) {
            sync.watch(owner, new NetworkWatchRequest(UUID.randomUUID(), home.id()));
            assertEquals(100, payloads(NetworkWatchResponse.class).getFirst().ids().size());
            assertEquals(0, payloads(DeviceSnapshot.class).size());
            int snapshots = 0;
            for (int tick = 0; tick < 7; tick++) {
                sent.clear(); sync.tick();
                int emitted = payloads(DeviceSnapshot.class).size();
                assertTrue(emitted <= 16, "Initial snapshots must be spread across bounded server ticks");
                snapshots += emitted;
            }
            assertEquals(100, snapshots);
            sent.clear(); sync.tick(); assertTrue(sent.isEmpty());
            fixtures.get(99).metrics.getFirst().setValue(42);
            sync.tick();
            assertEquals(1, payloads(MetricUpdate.class).size());
            assertEquals(fixtures.get(99).id(), payloads(MetricUpdate.class).getFirst().deviceId());
            assertTrue(payloads(DeviceSnapshot.class).isEmpty());
            assertTrue(payloads(NetworkWatchResponse.class).isEmpty());
            sent.clear();
            for (Device fixture : fixtures) for (var metric : fixture.metrics) metric.setValue(100);
            sync.tick();
            int first = payloads(MetricUpdate.class).size();
            assertTrue(first <= 256);
            sent.clear(); sync.tick();
            int second = payloads(MetricUpdate.class).size();
            assertTrue(second <= 256);
            assertEquals(400, first + second, "Rotating iteration must deliver the bounded backlog without starvation");
        }
    }

    @Test void rosterRemovalAndAdditionPreserveUnchangedSnapshots() {
        Device first = register(1), second = register(1);
        try (var sync = sync()) {
            UUID request = UUID.randomUUID();
            sync.watch(owner, new NetworkWatchRequest(request, home.id())); sync.tick();
            sent.clear(); devices.unregister(first.id());
            for (int tick = 0; tick < 20; tick++) sync.tick();
            var roster = payloads(NetworkWatchResponse.class).getLast();
            assertEquals(request, roster.requestId()); assertEquals(List.of(second.id()), roster.ids());
            assertTrue(payloads(DeviceSnapshot.class).stream().noneMatch(packet -> packet.deviceId().equals(second.id())));
            sent.clear(); Device third = register(1); sync.tick();
            assertEquals(List.of(second.id(), third.id()), payloads(NetworkWatchResponse.class).getFirst().ids());
            assertEquals(1, payloads(DeviceSnapshot.class).size());
            assertEquals(third.id(), payloads(DeviceSnapshot.class).getFirst().deviceId());
            sent.clear(); second.metrics.getFirst().setValue(12); sync.tick();
            assertEquals(1, payloads(MetricUpdate.class).size());
        }
    }

    @Test void continuouslyChangingMetricsDoNotStarveDevicesBeyondBudget() {
        List<Device> fixtures = new ArrayList<>();
        for (int index = 0; index < 128; index++) fixtures.add(register(32));
        try (var sync = sync()) {
            sync.watch(owner, new NetworkWatchRequest(UUID.randomUUID(), home.id()));
            for (int tick = 0; tick < 8; tick++) sync.tick();
            assertEquals(128, payloads(DeviceSnapshot.class).size());
            assertTrue(payloads(DeviceSnapshot.class).stream().allMatch(packet -> packet.data().getString("status").equals("ONLINE")));
            Set<UUID> updated = new java.util.HashSet<>();
            for (int tick = 0; tick < 16; tick++) {
                for (Device fixture : fixtures) for (var metric : fixture.metrics) metric.setValue(tick + 10);
                sent.clear(); sync.tick();
                var deltas = payloads(MetricUpdate.class);
                assertTrue(deltas.size() <= 256);
                deltas.forEach(delta -> updated.add(delta.deviceId()));
            }
            assertEquals(128, updated.size(), "Exhausted delta budget must resume after the processed devices, not skip a fixed stride");
        }
    }

    @Test void brokenProviderIsIsolatedAndRecoversWithoutLogOrPacketFlood() {
        Device broken = register(1), healthy = register(1);
        broken.broken = true;
        try (var sync = sync()) {
            sync.watch(owner, new NetworkWatchRequest(UUID.randomUUID(), home.id())); sync.tick();
            assertEquals(2, payloads(DeviceSnapshot.class).size());
            var fallback = payloads(DeviceSnapshot.class).stream().filter(packet -> packet.deviceId().equals(broken.id())).findFirst().orElseThrow();
            assertEquals("ERROR", fallback.data().getString("status"));
            sent.clear(); healthy.metrics.getFirst().setValue(9);
            for (int tick = 0; tick < 25; tick++) sync.tick();
            assertEquals(1, payloads(MetricUpdate.class).size());
            assertTrue(payloads(DeviceSnapshot.class).isEmpty(), "An unchanged broken device must not resend fallback every tick");
            assertTrue(payloads(NetworkWatchResponse.class).isEmpty());
            broken.broken = false;
            for (int tick = 0; tick < 20; tick++) sync.tick();
            assertEquals(1, payloads(DeviceSnapshot.class).size());
            assertEquals("ONLINE", payloads(DeviceSnapshot.class).getFirst().data().getString("status"));
        }
    }

    @Test void networkWideEventsBeyondWatchedCapAndRoleRevocation() {
        Device outside = null;
        for (int index = 0; index < 129; index++) outside = register(1);
        UUID viewer = UUID.randomUUID(); networks.setMember(home.id(), viewer, NetworkRole.VIEWER);
        try (var sync = sync()) {
            sync.watch(viewer, new NetworkWatchRequest(UUID.randomUUID(), home.id()));
            var response = payloads(NetworkWatchResponse.class).getFirst();
            assertTrue(response.truncated()); assertEquals(129, response.totalCount()); assertEquals(128, response.ids().size());
            assertFalse(response.ids().contains(outside.id()));
            sent.clear(); events.publish(event(outside.id()));
            assertEquals(1, payloads(DeviceEventNotification.class).size(), "Network alerts must not depend on the watched device roster");
            networks.setMember(home.id(), viewer, NetworkRole.MEMBER); sent.clear(); sync.tick();
            assertEquals("MEMBER", payloads(HomeNetworkSnapshot.class).getFirst().data().getString("role"));
            networks.removeMember(home.id(), viewer); sent.clear(); events.publish(event(outside.id()));
            assertTrue(sent.isEmpty()); sync.tick();
            assertEquals(ActionResult.Code.DENIED, payloads(NetworkWatchResponse.class).getFirst().result());
            sent.clear(); sync.tick(); assertTrue(sent.isEmpty());
        }
        assertEquals(0, events.listenerCount());
    }

    @Test void rateLimitedRefreshAndLegacyReplacementKeepOneSubscription() {
        Device device = register(1);
        try (var sync = sync()) {
            sync.watch(owner, new NetworkWatchRequest(UUID.randomUUID(), home.id())); sync.tick();
            sync.watch(owner, new NetworkWatchRequest(UUID.randomUUID(), home.id())); sync.tick();
            sent.clear(); sync.watch(owner, new NetworkWatchRequest(UUID.randomUUID(), home.id()));
            assertEquals(ActionResult.Code.RATE_LIMITED, payloads(NetworkWatchResponse.class).getFirst().result());
            sent.clear(); device.metrics.getFirst().setValue(8); sync.tick();
            assertEquals(1, payloads(MetricUpdate.class).size());
            clock.set(1_000_000_000L); sent.clear();
            sync.request(owner, new DeviceListRequest(UUID.randomUUID(), java.util.Optional.of(home.id()), 0));
            sent.clear(); device.metrics.getFirst().setValue(9); sync.tick();
            assertEquals(1, payloads(MetricUpdate.class).size(), "Switching to legacy page must stop the old watch");
            sync.playerLeft(owner); sent.clear(); device.metrics.getFirst().setValue(10); sync.tick(); assertTrue(sent.isEmpty());
        }
    }

    @Test void radioLossStopsDeltasEventsAndRosterThenRecovers() {
        Device device = register(1);
        var reachable = new java.util.concurrent.atomic.AtomicBoolean(true);
        networks.setReachabilityPolicy(ResourceLocation.parse("test:radio"), (network, target) -> reachable.get());
        try (var sync = sync()) {
            sync.watch(owner, new NetworkWatchRequest(UUID.randomUUID(), home.id())); sync.tick();
            assertEquals(1, payloads(DeviceSnapshot.class).size());
            sent.clear(); reachable.set(false);
            device.metrics.getFirst().setValue(12);
            events.publish(event(device.id()));
            for (int i = 0; i < 22; i++) sync.tick();
            assertTrue(payloads(MetricUpdate.class).isEmpty());
            assertTrue(payloads(DeviceEventNotification.class).isEmpty());
            assertTrue(payloads(NetworkWatchResponse.class).getLast().ids().isEmpty());
            sent.clear(); reachable.set(true);
            for (int i = 0; i < 22; i++) sync.tick();
            assertEquals(List.of(device.id()), payloads(NetworkWatchResponse.class).getLast().ids());
            assertEquals(1, payloads(DeviceSnapshot.class).size());
        }
    }

    private ServerSync sync() {
        var executor = new ActionExecutor(devices, networks, permissions, new RateLimiter(10, Duration.ofSeconds(1), 100, clock::get));
        return new ServerSync(devices, networks, permissions, executor, events, ignored -> true,
                (player, packet) -> sent.add(packet), () -> { }, clock::get);
    }
    private Device register(int count) {
        Device device = new Device(new UUID(0, ++nextDevice), count); devices.register(device); networks.addDevice(home.id(), device.id()); return device;
    }
    private <T> List<T> payloads(Class<T> type) { return sent.stream().filter(type::isInstance).map(type::cast).toList(); }
    private DeviceEvent event(UUID device) { return new DeviceEvent(EVENT, device, Instant.EPOCH, DeviceEvent.Severity.WARNING, Map.of("message", "Event")); }
    private static final class Device implements DashboardDevice {
        private final UUID id;
        private final List<DeviceMetric<Integer>> metrics = new ArrayList<>();
        private boolean broken;
        Device(UUID id, int count) {
            this.id = id;
            for (int index = 0; index < count; index++) metrics.add(DeviceMetric.builder(ResourceLocation.parse("test:value_" + index),
                    Component.literal("Value"), MetricTypes.INTEGER, 1).updatePolicy(UpdatePolicy.ON_CHANGE).build());
        }
        public UUID id() { return id; }
        public ResourceLocation deviceType() { return ResourceLocation.parse("test:watched"); }
        public Component displayName() { if (broken) throw new IllegalStateException("Broken provider"); return Component.literal("Watched"); }
        public DeviceStatus status() { return DeviceStatus.ONLINE; }
        public List<DeviceMetric<?>> metrics() { return List.copyOf(metrics); }
        public Set<ResourceLocation> eventTypes() { return Set.of(EVENT); }
    }
}
