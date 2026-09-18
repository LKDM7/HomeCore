package fr.lkdm.homecore.api.security;

import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.NetworkRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable role policy. The network's actual owner always has every permission.
 * Membership is read from the supplied current snapshot, never cached by player.
 */
public final class PermissionValidator {
    private final Map<NetworkRole, Set<Permission>> grants;

    /** Creates the default policy: viewer reads; member reads, controls and automates;
     * administrator and owner have all permissions.
     */
    public PermissionValidator() {
        this(Map.of(NetworkRole.VIEWER, Set.of(Permission.VIEW),
                NetworkRole.MEMBER, Set.of(Permission.VIEW, Permission.CONTROL, Permission.AUTOMATE),
                NetworkRole.ADMIN, EnumSet.allOf(Permission.class)));
    }

    /** Creates a custom policy. Unspecified non-owner roles have no permissions.
     * Owner permissions cannot be restricted, even if an OWNER entry is supplied.
     * @param grants complete role grants, copied defensively
     */
    public PermissionValidator(Map<NetworkRole, Set<Permission>> grants) {
        Objects.requireNonNull(grants, "grants");
        Map<NetworkRole, Set<Permission>> copy = new EnumMap<>(NetworkRole.class);
        grants.forEach((role, permissions) -> copy.put(Objects.requireNonNull(role, "role"), Set.copyOf(permissions)));
        this.grants = Map.copyOf(copy);
    }

    /** Checks membership and the applicable role grant; missing inputs are denied.
     * @param network current network snapshot
     * @param player authenticated player identity
     * @param permission required permission
     * @return whether access is allowed
     */
    public boolean hasPermission(HomeNetwork network, UUID player, Permission permission) {
        if (network == null || player == null || permission == null) return false;
        if (network.owner().equals(player)) return true;
        NetworkRole role = network.members().get(player);
        return role != null && grants.getOrDefault(role, Set.of()).contains(permission);
    }
}
