package fr.lkdm.homecore.api.client;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homecore.api.transport.WireValue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientDeviceCacheTest {
    private final UUID network = UUID.randomUUID();
    private final UUID device = UUID.randomUUID();
    private final ResourceLocation metric = ResourceLocation.fromNamespaceAndPath("homecore", "progress");

    @Test void watchRosterRetainsDeltasAndAcceptsNetworkWideEvents() {
        var cache = new ClientDeviceCache();
        UUID request = UUID.randomUUID(), other = UUID.randomUUID();
        cache.accept(new HomeCorePayloads.NetworkWatchResponse(request, network, List.of(device), 1, false, ActionResult.Code.SUCCESS));
        cache.accept(snapshot(device));
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 8, WireValue.from(8.0)));
        cache.accept(new HomeCorePayloads.NetworkWatchResponse(request, network, List.of(device, other), 2, false, ActionResult.Code.SUCCESS));
        assertEquals(1, cache.devices().size()); assertEquals(1, cache.metricUpdates().size());
        var received = new AtomicInteger();
        cache.listen(payload -> { if (payload instanceof HomeCorePayloads.DeviceEventNotification) received.incrementAndGet(); });
        var event = new fr.lkdm.homecore.api.event.DeviceEvent(metric, other, java.time.Instant.EPOCH,
                fr.lkdm.homecore.api.event.DeviceEvent.Severity.INFO, java.util.Map.of("message", "Outside cached roster"));
        cache.accept(new HomeCorePayloads.DeviceEventNotification(network, event));
        assertEquals(1, received.get());
        cache.accept(new HomeCorePayloads.NetworkWatchResponse(request, network, List.of(), 0, false, ActionResult.Code.DENIED));
        cache.accept(new HomeCorePayloads.DeviceEventNotification(network, event));
        assertEquals(1, received.get()); assertTrue(cache.devices().isEmpty()); assertTrue(cache.metricUpdates().isEmpty());
    }

    private HomeCorePayloads.DeviceSnapshot snapshot(UUID deviceId) {
        var definition = new CompoundTag();
        definition.putString("id", metric.toString()); definition.putLong("revision", 4);
        var metrics = new ListTag(); metrics.add(definition);
        var data = new CompoundTag(); data.put("metrics", metrics);
        return new HomeCorePayloads.DeviceSnapshot(network, deviceId, data);
    }

    @Test void ignoresUnknownAndStaleMetricsButAcceptsNewerRevision() {
        var cache = new ClientDeviceCache();
        cache.accept(snapshot(device));
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 4, WireValue.from(4.0)));
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, ResourceLocation.fromNamespaceAndPath("homecore", "unknown"), 5, WireValue.from(5.0)));
        assertEquals(0, cache.updateCount());
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 6, WireValue.from(6.0)));
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 5, WireValue.from(5.0)));
        assertEquals(1, cache.updateCount());
        assertEquals(6L, cache.metricUpdates().values().iterator().next().revision());
    }

    @Test void revokedNetworkPurgesSnapshotsDeltasAndRecentSensitiveMessages() {
        var cache = new ClientDeviceCache();
        cache.accept(snapshot(device));
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 6, WireValue.from(6.0)));
        cache.accept(new HomeCorePayloads.DeviceListResponse(UUID.randomUUID(), Optional.of(network), List.of(), -1, ActionResult.Code.DENIED));
        assertTrue(cache.devices().isEmpty());
        assertTrue(cache.metricUpdates().isEmpty());
        assertEquals(1, cache.recentMessages().size());
        assertInstanceOf(HomeCorePayloads.DeviceListResponse.class, cache.recentMessages().getFirst());
    }

    @Test void replacementPagePrunesPriorDevicesAndPreservesReturnedOnes() {
        var cache = new ClientDeviceCache();
        UUID other = UUID.randomUUID();
        cache.accept(snapshot(device)); cache.accept(snapshot(other));
        cache.accept(new HomeCorePayloads.DeviceListResponse(UUID.randomUUID(), Optional.of(network), List.of(other), -1, ActionResult.Code.SUCCESS));
        assertEquals(1, cache.devices().size());
        assertTrue(cache.devices().containsKey(new ClientDeviceCache.DeviceKey(network, other)));
    }

    @Test void closingAndClearingReleaseListenersAndSessionState() throws Exception {
        var cache = new ClientDeviceCache();
        var calls = new AtomicInteger();
        var listener = cache.listen(payload -> calls.incrementAndGet());
        cache.accept(snapshot(device)); listener.close(); cache.accept(snapshot(device));
        assertEquals(1, calls.get());
        cache.listen(payload -> calls.incrementAndGet()); cache.clear(); cache.accept(snapshot(device));
        assertEquals(1, calls.get());
        assertEquals(1, cache.snapshotCount());
        assertEquals(1, cache.recentMessages().size());
    }

    @Test void replacementSnapshotRefreshesBaselineAndForgettingDropsOldDefinitions() {
        var cache = new ClientDeviceCache();
        cache.accept(snapshot(device));
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 6, WireValue.from(6.0)));
        var updated = snapshot(device).data();
        updated.getList("metrics", 10).getCompound(0).putLong("revision", 8);
        cache.accept(new HomeCorePayloads.DeviceSnapshot(network, device, updated));
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 7, WireValue.from(7.0)));
        assertTrue(cache.metricUpdates().isEmpty());
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 9, WireValue.from(9.0)));
        assertEquals(1, cache.metricUpdates().size());
        cache.forgetNetwork(network);
        cache.accept(new HomeCorePayloads.DeviceSnapshot(network, device, new CompoundTag()));
        cache.accept(new HomeCorePayloads.MetricUpdate(network, device, metric, 10, WireValue.from(10.0)));
        assertTrue(cache.metricUpdates().isEmpty());
    }
}
