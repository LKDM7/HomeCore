package fr.lkdm.homecore.api.network;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkMemberTest {
    /** Records the binding like a machine block would. */
    private static final class Machine implements NetworkMember {
        final AtomicReference<Optional<UUID>> network = new AtomicReference<>(Optional.empty());
        int notifications;
        @Override public Optional<UUID> homeNetwork() { return network.get(); }
        @Override public Optional<UUID> owner() { return Optional.empty(); }
        @Override public boolean canConfigure(ServerPlayer player) { return true; }
        @Override public void homeNetworkChanged(Optional<HomeNetwork> value) {
            notifications++;
            network.set(value.map(HomeNetwork::id));
        }
    }

    @Test void bindsMovesAndDetachesKeepingBlockAndMembershipInStep() {
        var manager = new HomeNetworkManager();
        UUID owner = UUID.randomUUID(), device = UUID.randomUUID();
        var first = manager.createNetwork("Base", owner);
        var second = manager.createNetwork("Atelier", owner);
        var machine = new Machine();

        assertEquals(NetworkMember.BindResult.BOUND, NetworkMember.move(manager, device, machine, Optional.of(first.id()), true, id -> true));
        assertEquals(Optional.of(first.id()), machine.homeNetwork());
        assertTrue(manager.getNetwork(first.id()).orElseThrow().devices().contains(device));

        assertEquals(NetworkMember.BindResult.UNCHANGED, NetworkMember.move(manager, device, machine, Optional.of(first.id()), true, id -> true));
        assertEquals(1, machine.notifications);

        assertEquals(NetworkMember.BindResult.BOUND, NetworkMember.move(manager, device, machine, Optional.of(second.id()), true, id -> true));
        assertFalse(manager.getNetwork(first.id()).orElseThrow().devices().contains(device), "Moving must leave the old network");
        assertEquals(Set.of(device), manager.getNetwork(second.id()).orElseThrow().devices());
        assertEquals(Optional.of(second.id()), machine.homeNetwork());

        assertEquals(NetworkMember.BindResult.UNBOUND, NetworkMember.move(manager, device, machine, Optional.empty(), true, id -> true));
        assertTrue(manager.getNetwork(second.id()).orElseThrow().devices().isEmpty());
        assertEquals(Optional.empty(), machine.homeNetwork());
    }

    @Test void deniedOrUnknownRequestsChangeNothing() {
        var manager = new HomeNetworkManager();
        UUID owner = UUID.randomUUID(), device = UUID.randomUUID();
        var mine = manager.createNetwork("Mine", owner);
        var theirs = manager.createNetwork("Theirs", UUID.randomUUID());
        var machine = new Machine();
        NetworkMember.move(manager, device, machine, Optional.of(theirs.id()), true, id -> true);

        assertEquals(NetworkMember.BindResult.DENIED,
                NetworkMember.move(manager, device, machine, Optional.of(mine.id()), false, id -> true), "Machine rights are required");
        assertEquals(NetworkMember.BindResult.DENIED,
                NetworkMember.move(manager, device, machine, Optional.of(mine.id()), true, mine.id()::equals),
                "Leaving a network requires MANAGE_NETWORK on it");
        assertEquals(NetworkMember.BindResult.DENIED,
                NetworkMember.move(manager, device, machine, Optional.of(mine.id()), true, theirs.id()::equals),
                "Joining a network requires MANAGE_NETWORK on it");
        assertEquals(NetworkMember.BindResult.UNKNOWN_NETWORK,
                NetworkMember.move(manager, device, machine, Optional.of(UUID.randomUUID()), true, id -> true));
        assertEquals(Optional.of(theirs.id()), machine.homeNetwork());
        assertEquals(Set.of(device), manager.getNetwork(theirs.id()).orElseThrow().devices());
        assertTrue(manager.getNetwork(mine.id()).orElseThrow().devices().isEmpty());
    }

    @Test void deletedPreviousNetworkNeedsNoPermission() {
        var manager = new HomeNetworkManager();
        UUID owner = UUID.randomUUID(), device = UUID.randomUUID();
        var gone = manager.createNetwork("Gone", owner);
        var target = manager.createNetwork("Target", owner);
        var machine = new Machine();
        NetworkMember.move(manager, device, machine, Optional.of(gone.id()), true, id -> true);
        manager.deleteNetwork(gone.id());
        assertEquals(NetworkMember.BindResult.BOUND,
                NetworkMember.move(manager, device, machine, Optional.of(target.id()), true, target.id()::equals));
        assertEquals(Optional.of(target.id()), machine.homeNetwork());
    }
}
