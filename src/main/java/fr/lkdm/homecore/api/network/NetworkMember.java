package fr.lkdm.homecore.api.network;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional contract for a {@link fr.lkdm.homecore.api.device.DashboardDevice} whose block records its own
 * HomeNetwork. Implementing it lets trusted server code, such as a dashboard, list the device's binding and
 * move it between networks while the block stays consistent. Call every method on the server thread.
 *
 * <p>Use {@link fr.lkdm.homecore.api.DashboardAPI#bindDevice} for player requests: it combines
 * {@link #canConfigure} with {@link fr.lkdm.homecore.api.security.Permission#MANAGE_NETWORK} on both networks.</p>
 */
public interface NetworkMember {
    /** Outcome of a membership change. */
    enum BindResult {
        /** The device joined the destination network. */
        BOUND,
        /** The device left its network and is now unbound. */
        UNBOUND,
        /** The device already had the requested binding. */
        UNCHANGED,
        /** A machine or network permission was missing. */
        DENIED,
        /** The destination network does not exist. */
        UNKNOWN_NETWORK,
        /** The device does not record its network. */
        NOT_SUPPORTED
    }

    /** Network recorded by the device's block.
     * @return network identity, empty when the device is unbound
     */
    Optional<UUID> homeNetwork();

    /** Owner of the physical machine.
     * @return owner identity, empty when unknown
     */
    Optional<UUID> owner();

    /** Integration-specific right to reconfigure the machine itself (owner, operator, ...).
     * Network permissions are checked separately by HomeCore.
     * @param player authenticated server player
     * @return whether this player may change the machine's network
     */
    boolean canConfigure(ServerPlayer player);

    /** Records a binding already applied to HomeCore's network membership.
     * @param network new network, empty when detached
     */
    void homeNetworkChanged(Optional<HomeNetwork> network);

    /**
     * Moves a device between networks once the caller has decided the permissions. Nothing is changed unless
     * every check passes; the device joins the destination before leaving its previous network.
     * @param networks server network manager
     * @param device device identity registered in the networks
     * @param member binding recorded by the device's block
     * @param target destination network, empty to detach
     * @param canConfigureDevice caller's right on the machine itself
     * @param canManageNetwork caller's MANAGE_NETWORK right on a given network
     * @return outcome; {@code BOUND} or {@code UNBOUND} when membership changed
     */
    static BindResult move(HomeNetworkManager networks, UUID device, NetworkMember member, Optional<UUID> target,
            boolean canConfigureDevice, Predicate<UUID> canManageNetwork) {
        Objects.requireNonNull(networks, "networks");
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(canManageNetwork, "canManageNetwork");
        if (!canConfigureDevice) return BindResult.DENIED;
        Optional<UUID> current = member.homeNetwork();
        Optional<HomeNetwork> destination = Optional.empty();
        if (target.isPresent()) {
            destination = networks.getNetwork(target.get());
            if (destination.isEmpty()) return BindResult.UNKNOWN_NETWORK;
        }
        if (current.equals(target)) return BindResult.UNCHANGED;
        if (target.isPresent() && !canManageNetwork.test(target.get())) return BindResult.DENIED;
        // A deleted previous network needs no permission: there is nothing left to leave.
        boolean leave = current.isPresent() && networks.getNetwork(current.get()).isPresent();
        if (leave && !canManageNetwork.test(current.get())) return BindResult.DENIED;
        if (destination.isPresent()) networks.addDevice(destination.get().id(), device);
        if (leave) networks.removeDevice(current.get(), device);
        member.homeNetworkChanged(destination.flatMap(network -> networks.getNetwork(network.id())));
        return destination.isPresent() ? BindResult.BOUND : BindResult.UNBOUND;
    }
}
