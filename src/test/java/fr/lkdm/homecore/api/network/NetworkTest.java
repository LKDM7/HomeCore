package fr.lkdm.homecore.api.network;

import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkTest {
    @Test void logicalConnectionRequiresMembershipLoadedDeviceAndOnlineStatus() {
        var manager = new HomeNetworkManager();
        var registry = new DeviceRegistry();
        var network = manager.createNetwork("Home", UUID.randomUUID());
        UUID deviceId = UUID.randomUUID();
        DeviceStatus[] status = {DeviceStatus.ONLINE};
        var device = new DashboardDevice() {
            @Override public UUID id() { return deviceId; }
            @Override public ResourceLocation deviceType() { return ResourceLocation.parse("test:machine"); }
            @Override public Component displayName() { return Component.literal("Machine"); }
            @Override public DeviceStatus status() { return status[0]; }
        };
        assertEquals(ConnectionState.UNREACHABLE, manager.connectionState(network.id(), deviceId, registry));
        manager.addDevice(network.id(), deviceId);
        assertEquals(ConnectionState.OFFLINE, manager.connectionState(network.id(), deviceId, registry));
        registry.register(device);
        assertEquals(ConnectionState.CONNECTED, manager.connectionState(network.id(), deviceId, registry));
        status[0] = DeviceStatus.WARNING;
        assertEquals(ConnectionState.OFFLINE, manager.connectionState(network.id(), deviceId, registry));
        registry.unregister(deviceId);
        assertEquals(ConnectionState.OFFLINE, manager.connectionState(network.id(), deviceId, registry));
    }
    @Test void createsOwnedNetworkAndTracksActualChangesOnly() {
        var dirty = new AtomicInteger();
        var manager = new HomeNetworkManager(dirty::incrementAndGet);
        UUID owner = UUID.randomUUID();
        HomeNetwork network = manager.createNetwork("Home", owner);
        assertEquals(NetworkRole.OWNER, network.members().get(owner));
        assertEquals(List.of(network), manager.getNetworksForPlayer(owner));
        UUID device = UUID.randomUUID();
        assertTrue(manager.addDevice(network.id(), device));
        assertFalse(manager.addDevice(network.id(), device));
        assertEquals(Set.of(device), manager.getDevices(network.id()));
        assertTrue(network.devices().isEmpty());
        assertTrue(manager.removeDevice(network.id(), device));
        assertFalse(manager.removeDevice(network.id(), device));
        assertEquals(3, dirty.get());
        assertTrue(manager.deleteNetwork(network.id()));
        assertFalse(manager.deleteNetwork(network.id()));
        assertEquals(4, dirty.get());
    }

    @Test void membershipPreservesUniqueOwnerAndSnapshots() {
        var manager = new HomeNetworkManager();
        UUID owner = UUID.randomUUID(), member = UUID.randomUUID();
        var network = manager.createNetwork("Home", owner);
        assertTrue(manager.setMember(network.id(), member, NetworkRole.MEMBER));
        assertFalse(manager.setMember(network.id(), member, NetworkRole.MEMBER));
        assertTrue(manager.setMember(network.id(), member, NetworkRole.VIEWER));
        assertEquals(1, manager.getNetworksForPlayer(member).size());
        assertFalse(network.members().containsKey(member));
        assertFalse(manager.setMember(network.id(), owner, NetworkRole.OWNER));
        assertThrows(IllegalArgumentException.class, () -> manager.setMember(network.id(), owner, NetworkRole.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> manager.setMember(network.id(), member, NetworkRole.OWNER));
        assertThrows(IllegalArgumentException.class, () -> manager.removeMember(network.id(), owner));
        assertTrue(manager.removeMember(network.id(), member));
        assertFalse(manager.removeMember(network.id(), member));
        assertTrue(manager.getNetworksForPlayer(member).isEmpty());
    }

    @Test void loadingPreservesIdentityAndDoesNotDirtyStorage() {
        var dirty = new AtomicInteger();
        var network = new HomeNetwork(UUID.randomUUID(), "Saved home", UUID.randomUUID(), Map.of(), Set.of(UUID.randomUUID()), Instant.parse("2026-01-01T00:00:00Z"));
        var manager = new HomeNetworkManager(List.of(network), dirty::incrementAndGet);
        assertEquals(network, manager.getNetwork(network.id()).orElseThrow());
        assertEquals(0, dirty.get());
        assertThrows(IllegalArgumentException.class, () -> new HomeNetworkManager(List.of(network, network), () -> { }));
        assertThrows(UnsupportedOperationException.class, () -> manager.getAll().clear());
        assertThrows(UnsupportedOperationException.class, () -> manager.getDevices(network.id()).clear());
        assertThrows(UnsupportedOperationException.class, () -> network.members().clear());
    }

    @Test void rejectsMalformedNetworksAndMissingMutations() {
        UUID owner = UUID.randomUUID();
        var manager = new HomeNetworkManager();
        assertThrows(IllegalArgumentException.class, () -> manager.createNetwork(" ", owner));
        assertThrows(IllegalArgumentException.class, () -> manager.createNetwork("a".repeat(129), owner));
        assertThrows(IllegalArgumentException.class, () -> new HomeNetwork(UUID.randomUUID(), "Home", owner, Map.of(owner, NetworkRole.VIEWER), Set.of(), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new HomeNetwork(UUID.randomUUID(), "Home", owner, Map.of(UUID.randomUUID(), NetworkRole.OWNER), Set.of(), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> manager.addDevice(UUID.randomUUID(), UUID.randomUUID()));
        assertTrue(manager.getNetwork(UUID.randomUUID()).isEmpty());
        assertTrue(manager.getAll().isEmpty());
    }
}
