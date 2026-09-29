package fr.lkdm.homecore.api.security;

import fr.lkdm.homecore.api.action.ActionContext;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.action.StandardActions;
import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/** Trusted server-side action gateway. Call only on the owning server thread and
 * derive the player UUID from the authenticated server connection, never a packet
 * field. All attempts share the player's limiter across devices and networks.
 * The gateway re-reads current membership and does not retain authorization grants.
 */
public final class ActionExecutor {
    private final DeviceRegistry devices;
    private final HomeNetworkManager networks;
    private final PermissionValidator permissions;
    private final RateLimiter limiter;

    /** Creates a gateway over server-owned state.
     * @param devices live device registry
     * @param networks network memberships
     * @param permissions role policy
     * @param limiter player-scoped request limiter shared by every control request
     */
    public ActionExecutor(DeviceRegistry devices, HomeNetworkManager networks,
                          PermissionValidator permissions, RateLimiter limiter) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.networks = Objects.requireNonNull(networks, "networks");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    /** Validates identity, membership, permissions, availability and parameter before invoking a handler.
     * Attempts consume rate allowance before device or network lookups, including denied attempts.
     * @param player authenticated player identity
     * @param network network identity
     * @param device device identity
     * @param action action identifier
     * @param parameter untrusted parameter
     * @return result without exposing implementation exceptions to the caller
     */
    public ActionResult execute(UUID player, UUID network, UUID device, ResourceLocation action, Object parameter) {
        return executeResolved(player, network, device, action, ignored -> parameter);
    }

    /** Resolves a transport parameter against the authorized server-side action definition.
     * The resolver is invoked only after identity, permissions and availability checks.
     * It must be a pure conversion and must not execute commands or mutate server state.
     * @param player authenticated player identity
     * @param network network identity
     * @param device device identity
     * @param action action identifier
     * @param resolver pure parameter converter, never invoked for unauthorized requests
     * @return secured execution result
     */
    public ActionResult executeResolved(UUID player, UUID network, UUID device, ResourceLocation action,
                                       Function<DeviceAction<?>, Object> resolver) {
        Objects.requireNonNull(resolver, "resolver");
        if (player == null) return result(ActionResult.Code.DENIED);
        if (!limiter.tryAcquire(player)) return result(ActionResult.Code.RATE_LIMITED);
        if (device == null) return result(ActionResult.Code.FAILED);
        if (network == null) return result(ActionResult.Code.DENIED);
        if (action == null) return result(ActionResult.Code.INVALID_PARAMETER);
        try {
            var target = devices.get(device).orElse(null);
            if (target == null) return result(ActionResult.Code.FAILED);
            var home = networks.getNetwork(network).orElse(null);
            if (home == null || !home.devices().contains(device)
                    || !permissions.hasPermission(home, player, Permission.CONTROL)) return result(ActionResult.Code.DENIED);
            DeviceAction<?> command = DeviceSchema.from(target).actions().stream()
                    .filter(candidate -> candidate.id().equals(action)).findFirst().orElse(null);
            if (command == null) return result(ActionResult.Code.INVALID_PARAMETER);
            Permission required = Permission.fromId(command.requiredPermission()).orElse(null);
            if (!permissions.hasPermission(home, player, required)) return result(ActionResult.Code.DENIED);
            // Switching on and renaming must stay possible while a machine is off or reporting a problem.
            DeviceStatus.State state = target.status().state();
            boolean available = StandardActions.isStandard(command.id()) ? state != DeviceStatus.State.OFFLINE
                    : state == DeviceStatus.State.ONLINE;
            if (!networks.isReachable(network, target) || !available) return result(ActionResult.Code.DEVICE_OFFLINE);
            Object parameter;
            try {
                parameter = resolver.apply(command);
            } catch (RuntimeException exception) {
                return result(ActionResult.Code.INVALID_PARAMETER);
            }
            return command.execute(new ActionContext(player, network, device), parameter);
        } catch (RuntimeException exception) {
            return result(ActionResult.Code.FAILED);
        }
    }

    private static ActionResult result(ActionResult.Code code) { return ActionResult.of(code); }
}
