package fr.lkdm.homecore.api.client;

import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homecore.api.action.ActionResult;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded client session state. This class is safe to load on a dedicated server.
 * Snapshots describe initial state; metric updates are stored separately by revision.
 * Consumers must dispose subscriptions when their screen closes.
 */
public final class ClientDeviceCache {
    /** Creates an empty isolated cache; transport handlers use INSTANCE. */
    public ClientDeviceCache() { }

    /** Shared cache populated by HomeCore's client payload handlers. */
    public static final ClientDeviceCache INSTANCE = new ClientDeviceCache();
    private final Map<DeviceKey, HomeCorePayloads.DeviceSnapshot> devices = new LinkedHashMap<>();
    private final Map<UUID, HomeCorePayloads.HomeNetworkSnapshot> networks = new LinkedHashMap<>();
    private final Map<MetricKey, HomeCorePayloads.MetricUpdate> metrics = new LinkedHashMap<>();
    private final Map<MetricKey, Long> baselineRevisions = new LinkedHashMap<>();
    private final ArrayDeque<CustomPacketPayload> recent = new ArrayDeque<>();
    private final Map<Long, Consumer<CustomPacketPayload>> listeners = new LinkedHashMap<>();
    private final Map<UUID, UUID> watchedNetworks = new LinkedHashMap<>();
    private long nextListener;
    private long snapshotCount;
    private long updateCount;

    /** Identifies a device within a subscribed network.
     * @param networkId containing network
     * @param deviceId device identity
     */
    public record DeviceKey(UUID networkId, UUID deviceId) { }
    /** Identifies an individual metric.
     * @param device containing device
     * @param metricId metric identity
     */
    public record MetricKey(DeviceKey device, ResourceLocation metricId) { }

    /** Accepts one decoded server message on the client thread.
     * @param payload decoded server payload
     */
    public synchronized void accept(CustomPacketPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload instanceof HomeCorePayloads.DeviceSnapshot snapshot) {
            DeviceKey key = new DeviceKey(snapshot.networkId(), snapshot.deviceId());
            var definitions = snapshot.data().getList("metrics", 10);
            if (definitions.size() > 128) throw new IllegalArgumentException("Too many metric definitions");
            if (!devices.containsKey(key) && devices.size() >= 256) forgetDevice(devices.keySet().iterator().next());
            devices.put(key, snapshot);
            metrics.keySet().removeIf(metric -> metric.device().equals(key));
            baselineRevisions.keySet().removeIf(metric -> metric.device().equals(key));
            for (int index = 0; index < definitions.size(); index++) {
                var definition = definitions.getCompound(index);
                ResourceLocation metricId = ResourceLocation.parse(definition.getString("id"));
                baselineRevisions.put(new MetricKey(key, metricId), definition.getLong("revision"));
            }
            snapshotCount++;
        } else if (payload instanceof HomeCorePayloads.HomeNetworkSnapshot snapshot) {
            if (!networks.containsKey(snapshot.networkId()) && networks.size() >= 16) forgetNetwork(networks.keySet().iterator().next());
            networks.put(snapshot.networkId(), snapshot);
        } else if (payload instanceof HomeCorePayloads.MetricUpdate update) {
            MetricKey key = new MetricKey(new DeviceKey(update.networkId(), update.deviceId()), update.metricId());
            if (!devices.containsKey(key.device())) return;
            Long baseline = baselineRevisions.get(key);
            if (baseline == null || update.revision() <= baseline) return;
            var previous = metrics.get(key);
            if (previous != null && previous.revision() >= update.revision()) return;
            if (!metrics.containsKey(key) && metrics.size() >= 16384) metrics.remove(metrics.keySet().iterator().next());
            metrics.put(key, update);
            updateCount++;
        } else if (payload instanceof HomeCorePayloads.NetworkWatchResponse response) {
            if (response.result() == ActionResult.Code.DENIED || response.result() == ActionResult.Code.FAILED) forgetNetwork(response.networkId());
            else if (response.result() == ActionResult.Code.SUCCESS) {
                watchedNetworks.clear();
                watchedNetworks.put(response.networkId(), response.requestId());
                List.copyOf(devices.keySet()).stream()
                        .filter(key -> !key.networkId().equals(response.networkId()) || !response.ids().contains(key.deviceId()))
                        .forEach(this::forgetDevice);
            }
        } else if (payload instanceof HomeCorePayloads.DeviceListResponse response) {
            if (response.result() == ActionResult.Code.DENIED || response.result() == ActionResult.Code.FAILED) response.networkId().ifPresent(this::forgetNetwork);
            else if (response.result() != ActionResult.Code.SUCCESS) { /* Rate-limited requests retain the active subscription. */ }
            else if (response.networkId().isPresent()) {
                watchedNetworks.clear();
                UUID network = response.networkId().orElseThrow();
                List.copyOf(devices.keySet()).stream()
                        .filter(key -> !key.networkId().equals(network) || !response.ids().contains(key.deviceId()))
                        .forEach(this::forgetDevice);
            } else {
                watchedNetworks.clear();
                List.copyOf(devices.keySet()).forEach(this::forgetDevice);
                List.copyOf(networks.keySet()).stream().filter(id -> !response.ids().contains(id)).forEach(this::forgetNetwork);
            }
        } else if (payload instanceof HomeCorePayloads.DeviceEventNotification notification) {
            if (!watchedNetworks.containsKey(notification.networkId())
                    && !devices.containsKey(new DeviceKey(notification.networkId(), notification.event().source()))) return;
        } else if (!(payload instanceof HomeCorePayloads.ActionResultResponse)) {
            throw new IllegalArgumentException("Expected a HomeCore server payload");
        }
        if (recent.size() == 128) recent.removeFirst();
        recent.addLast(payload);
        for (var listener : List.copyOf(listeners.values())) {
            try { listener.accept(payload); } catch (RuntimeException ignored) { }
        }
    }

    /** Returns initial device snapshots; overlay {@link #metricUpdates()} for current values.
     * @return immutable device map
     */
    public synchronized Map<DeviceKey, HomeCorePayloads.DeviceSnapshot> devices() { return Map.copyOf(devices); }
    /** Returns network snapshots.
     * @return immutable network map
     */
    public synchronized Map<UUID, HomeCorePayloads.HomeNetworkSnapshot> networks() { return Map.copyOf(networks); }
    /** Returns latest received metric deltas.
     * @return immutable update map
     */
    public synchronized Map<MetricKey, HomeCorePayloads.MetricUpdate> metricUpdates() { return Map.copyOf(metrics); }
    /** Returns the last 128 received messages in arrival order.
     * @return immutable recent messages
     */
    public synchronized List<CustomPacketPayload> recentMessages() { return List.copyOf(recent); }
    /** Returns accepted full device snapshot count for this session.
     * @return snapshot count
     */
    public synchronized long snapshotCount() { return snapshotCount; }
    /** Returns accepted metric delta count for this session.
     * @return delta count
     */
    public synchronized long updateCount() { return updateCount; }
    /** Adds a listener; closing the returned handle removes only this registration.
     * @param listener client-thread callback, exceptions isolated
     * @return removable subscription
     */
    public synchronized AutoCloseable listen(Consumer<CustomPacketPayload> listener) {
        if (listeners.size() >= 128) throw new IllegalStateException("Too many cache listeners");
        long token = ++nextListener;
        listeners.put(token, Objects.requireNonNull(listener));
        return () -> { synchronized (ClientDeviceCache.this) { listeners.remove(token); } };
    }
    /** Removes cached data after unsubscribing from a network.
     * @param network network identity
     */
    public synchronized void forgetNetwork(UUID network) {
        watchedNetworks.remove(network);
        networks.remove(network);
        List.copyOf(devices.keySet()).stream().filter(key -> key.networkId().equals(network)).forEach(this::forgetDevice);
        recent.removeIf(payload -> belongsTo(payload, network));
    }
    private static boolean belongsTo(CustomPacketPayload payload, UUID network) {
        if (payload instanceof HomeCorePayloads.DeviceSnapshot value) return value.networkId().equals(network);
        if (payload instanceof HomeCorePayloads.HomeNetworkSnapshot value) return value.networkId().equals(network);
        if (payload instanceof HomeCorePayloads.MetricUpdate value) return value.networkId().equals(network);
        if (payload instanceof HomeCorePayloads.DeviceEventNotification value) return value.networkId().equals(network);
        if (payload instanceof HomeCorePayloads.DeviceListResponse value) return value.networkId().filter(network::equals).isPresent();
        if (payload instanceof HomeCorePayloads.NetworkWatchResponse value) return value.networkId().equals(network);
        return false;
    }
    private void forgetDevice(DeviceKey key) {
        devices.remove(key);
        metrics.keySet().removeIf(metric -> metric.device().equals(key));
        baselineRevisions.keySet().removeIf(metric -> metric.device().equals(key));
        recent.removeIf(payload -> payload instanceof HomeCorePayloads.DeviceSnapshot value
                && value.networkId().equals(key.networkId()) && value.deviceId().equals(key.deviceId())
                || payload instanceof HomeCorePayloads.MetricUpdate update
                && update.networkId().equals(key.networkId()) && update.deviceId().equals(key.deviceId())
                || payload instanceof HomeCorePayloads.DeviceEventNotification event
                && event.networkId().equals(key.networkId()) && event.event().source().equals(key.deviceId()));
    }
    /** Clears cached state and listeners when disconnected. */
    public synchronized void clear() {
        devices.clear(); networks.clear(); metrics.clear(); baselineRevisions.clear(); recent.clear(); listeners.clear(); watchedNetworks.clear();
        snapshotCount = 0; updateCount = 0;
    }
}
