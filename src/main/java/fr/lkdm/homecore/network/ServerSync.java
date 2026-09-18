package fr.lkdm.homecore.network;

import static fr.lkdm.homecore.api.transport.HomeCorePayloads.WATCH_SIZE;

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
import java.util.LinkedHashSet;
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

/** Server-thread snapshot/delta delivery for one bounded network watch or legacy page per connected player. */
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
    private final Map<UUID, Watch> watches = new HashMap<>();
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
    public void watch(ServerPlayer player, NetworkWatchRequest request) { watch(player.getUUID(), request); }

    public void unsubscribe(ServerPlayer player, Unsubscribe request) {
        checkThread();
        Page page = subscriptions.get(player.getUUID());
        if (page != null && page.network.equals(request.networkId())) subscriptions.remove(player.getUUID());
        Watch watch = watches.get(player.getUUID());
        if (watch != null && watch.network.equals(request.networkId())) watches.remove(player.getUUID());
    }

    void watch(UUID player, NetworkWatchRequest request) {
        checkThread();
        if (!online.test(player)) return;
        if (!queries.tryAcquire(player)) { watchResult(player, request, ActionResult.Code.RATE_LIMITED); return; }
        HomeNetwork network = networks.getNetwork(request.networkId()).orElse(null);
        if (!permissions.hasPermission(network, player, Permission.VIEW)) {
            Watch previous = watches.get(player);
            if (previous != null && previous.network.equals(request.networkId())) watches.remove(player);
            watchResult(player, request, ActionResult.Code.DENIED);
            return;
        }
        if (subscriptions.size() + watches.size() >= MAX_SUBSCRIPTIONS && !watches.containsKey(player) && !subscriptions.containsKey(player)) {
            watchResult(player, request, ActionResult.Code.RATE_LIMITED); return;
        }
        Watch watch = new Watch(request.networkId(), request.requestId());
        subscriptions.remove(player);
        watches.put(player, watch);
        refreshRoster(player, watch, network, true);
        send(player, networkSnapshot(network, player));
        watch.lastNetwork = network;
    }

    private void watchResult(UUID player, NetworkWatchRequest request, ActionResult.Code result) {
        send(player, new NetworkWatchResponse(request.requestId(), request.networkId(), List.of(), 0, false, result));
    }

    private void refreshRoster(UUID player, Watch watch, HomeNetwork network, boolean initial) {
        List<UUID> available = network.devices().stream().sorted().filter(id -> {
            try { return devices.get(id).filter(device -> networks.isReachable(network.id(), device)).isPresent(); }
            catch (RuntimeException exception) { return true; } // A broken provider remains visible as an ERROR placeholder.
        }).toList();
        List<UUID> ids = List.copyOf(available.subList(0, Math.min(WATCH_SIZE, available.size())));
        if (initial || !ids.equals(watch.ids) || available.size() != watch.totalCount) {
            watch.ids = ids;
            watch.totalCount = available.size();
            watch.devices.keySet().retainAll(ids);
            watch.pending.retainAll(ids);
            watch.failedUntil.keySet().retainAll(ids);
            watch.metricCursors.keySet().retainAll(ids);
            for (UUID id : ids) if (!watch.devices.containsKey(id)) watch.pending.add(id);
            send(player, new NetworkWatchResponse(watch.request, watch.network, ids, available.size(), available.size() > ids.size(), ActionResult.Code.SUCCESS));
        }
        watch.nextRosterTick = tick + 20;
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
                watches.remove(player);
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
            if (subscriptions.size() + watches.size() >= MAX_SUBSCRIPTIONS && !subscriptions.containsKey(player) && !watches.containsKey(player)) {
                respond(player, request, List.of(), -1, ActionResult.Code.RATE_LIMITED);
                return;
            }
            List<UUID> available = network.devices().stream().sorted()
                    .filter(id -> devices.get(id).filter(device -> networks.isReachable(network.id(), device)).isPresent()).toList();
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
            watches.remove(player);
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
                    if (!network.devices().contains(id) || devices.get(id).filter(device -> networks.isReachable(network.id(), device)).isEmpty()) { removed = true; break; }
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
        Iterator<Map.Entry<UUID, Watch>> watchers = watches.entrySet().iterator();
        while (watchers.hasNext()) {
            var entry = watchers.next();
            UUID player = entry.getKey(); Watch watch = entry.getValue();
            watch.eventsThisTick = 0;
            if (!online.test(player)) { watchers.remove(); continue; }
            HomeNetwork network = networks.getNetwork(watch.network).orElse(null);
            if (!permissions.hasPermission(network, player, Permission.VIEW)) {
                watchResult(player, new NetworkWatchRequest(watch.request, watch.network), ActionResult.Code.DENIED);
                watchers.remove(); continue;
            }
            if (network != watch.lastNetwork || tick >= watch.nextRosterTick) refreshRoster(player, watch, network, false);
            if (network != watch.lastNetwork) {
                send(player, networkSnapshot(network, player));
                watch.lastNetwork = network;
            }
            WatchBudget budget = new WatchBudget();
            for (UUID id : List.copyOf(watch.pending)) {
                if (budget.snapshots == 0) break;
                watchSnapshot(player, watch, id, budget);
            }
            int size = watch.ids.size();
            int resume = -1;
            for (int index = 0; index < size; index++) {
                UUID id = watch.ids.get((index + watch.cursor) % size);
                if (watch.pending.contains(id) || watch.failedUntil.getOrDefault(id, 0L) > tick) continue;
                try {
                    DashboardDevice device = devices.get(id).orElse(null);
                    if (device == null || !networks.isReachable(watch.network, device)) { watch.nextRosterTick = tick; continue; }
                    Tracked tracked = watch.devices.get(id);
                    if (tracked == null || watch.failedUntil.containsKey(id)
                            || tracked.device != device || !tracked.status.equals(device.status())) {
                        if (budget.snapshots > 0) watchSnapshot(player, watch, id, budget);
                        continue;
                    }
                    if (budget.deltas == 0) continue;
                    var metrics = device.metrics();
                    if (metrics.size() > 128) throw new IllegalArgumentException("Device metric limit exceeded");
                    int metricStart = metrics.isEmpty() ? 0 : watch.metricCursors.getOrDefault(id, 0) % metrics.size();
                    for (int metricIndex = 0; metricIndex < metrics.size(); metricIndex++) {
                        if (budget.deltas == 0) break;
                        int metricPosition = (metricStart + metricIndex) % metrics.size();
                        var metric = metrics.get(metricPosition);
                        watch.metricCursors.put(id, (metricPosition + 1) % metrics.size());
                        int interval = interval(metric.updatePolicy());
                        if (interval == 0 || tick % interval != 0) continue;
                        var value = metric.snapshot();
                        Long previous = tracked.revisions.get(metric.id());
                        if (previous == null) {
                            // A provider changing its declared metric IDs cannot grow the revision map without bounds.
                            if (budget.snapshots > 0) watchSnapshot(player, watch, id, budget);
                            break;
                        }
                        if (previous.longValue() != value.revision()) {
                            send(player, new MetricUpdate(watch.network, id, metric.id(), value.revision(), WireValue.from(value.value())));
                            tracked.revisions.put(metric.id(), value.revision());
                            budget.deltas--;
                        }
                    }
                    if (budget.deltas == 0 && resume < 0) resume = (watch.cursor + index + 1) % size;
                } catch (RuntimeException exception) {
                    if (budget.snapshots > 0) watchFallback(player, watch, id, budget);
                    else watch.pending.add(id);
                }
            }
            if (size > 0) watch.cursor = resume >= 0 ? resume : (watch.cursor + 1) % size;
        }
    }

    private void watchSnapshot(UUID player, Watch watch, UUID id, WatchBudget budget) {
        try {
            DashboardDevice device = devices.get(id).orElse(null);
            if (device == null || !networks.isReachable(watch.network, device)) { watch.nextRosterTick = tick; watch.pending.remove(id); return; }
            CompoundTag snapshot = SnapshotEncoder.device(device);
            Tracked tracked = tracked(device, snapshot);
            watch.devices.put(id, tracked);
            watch.failedUntil.remove(id);
            watch.metricCursors.remove(id);
            watch.pending.remove(id);
            budget.snapshots--;
            send(player, new DeviceSnapshot(watch.network, id, snapshot));
        } catch (RuntimeException exception) { watchFallback(player, watch, id, budget); }
    }

    private void watchFallback(UUID player, Watch watch, UUID id, WatchBudget budget) {
        boolean alreadyFailed = watch.failedUntil.containsKey(id);
        watch.failedUntil.put(id, tick + 20);
        watch.pending.remove(id);
        if (alreadyFailed && watch.devices.containsKey(id)) return;
        CompoundTag snapshot = new CompoundTag();
        snapshot.putUUID("id", id);
        snapshot.putString("name", "Unavailable device " + id.toString().substring(0, 8));
        snapshot.putString("type", "homecore:unavailable");
        snapshot.putString("status", "ERROR");
        snapshot.putString("message", "Device metadata is unavailable");
        watch.devices.put(id, new Tracked(null, DeviceStatus.ERROR, new HashMap<>()));
        budget.snapshots--;
        send(player, new DeviceSnapshot(watch.network, id, snapshot));
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
            if (network == null || !networks.isReachable(network.id(), source)
                    || !permissions.hasPermission(network, player, Permission.VIEW)) continue;
            if (!eventLimit.tryAcquire(player)) continue;
            page.eventsThisTick++;
            send(player, new DeviceEventNotification(page.network, event));
        }
        for (var entry : watches.entrySet()) {
            UUID player = entry.getKey(); Watch watch = entry.getValue();
            if (!online.test(player) || watch.eventsThisTick >= EVENTS_PER_TICK) continue;
            HomeNetwork network = networks.getNetwork(watch.network).orElse(null);
            if (network == null || !networks.isReachable(network.id(), source)
                    || !permissions.hasPermission(network, player, Permission.VIEW) || !eventLimit.tryAcquire(player)) continue;
            watch.eventsThisTick++;
            send(player, new DeviceEventNotification(watch.network, event));
        }
    }

    public void playerLeft(UUID player) {
        checkThread();
        subscriptions.remove(player);
        watches.remove(player);
        // Keep request tokens until natural expiry: reconnect must not reset the budget.
    }

    @Override public void close() {
        threadGuard.run();
        if (closed) return;
        closed = true;
        eventSubscription.close();
        subscriptions.clear();
        watches.clear();
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
    private static final class WatchBudget {
        int snapshots = 16;
        int deltas = 256;
    }
    private static final class Watch {
        final UUID network;
        final UUID request;
        List<UUID> ids = List.of();
        int totalCount;
        final Map<UUID, Tracked> devices = new LinkedHashMap<>();
        final LinkedHashSet<UUID> pending = new LinkedHashSet<>();
        final Map<UUID, Long> failedUntil = new HashMap<>();
        final Map<UUID, Integer> metricCursors = new HashMap<>();
        HomeNetwork lastNetwork;
        long nextRosterTick;
        int eventsThisTick;
        int cursor;
        Watch(UUID network, UUID request) { this.network = network; this.request = request; }
    }
}
