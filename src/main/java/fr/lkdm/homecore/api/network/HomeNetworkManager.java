package fr.lkdm.homecore.api.network;

import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Trusted server-side network storage with immutable snapshots. These methods do
 * not authenticate callers: remote requests must pass server permission validation.
 * Call on the owning server thread; synchronization protects storage but does not
 * make the persistence callback or Minecraft worlds safe for asynchronous access.
 */
public final class HomeNetworkManager {
    private final Map<UUID, HomeNetwork> networks = new LinkedHashMap<>();
    private final Runnable onChange;
    private final Map<net.minecraft.resources.ResourceLocation, java.util.function.BiPredicate<UUID, fr.lkdm.homecore.api.device.DashboardDevice>> reachability = new LinkedHashMap<>();

    /** Installs a trusted, server-thread transport constraint. All installed policies must allow
     * access. Policies are ephemeral and must be reinstalled at server startup. Exceptions deny access.
     * This does not replace membership or permission checks, and never mutates device status.
     * @param id unique integration identifier
     * @param policy network/device reachability predicate; must not mutate this manager
     */
    public synchronized void setReachabilityPolicy(net.minecraft.resources.ResourceLocation id,
            java.util.function.BiPredicate<UUID, fr.lkdm.homecore.api.device.DashboardDevice> policy) {
        reachability.put(Objects.requireNonNull(id), Objects.requireNonNull(policy));
    }

    /** Tests membership and transport constraints independently of device health.
     * @param network network identity
     * @param device registered device
     * @return false for missing membership, rejected constraints or a failed policy
     */
    public synchronized boolean isReachable(UUID network, fr.lkdm.homecore.api.device.DashboardDevice device) {
        HomeNetwork home = networks.get(network);
        if (home == null || device == null || !home.devices().contains(device.id())) return false;
        try {
            for (var policy : reachability.values()) if (!policy.test(network, device)) return false;
            return true;
        } catch (RuntimeException exception) { return false; }
    }

    /** Creates empty, in-memory storage. */
    public HomeNetworkManager() { this(() -> { }); }

    /**
     * Creates empty storage with a persistence notification.
     * @param onChange callback invoked once after every actual mutation; must not throw
     */
    public HomeNetworkManager(Runnable onChange) { this(List.of(), onChange); }

    /**
     * Loads validated snapshots without invoking the dirty callback.
     * @param initial persisted networks
     * @param onChange callback invoked after mutations; must not throw or mutate this manager
     * @throws IllegalArgumentException for duplicate network IDs
     */
    public HomeNetworkManager(Collection<HomeNetwork> initial, Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        if (Objects.requireNonNull(initial, "initial").size() > HomeNetwork.MAX_ENTRIES) throw new IllegalArgumentException("Too many networks");
        for (HomeNetwork network : Objects.requireNonNull(initial, "initial")) {
            Objects.requireNonNull(network, "network");
            if (networks.putIfAbsent(network.id(), network) != null) throw new IllegalArgumentException("Duplicate network ID: " + network.id());
        }
    }

    /**
     * Creates and stores a network with a generated identity and current timestamp.
     * @param name display name
     * @param owner owning player
     * @return created snapshot
     */
    public synchronized HomeNetwork createNetwork(String name, UUID owner) {
        if (networks.size() >= HomeNetwork.MAX_ENTRIES) throw new IllegalStateException("Network capacity reached");
        UUID id;
        do { id = UUID.randomUUID(); } while (networks.containsKey(id));
        HomeNetwork network = new HomeNetwork(id, name, owner, Map.of(), Set.of(), Instant.now());
        networks.put(id, network);
        onChange.run();
        return network;
    }

    /**
     * Deletes a network; device objects are not destroyed.
     * @param id network identity
     * @return whether a network existed
     */
    public synchronized boolean deleteNetwork(UUID id) {
        if (networks.remove(Objects.requireNonNull(id, "id")) == null) return false;
        onChange.run(); return true;
    }

    /**
     * Reads an immutable snapshot.
     * @param id network identity
     * @return network or empty
     */
    public synchronized Optional<HomeNetwork> getNetwork(UUID id) { return Optional.ofNullable(networks.get(Objects.requireNonNull(id, "id"))); }

    /** Renames a network without changing its identity, members or devices.
     * Trusted server-thread API: remote callers must validate MANAGE_NETWORK first.
     * @param id network identity
     * @param name nonblank display name of at most 128 characters
     * @return updated immutable network snapshot
     */
    public synchronized HomeNetwork renameNetwork(UUID id, String name) {
        HomeNetwork current = require(id);
        var renamed = new HomeNetwork(current.id(), name, current.owner(), current.members(), current.devices(), current.createdAt());
        if (!renamed.equals(current)) { networks.put(id, renamed); onChange.run(); }
        return renamed;
    }

    /**
     * Lists networks containing a player, including owned networks.
     * @param player player identity
     * @return immutable snapshots in creation/load order
     */
    public synchronized List<HomeNetwork> getNetworksForPlayer(UUID player) {
        Objects.requireNonNull(player, "player");
        return networks.values().stream().filter(network -> network.members().containsKey(player)).toList();
    }

    /** Returns every immutable snapshot in creation/load order.
     * @return network snapshots
     */
    public synchronized List<HomeNetwork> getAll() { return List.copyOf(networks.values()); }

    /**
     * Attaches a persistent device ID; the device need not be currently loaded.
     * @param network network identity
     * @param device device identity
     * @return whether membership changed
     * @throws IllegalArgumentException if the network does not exist
     */
    public synchronized boolean addDevice(UUID network, UUID device) {
        HomeNetwork current = require(network);
        var devices = new LinkedHashSet<>(current.devices());
        if (!devices.add(Objects.requireNonNull(device, "device"))) return false;
        replace(current, current.members(), devices); return true;
    }

    /**
     * Detaches a device ID without deleting the device.
     * @param network network identity
     * @param device device identity
     * @return whether membership changed
     * @throws IllegalArgumentException if the network does not exist
     */
    public synchronized boolean removeDevice(UUID network, UUID device) {
        HomeNetwork current = require(network);
        var devices = new LinkedHashSet<>(current.devices());
        if (!devices.remove(Objects.requireNonNull(device, "device"))) return false;
        replace(current, current.members(), devices); return true;
    }

    /**
     * Returns persistent device memberships.
     * @param network network identity
     * @return immutable device IDs
     * @throws IllegalArgumentException if the network does not exist
     */
    public synchronized Set<UUID> getDevices(UUID network) { return require(network).devices(); }

    /**
     * Resolves availability, including installed reachability constraints.
     * Only an explicitly ONLINE, registered member is connected. Missing,
     * invalid or otherwise unavailable members are offline; nonmembers are unreachable.
     *
     * @param network network identity
     * @param device device identity
     * @param registry server-owned device registry
     * @return current logical connection state
     * @throws IllegalArgumentException if the network does not exist
     */
    public synchronized ConnectionState connectionState(UUID network, UUID device, DeviceRegistry registry) {
        Objects.requireNonNull(device, "device"); Objects.requireNonNull(registry, "registry");
        if (!require(network).devices().contains(device)) return ConnectionState.UNREACHABLE;
        var candidate = registry.get(device);
        if (candidate.isPresent() && !isReachable(network, candidate.orElseThrow())) return ConnectionState.UNREACHABLE;
        return candidate
                .filter(target -> target.status().state() == DeviceStatus.State.ONLINE)
                .map(target -> ConnectionState.CONNECTED).orElse(ConnectionState.OFFLINE);
    }

    /**
     * Adds or changes a member role. Ownership cannot be transferred by this method.
     * @param network network identity
     * @param member player identity
     * @param role assigned role
     * @return whether membership changed
     * @throws IllegalArgumentException for missing networks or an owner invariant violation
     */
    public synchronized boolean setMember(UUID network, UUID member, NetworkRole role) {
        HomeNetwork current = require(network);
        Objects.requireNonNull(member, "member"); Objects.requireNonNull(role, "role");
        if ((current.owner().equals(member)) != (role == NetworkRole.OWNER)) throw new IllegalArgumentException("Cannot change network ownership");
        if (current.members().get(member) == role) return false;
        var members = new LinkedHashMap<>(current.members());
        members.put(member, role);
        replace(current, members, current.devices()); return true;
    }

    /**
     * Removes a member while preserving the owner.
     * @param network network identity
     * @param member player identity
     * @return whether membership changed
     * @throws IllegalArgumentException for missing networks or removal of the owner
     */
    public synchronized boolean removeMember(UUID network, UUID member) {
        HomeNetwork current = require(network);
        Objects.requireNonNull(member, "member");
        if (current.owner().equals(member)) throw new IllegalArgumentException("Cannot remove network owner");
        var members = new LinkedHashMap<>(current.members());
        if (members.remove(member) == null) return false;
        replace(current, members, current.devices()); return true;
    }

    private HomeNetwork require(UUID id) {
        HomeNetwork network = networks.get(Objects.requireNonNull(id, "network"));
        if (network == null) throw new IllegalArgumentException("Unknown network: " + id);
        return network;
    }

    private void replace(HomeNetwork current, Map<UUID, NetworkRole> members, Set<UUID> devices) {
        networks.put(current.id(), new HomeNetwork(current.id(), current.name(), current.owner(), members, devices, current.createdAt()));
        onChange.run();
    }
}
