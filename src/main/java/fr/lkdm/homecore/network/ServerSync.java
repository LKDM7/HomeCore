package fr.lkdm.homecore.network;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.event.DeviceEventBus;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import fr.lkdm.homecore.api.security.ActionExecutor;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homecore.api.security.PermissionValidator;
import fr.lkdm.homecore.api.security.RateLimiter;
import fr.lkdm.homecore.api.transport.HomeCorePayloads.*;
import fr.lkdm.homecore.api.transport.WireValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-thread snapshot and delta delivery for one bounded page per connected player. */
public final class ServerSync implements AutoCloseable {
    private static final int PAGE_SIZE = 16;
    private static final int MAX_SUBSCRIPTIONS = 1024;
    private static final int EVENTS_PER_TICK = 16;
    private final DeviceRegistry devices;
    private final HomeNetworkManager networks;
    private final PermissionValidator permissions;
    private final ActionExecutor executor;
    private final Predicate<UUID> online;
    private final BiConsumer<UUID, CustomPacketPayload> sender;
    private final Runnable threadGuard;
    private final RateLimiter queries;
    private final RateLimiter eventLimit;
    private final Map<UUID, Page> subscriptions = new HashMap<>();
    private final DeviceEventBus.Subscription eventSubscription;
    private long tick;
    private boolean closed;

    public ServerSync(MinecraftServer server, DeviceRegistry devices, HomeNetworkManager networks,
                      PermissionValidator permissions, ActionExecutor executor, DeviceEventBus events) {
        this(devices, networks, permissions, executor, events,
                id -> server.getPlayerList().getPlayer(id) != null,
                (id, payload) -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) PacketDistributor.sendToPlayer(player, payload);
                }, () -> {
                    if (!server.isSameThread()) throw new IllegalStateException("HomeCore synchronization requires the server thread");
                }, System::nanoTime);
    }

    ServerSync(DeviceRegistry devices, HomeNetworkManager networks, PermissionValidator permissions,
               ActionExecutor executor, DeviceEventBus events, Predicate<UUID> online,
               BiConsumer<UUID, CustomPacketPayload> sender, Runnable threadGuard, LongSupplier clock) {
        this.devices = Objects.requireNonNull(devices);
        this.networks = Objects.requireNonNull(networks);
        this.permissions = Objects.requireNonNull(permissions);
        this.executor = Objects.requireNonNull(executor);
        this.online = Objects.requireNonNull(online);
        this.sender = Objects.requireNonNull(sender);
        this.threadGuard = Objects.requireNonNull(threadGuard);
        queries = new RateLimiter(2, Duration.ofSeconds(1), 4096, clock);
        eventLimit = new RateLimiter(32, Duration.ofSeconds(1), 4096, clock);
        eventSubscription = Objects.requireNonNull(events).subscribe(this::event);
    }

    public void request(ServerPlayer player, DeviceListRequest request) { request(player.getUUID(), request); }
    public void execute(ServerPlayer player, ExecuteActionRequest request) { execute(player.getUUID(), request); }

    public void unsubscribe(ServerPlayer player, Unsubscribe request) {
        checkThread();
        Page page = subscriptions.get(player.getUUID());
        if (page != null && page.network.equals(request.networkId())) subscriptions.remove(player.getUUID());
    }

    void request(UUID player, DeviceListRequest request) {
        checkThread();
        if (!online.test(player)) return;
        if (!queries.tryAcquire(player)) {
            respond(player, request, List.of(), -1, ActionResult.Code.RATE_LIMITED);
            return;
        }
        try {
            if (request.offset() < 0) {
                removeRequestedPage(player, request);
                respond(player, request, List.of(), -1, ActionResult.Code.INVALID_PARAMETER);
                return;
            }
            if (request.networkId().isEmpty()) {
                List<HomeNetwork> accessible = networks.getNetworksForPlayer(player).stream()
                        .filter(network -> permissions.hasPermission(network, player, Permission.VIEW)).toList();
                int start = Math.min(request.offset(), accessible.size());
                int end = Math.min(start + PAGE_SIZE, accessible.size());
                List<HomeNetwork> page = accessible.subList(start, end);
                List<HomeNetworkSnapshot> snapshots = new ArrayList<>();
                for (HomeNetwork network : page) snapshots.add(networkSnapshot(network, player));
                subscriptions.remove(player);
                respond(player, request, page.stream().map(HomeNetwork::id).toList(), end < accessible.size() ? end : -1, ActionResult.Code.SUCCESS);
                snapshots.forEach(snapshot -> send(player, snapshot));
                return;
            }
            UUID networkId = request.networkId().orElseThrow();
            HomeNetwork network = networks.getNetwork(networkId).orElse(null);
            if (!permissions.hasPermission(network, player, Permission.VIEW)) {
                removeRequestedPage(player, request);
                respond(player, request, List.of(), -1, ActionResult.Code.DENIED);
                return;
            }
            if (subscriptions.size() >= MAX_SUBSCRIPTIONS && !subscriptions.containsKey(player)) {
                respond(player, request, List.of(), -1, ActionResult.Code.RATE_LIMITED);
                return;
            }
            List<UUID> available = network.devices().stream().sorted()
                    .filter(id -> devices.get(id).isPresent()).toList();
            int start = Math.min(request.offset(), available.size());
            int end = Math.min(start + PAGE_SIZE, available.size());
            List<UUID> ids = List.copyOf(available.subList(start, end));
            Page page = new Page(networkId, request.requestId(), ids);
            List<DeviceSnapshot> snapshots = new ArrayList<>();
            for (UUID id : ids) {
                DashboardDevice device = devices.get(id).orElseThrow();
                CompoundTag snapshot = SnapshotEncoder.device(device);
                page.devices.put(id, tracked(device, snapshot));
                snapshots.add(new DeviceSnapshot(networkId, id, snapshot));
            }
            HomeNetworkSnapshot summary = networkSnapshot(network, player);
            subscriptions.put(player, page);
            respond(player, request, ids, end < available.size() ? end : -1, ActionResult.Code.SUCCESS);
            send(player, summary);
            snapshots.forEach(snapshot -> send(player, snapshot));
        } catch (RuntimeException exception) {
            removeRequestedPage(player, request);
            respond(player, request, List.of(), -1, ActionResult.Code.FAILED);
        }
    }

    void execute(UUID player, ExecuteActionRequest request) {
        checkThread();
        if (!online.test(player)) return;
        ActionResult result = executor.executeResolved(player, request.networkId(), request.deviceId(), request.actionId(),
                request.parameter()::toActionValue);
        send(player, new ActionResultResponse(request.requestId(), transportResult(result)));
    }

    public void tick() {
        checkThread();
        tick++;
        Iterator<Map.Entry<UUID, Page>> iterator = subscriptions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            UUID player = entry.getKey();
            Page page = entry.getValue();
            page.eventsThisTick = 0;
            if (!online.test(player)) { iterator.remove(); continue; }
            try {
                HomeNetwork network = networks.getNetwork(page.network).orElse(null);
                if (!permissions.hasPermission(network, player, Permission.VIEW)) {
                    invalidate(player, page, ActionResult.Code.DENIED);
                    iterator.remove(); continue;
                }
                boolean removed = false;
                for (UUID id : page.ids) {
                    if (!network.devices().contains(id) || devices.get(id).isEmpty()) { removed = true; break; }
                }
                if (removed) {
                    invalidate(player, page, ActionResult.Code.FAILED);
                    iterator.remove(); continue;
                }
                for (UUID id : page.ids) update(player, page, id, devices.get(id).orElseThrow());
            } catch (RuntimeException exception) {
                invalidate(player, page, ActionResult.Code.FAILED);
                iterator.remove();
            }
        }
    }

    private void update(UUID player, Page page, UUID id, DashboardDevice device) {
        Tracked tracked = page.devices.get(id);
        if (tracked.device != device || !tracked.status.equals(device.status())) {
            CompoundTag snapshot = SnapshotEncoder.device(device);
            page.devices.put(id, tracked(device, snapshot));
            send(player, new DeviceSnapshot(page.network, id, snapshot));
            return;
        }
        for (var metric : device.metrics()) {
            int interval = interval(metric.updatePolicy());
            if (interval == 0 || tick % interval != 0) continue;
            var snapshot = metric.snapshot();
            Long previous = tracked.revisions.get(metric.id());
            if (previous == null || previous.longValue() != snapshot.revision()) {
                send(player, new MetricUpdate(page.network, id, metric.id(), snapshot.revision(), WireValue.from(snapshot.value())));
                tracked.revisions.put(metric.id(), snapshot.revision());
            }
        }
    }

    private void event(DeviceEvent event) {
        checkThread();
        long textUnits = event.data().entrySet().stream()
                .mapToLong(entry -> (long) entry.getKey().length() + entry.getValue().length()).sum();
        if (textUnits > 8192 || event.type().toString().length() > 256) return;
        DashboardDevice source;
        try {
            source = devices.get(event.source()).orElse(null);
            if (source == null || !source.eventTypes().contains(event.type())) return;
        } catch (RuntimeException exception) { return; }
        for (var entry : subscriptions.entrySet()) {
            UUID player = entry.getKey();
            Page page = entry.getValue();
            if (!online.test(player) || !page.devices.containsKey(event.source()) || page.eventsThisTick >= EVENTS_PER_TICK) continue;
            HomeNetwork network = networks.getNetwork(page.network).orElse(null);
            if (network == null || !network.devices().contains(event.source())
                    || !permissions.hasPermission(network, player, Permission.VIEW)) continue;
            if (!eventLimit.tryAcquire(player)) continue;
            page.eventsThisTick++;
            send(player, new DeviceEventNotification(page.network, event));
        }
    }

    public void playerLeft(UUID player) {
        checkThread();
        subscriptions.remove(player);
        // Keep request tokens until natural expiry: reconnect must not reset the budget.
    }

    @Override public void close() {
        threadGuard.run();
        if (closed) return;
        closed = true;
        eventSubscription.close();
        subscriptions.clear();
        queries.clear();
        eventLimit.clear();
    }

    private void checkThread() {
        threadGuard.run();
        if (closed) throw new IllegalStateException("Server synchronization is closed");
    }
    private void send(UUID player, CustomPacketPayload payload) { sender.accept(player, payload); }
    private static ActionResult transportResult(ActionResult result) {
        return new ActionResult(result.code(), result.message().map(message -> {
            String text = message.getString();
            return Component.literal(text.length() <= 4096 ? text : text.substring(0, 4096));
        }));
    }
    private void respond(UUID player, DeviceListRequest request, List<UUID> ids, int next, ActionResult.Code code) {
        send(player, new DeviceListResponse(request.requestId(), request.networkId(), ids, next, code));
    }
    private void removeRequestedPage(UUID player, DeviceListRequest request) {
        Page previous = subscriptions.get(player);
        if (previous != null && request.networkId().filter(previous.network::equals).isPresent()) subscriptions.remove(player);
    }
    private void invalidate(UUID player, Page page, ActionResult.Code code) {
        send(player, new DeviceListResponse(page.request, Optional.of(page.network), List.of(), -1, code));
    }
    private static HomeNetworkSnapshot networkSnapshot(HomeNetwork network, UUID player) {
        CompoundTag data = SnapshotEncoder.network(network);
        data.putString("role", network.members().get(player).name());
        return new HomeNetworkSnapshot(network.id(), data);
    }
    private static Tracked tracked(DashboardDevice device, CompoundTag snapshot) {
        Map<ResourceLocation, Long> revisions = new HashMap<>();
        for (Tag tag : snapshot.getList("metrics", Tag.TAG_COMPOUND)) {
            CompoundTag metric = (CompoundTag) tag;
            revisions.put(ResourceLocation.parse(metric.getString("id")), metric.getLong("revision"));
        }
        return new Tracked(device, device.status(), revisions);
    }
    private static int interval(UpdatePolicy policy) {
        return switch (policy) { case REALTIME, ON_CHANGE -> 1; case FAST -> 5; case NORMAL -> 20; case SLOW -> 100; case STATIC -> 0; };
    }
    private static final class Page {
        final UUID network;
        final UUID request;
        final List<UUID> ids;
        final Map<UUID, Tracked> devices = new LinkedHashMap<>();
        int eventsThisTick;
        Page(UUID network, UUID request, List<UUID> ids) { this.network = network; this.request = request; this.ids = ids; }
    }
    private record Tracked(DashboardDevice device, DeviceStatus status, Map<ResourceLocation, Long> revisions) { }
}
