package fr.lkdm.homecore.api.network;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable logical home network snapshot. Device IDs persist independently of
 * loaded devices. The owner always appears in the members map with the OWNER role.
 *
 * @param id persistent network identity
 * @param name nonblank display name, at most 128 characters
 * @param owner unique owning player
 * @param members player roles, defensively copied
 * @param devices persistent device IDs, defensively copied
 * @param createdAt creation time
 */
public record HomeNetwork(UUID id, String name, UUID owner, Map<UUID, NetworkRole> members,
                          Set<UUID> devices, Instant createdAt) {
    /** Maximum persisted entries in a network collection. */
    public static final int MAX_ENTRIES = 100_000;
    /** Validates metadata and normalizes the owner membership invariant. */
    public HomeNetwork {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank() || name.length() > 128) throw new IllegalArgumentException("Network name must contain 1 to 128 characters");
        var normalized = new LinkedHashMap<>(Objects.requireNonNull(members, "members"));
        for (var member : normalized.entrySet()) {
            Objects.requireNonNull(member.getKey(), "member");
            Objects.requireNonNull(member.getValue(), "role");
            if (member.getValue() == NetworkRole.OWNER && !member.getKey().equals(owner)) {
                throw new IllegalArgumentException("Only the owner can have OWNER role");
            }
            if (member.getKey().equals(owner) && member.getValue() != NetworkRole.OWNER) {
                throw new IllegalArgumentException("Owner cannot have a different role");
            }
        }
        normalized.put(owner, NetworkRole.OWNER);
        if (normalized.size() > MAX_ENTRIES || Objects.requireNonNull(devices, "devices").size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Network collection exceeds " + MAX_ENTRIES + " entries");
        }
        members = Map.copyOf(normalized);
        devices = Set.copyOf(devices);
    }
}
