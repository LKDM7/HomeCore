package fr.lkdm.homecore.network;

import static org.junit.jupiter.api.Assertions.*;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
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
import fr.lkdm.homecore.api.transport.WireValue;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ServerSyncTest {
    private static final ResourceLocation PROGRESS = ResourceLocation.parse("test:progress");
    private static final ResourceLocation SET = ResourceLocation.parse("test:set_progress");
    private static final ResourceLocation EVENT = ResourceLocation.parse("test:changed");
    private final UUID player = UUID.randomUUID();
    private final DeviceRegistry devices = new DeviceRegistry();
    private final HomeNetworkManager networks = new HomeNetworkManager();
    private final HomeNetwork home = networks.createNetwork("Home", player);
    private final DeviceEventBus events = new DeviceEventBus();
    private final PermissionValidator permissions = new PermissionValidator();
    private final AtomicLong clock = new AtomicLong();
    private final List<CustomPacketPayload> sent = new ArrayList<>();
    private final ActionExecutor executor = new ActionExecutor(devices, networks, permissions,
            new RateLimiter(10, Duration.ofSeconds(1), 100, clock::get));

    @Test void initialSnapshotActionResultAndDeltaWithoutRepeatedSnapshot() {
        var device = register(UpdatePolicy.ON_CHANGE);
        try (var sync = sync()) {
            subscribe(sync, player);
            var initial = only(DeviceSnapshot.class);
            assertEquals(73.0, WireValue.fromTag(initial.data().getList("metrics", Tag.TAG_COMPOUND).getCompound(0).getCompound("value")).value());
            sent.clear();
            sync.execute(player, new ExecuteActionRequest(UUID.randomUUID(), home.id(), device.id(), SET, WireValue.from(85.0)));
            assertEquals(ActionResult.Code.SUCCESS, only(ActionResultResponse.class).result().code());
            sync.tick();
            assertEquals(85.0, only(MetricUpdate.class).value().value());
            assertEquals(0, count(DeviceSnapshot.class));
            sent.clear();
            sync.tick();
            assertTrue(sent.isEmpty());
        }
    }

    @Test void unreachableDeviceCannotBeListedOrStreamedOnLegacyPage() {
        var device = register(UpdatePolicy.ON_CHANGE);
        var reachable = new java.util.concurrent.atomic.AtomicBoolean(false);
        networks.setReachabilityPolicy(ResourceLocation.parse("test:radio"), (network, target) -> reachable.get());
        try (var sync = sync()) {
            subscribe(sync, player);
            assertEquals(0, count(DeviceSnapshot.class));
            reachable.set(true);
            clock.addAndGet(2_000_000_000L);
            sent.clear(); subscribe(sync, player);
            assertEquals(1, count(DeviceSnapshot.class));
            sent.clear(); reachable.set(false);
            device.progress.setValue(90.0);
            sync.tick();
            assertEquals(0, count(MetricUpdate.class));
            assertEquals(ActionResult.Code.FAILED, only(DeviceListResponse.class).result());
        }
    }

    @Test void policiesStatusRemovalAndRevocationAreHandled() {
        var device = register(UpdatePolicy.NORMAL);
        UUID viewer = UUID.randomUUID();
        networks.setMember(home.id(), viewer, NetworkRole.VIEWER);
        try (var sync = sync()) {
            subscribe(sync, viewer);
            sent.clear();
            device.progress.setValue(80.0);
            for (int i = 0; i < 19; i++) sync.tick();
            assertTrue(sent.isEmpty());
            sync.tick();
            assertEquals(1, count(MetricUpdate.class));
            sent.clear();
            device.status = DeviceStatus.OFFLINE;
            sync.tick();
            assertEquals("OFFLINE", only(DeviceSnapshot.class).data().getString("status"));
            sent.clear();
            networks.removeMember(home.id(), viewer);
            sync.tick();
            assertEquals(ActionResult.Code.DENIED, only(DeviceListResponse.class).result());
            sent.clear();
            subscribe(sync, player);
            sent.clear();
            devices.unregister(device.id());
            sync.tick();
            assertEquals(ActionResult.Code.FAILED, only(DeviceListResponse.class).result());
        }
    }

    @Test void staticMetricsNeverSendDeltasAndDirectoryRequestUnsubscribes() {
        var device = register(UpdatePolicy.STATIC);
        try (var sync = sync()) {
            subscribe(sync, player);
            sent.clear();
            device.progress.setValue(80.0);
            for (int i = 0; i < 100; i++) sync.tick();
            assertTrue(sent.isEmpty());
            sync.request(player, new DeviceListRequest(UUID.randomUUID(), Optional.empty(), 0));
            assertEquals(List.of(home.id()), only(DeviceListResponse.class).ids());
            assertEquals("OWNER", only(HomeNetworkSnapshot.class).data().getString("role"));
            sent.clear();
            device.status = DeviceStatus.OFFLINE;
            sync.tick();
            assertTrue(sent.isEmpty());
        }
    }

    @Test void onlySubscribedDeclaredEventsAreForwardedAndFloodIsBounded() {
        var device = register(UpdatePolicy.ON_CHANGE);
        UUID viewer = UUID.randomUUID();
        networks.setMember(home.id(), viewer, NetworkRole.VIEWER);
        try (var sync = sync()) {
            subscribe(sync, viewer);
            sent.clear();
            for (int i = 0; i < 100; i++) events.publish(event(device.id(), EVENT));
            assertEquals(16, count(DeviceEventNotification.class));
            sent.clear();
            sync.tick();
            events.publish(event(device.id(), ResourceLocation.parse("test:undeclared")));
            assertTrue(sent.isEmpty());
            networks.removeMember(home.id(), viewer);
            events.publish(event(device.id(), EVENT));
            assertTrue(sent.isEmpty());
        }
        assertEquals(0, events.listenerCount());
    }

    @Test void pageBoundAndQueryBudgetSurvivePlayerLeaving() {
        for (int i = 0; i < 17; i++) register(UpdatePolicy.NORMAL);
        try (var sync = sync()) {
            subscribe(sync, player);
            assertEquals(16, only(DeviceListResponse.class).ids().size());
            assertEquals(16, only(DeviceListResponse.class).nextOffset());
            assertEquals(16, count(DeviceSnapshot.class));
            sent.clear();
            sync.request(player, new DeviceListRequest(UUID.randomUUID(), Optional.of(home.id()), 16));
            assertEquals(1, only(DeviceListResponse.class).ids().size());
            assertEquals(-1, only(DeviceListResponse.class).nextOffset());
            sync.playerLeft(player);
            sent.clear();
            subscribe(sync, player);
            assertEquals(ActionResult.Code.RATE_LIMITED, only(DeviceListResponse.class).result());
        }
    }

    @Test void oversizedHandlerMessageKeepsSuccessfulResultAndIsTruncatedForTransport() {
        var device = register(UpdatePolicy.ON_CHANGE);
        device.message = "x".repeat(5000);
        try (var sync = sync()) {
            sync.execute(player, new ExecuteActionRequest(UUID.randomUUID(), home.id(), device.id(), SET, WireValue.from(85.0)));
            var result = only(ActionResultResponse.class).result();
            assertEquals(ActionResult.Code.SUCCESS, result.code());
            assertEquals(4096, result.message().orElseThrow().getString().length());
            assertEquals(85.0, device.progress.value());
        }
    }

    @Test void snapshotAllocationBudgetRejectsOversizedDataBeforeSending() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("large", "x".repeat(40_000));
        assertThrows(IllegalArgumentException.class, () -> SnapshotEncoder.checked(tag));
    }

    @Test void advertisedTextLengthDoesNotExceedWireLimit() {
        var text = DeviceAction.builder(ResourceLocation.parse("test:text"), Component.literal("Text"),
                        fr.lkdm.homecore.api.action.ActionType.TEXT, String.class)
                .maxLength(8192).handler((context, value) -> ActionResult.success()).build();
        UUID deviceId = UUID.randomUUID();
        DashboardDevice device = new DashboardDevice() {
            public UUID id() { return deviceId; }
            public ResourceLocation deviceType() { return ResourceLocation.parse("test:text_device"); }
            public Component displayName() { return Component.literal("Text device"); }
            public DeviceStatus status() { return DeviceStatus.ONLINE; }
            public List<DeviceAction<?>> actions() { return List.of(text); }
        };
        assertTrue(text.validate("x".repeat(5000)).isSuccess());
        assertEquals(4096, SnapshotEncoder.device(device).getList("actions", Tag.TAG_COMPOUND)
                .getCompound(0).getInt("maxLength"));
    }

    @Test void longPresentationTextIsTruncatedWithoutDiscardingDeviceSchema() {
        String longText = "x".repeat(5000);
        var metric = DeviceMetric.builder(PROGRESS, Component.literal(longText), MetricTypes.INTEGER, 42)
                .unit(new fr.lkdm.homecore.api.metric.Unit(ResourceLocation.parse("test:unit"), longText)).build();
        var action = DeviceAction.button(SET, Component.literal(longText)).description(Component.literal(longText))
                .handler((context, value) -> ActionResult.success()).build();
        UUID identity = UUID.randomUUID();
        DashboardDevice fixture = new DashboardDevice() {
            public UUID id() { return identity; }
            public ResourceLocation deviceType() { return ResourceLocation.parse("test:long_text"); }
            public Component displayName() { return Component.literal("x".repeat(252) + "\uD83D\uDE00" + longText); }
            public DeviceStatus status() { return DeviceStatus.ONLINE.withMessage(Component.literal(longText)); }
            public List<DeviceMetric<?>> metrics() { return List.of(metric); }
            public List<DeviceAction<?>> actions() { return List.of(action); }
        };
        var snapshot = SnapshotEncoder.device(fixture);
        assertTrue(snapshot.getString("name").length() <= 256);
        assertFalse(snapshot.getString("name").contains("\uD83D"), "Truncation must not split a surrogate pair");
        assertEquals(1024, snapshot.getString("message").length());
        assertEquals("ONLINE", snapshot.getString("status"));
        var encodedMetric = snapshot.getList("metrics", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(42, WireValue.fromTag(encodedMetric.getCompound("value")).value());
        assertEquals(PROGRESS.toString(), encodedMetric.getString("id"));
        assertEquals(256, encodedMetric.getString("name").length());
        assertEquals(64, encodedMetric.getString("unitSymbol").length());
        var encodedAction = snapshot.getList("actions", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(SET.toString(), encodedAction.getString("id"));
        assertEquals(256, encodedAction.getString("name").length());
        assertEquals(1024, encodedAction.getString("description").length());
    }

    @Test void overlongMachineIdentifiersRemainStrictlyRejected() {
        UUID identity = UUID.randomUUID();
        DashboardDevice fixture = new DashboardDevice() {
            public UUID id() { return identity; }
            public ResourceLocation deviceType() { return ResourceLocation.parse("test:" + "x".repeat(300)); }
            public Component displayName() { return Component.literal("Valid label"); }
            public DeviceStatus status() { return DeviceStatus.ONLINE; }
        };
        assertThrows(IllegalArgumentException.class, () -> SnapshotEncoder.device(fixture));
    }

    @Test void deniedReplacementAndQueryLimitPreserveExistingSubscription() {
        var device = register(UpdatePolicy.ON_CHANGE);
        var other = networks.createNetwork("Private", UUID.randomUUID());
        try (var sync = sync()) {
            subscribe(sync, player);
            sent.clear();
            sync.request(player, new DeviceListRequest(UUID.randomUUID(), Optional.of(other.id()), 0));
            assertEquals(ActionResult.Code.DENIED, only(DeviceListResponse.class).result());
            sent.clear();
            device.progress.setValue(80.0);
            sync.tick();
            assertEquals(80.0, only(MetricUpdate.class).value().value());
            sent.clear();
            subscribe(sync, player);
            assertEquals(ActionResult.Code.RATE_LIMITED, only(DeviceListResponse.class).result());
            sent.clear();
            device.progress.setValue(90.0);
            sync.tick();
            assertEquals(90.0, only(MetricUpdate.class).value().value());
        }
    }

    private ServerSync sync() { return new ServerSync(devices, networks, permissions, executor, events,
            id -> true, (id, payload) -> sent.add(payload), () -> { }, clock::get); }
    private void subscribe(ServerSync sync, UUID player) {
        sync.request(player, new DeviceListRequest(UUID.randomUUID(), Optional.of(home.id()), 0));
    }
    private TestDevice register(UpdatePolicy policy) {
        TestDevice device = new TestDevice(policy);
        devices.register(device);
        networks.addDevice(home.id(), device.id());
        return device;
    }
    private static DeviceEvent event(UUID device, ResourceLocation type) {
        return new DeviceEvent(type, device, Instant.now(), DeviceEvent.Severity.INFO, Map.of("progress", "85"));
    }
    private long count(Class<?> type) { return sent.stream().filter(type::isInstance).count(); }
    private <T> T only(Class<T> type) {
        List<T> values = sent.stream().filter(type::isInstance).map(type::cast).toList();
        assertEquals(1, values.size(), type.getSimpleName());
        return values.getFirst();
    }

    private static final class TestDevice implements DashboardDevice {
        private final UUID id = UUID.randomUUID();
        private final DeviceMetric<Double> progress;
        private final DeviceAction<Double> action;
        private DeviceStatus status = DeviceStatus.ONLINE;
        private String message = "";
        TestDevice(UpdatePolicy policy) {
            progress = DeviceMetric.builder(PROGRESS, Component.literal("Progress"), MetricTypes.DOUBLE, 73.0)
                    .range(0, 100).updatePolicy(policy).build();
            action = DeviceAction.slider(SET, Component.literal("Set progress"), 0, 100).handler((context, value) -> {
                progress.setValue(value); return ActionResult.of(ActionResult.Code.SUCCESS, Component.literal(message));
            }).build();
        }
        public UUID id() { return id; }
        public ResourceLocation deviceType() { return ResourceLocation.parse("test:machine"); }
        public Component displayName() { return Component.literal("Machine"); }
        public DeviceStatus status() { return status; }
        public List<DeviceMetric<?>> metrics() { return List.of(progress); }
        public List<DeviceAction<?>> actions() { return List.of(action); }
        public Set<ResourceLocation> eventTypes() { return Set.of(EVENT); }
    }
}
