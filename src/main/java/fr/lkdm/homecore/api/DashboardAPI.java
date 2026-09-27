package fr.lkdm.homecore.api;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.capability.CapabilityRegistry;
import fr.lkdm.homecore.api.event.DeviceEventBus;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homecore.api.registry.DeviceProvider;
import fr.lkdm.homecore.api.registry.DeviceProviderRegistry;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homecore.internal.HomeNetworkSavedData;
import fr.lkdm.homecore.internal.ServerRuntime;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Public entry point for mods integrating with HomeCore.
 *
 * <p>HomeCore owns the shared contracts; consumer mods own their integrations.
 * Live state is owned by a server and accessed on its server thread.</p>
 */
public final class DashboardAPI {
    private static final DeviceProviderRegistry PROVIDERS = new DeviceProviderRegistry();
    private static final CapabilityRegistry CAPABILITIES = new CapabilityRegistry();
    /** Semantic version of the public API, independent of the mod artifact version. */
    public static final String API_VERSION = "1.5.0";

    private DashboardAPI() {
    }

    /** Returns the process-wide adapter definitions; live devices are server-scoped.
     * @return provider definitions
     */
    public static DeviceProviderRegistry providers() { return PROVIDERS; }

    /** Returns the process-wide registry of capability contracts.
     * @return capability definitions
     */
    public static CapabilityRegistry capabilities() { return CAPABILITIES; }

    /** Returns the event bus for this server; publish Minecraft-related events on its thread.
     * @param server owning server
     * @return event bus, closed on server stop
     */
    public static DeviceEventBus events(MinecraftServer server) {
        return ServerRuntime.get(server).events();
    }

    /** Returns persistent home networks, shared across dimensions.
     * This trusted management API must be called on the server thread. Consumers
     * accepting player input must validate permissions before mutating networks.
     * @param server owning server
     * @return network manager backed by the Overworld's saved data
     */
    public static HomeNetworkManager networks(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("HomeCore requires the server thread");
        return HomeNetworkSavedData.get(server).manager();
    }

    /** Executes an action using the authenticated server player's identity.
     * Call on the server thread; every invocation checks current membership,
     * permissions, availability, validation and the shared per-player rate limit.
     * @param player authenticated player, never an identity from a packet
     * @param network network identity
     * @param device device identity
     * @param action action identifier
     * @param value untrusted action parameter
     * @return structured action outcome
     */
    public static ActionResult executeAction(ServerPlayer player,
            UUID network, UUID device, ResourceLocation action, Object value) {
        var runtime = ServerRuntime.get(player.server);
        return runtime.actions(player.server).execute(player.getUUID(), network, device, action, value);
    }

    /** Tests current network permissions for a connected player.
     * @param player authenticated server player
     * @param network network identity
     * @param permission required permission
     * @return false when the network does not exist or access is denied
     */
    public static boolean hasPermission(ServerPlayer player, UUID network,
            Permission permission) {
        var runtime = ServerRuntime.get(player.server);
        return networks(player.server).getNetwork(network)
                .map(home -> runtime.permissions().hasPermission(home, player.getUUID(), permission)).orElse(false);
    }

    /** Moves a device between networks on behalf of a player. Requires the integration's own right on the
     * machine ({@link NetworkMember#canConfigure}) and MANAGE_NETWORK on the destination and on the
     * previous network. Call on the server thread.
     * @param player authenticated player, never an identity from a packet
     * @param device live device
     * @param target destination network, empty to detach
     * @return outcome; {@code NOT_SUPPORTED} when the device does not record its network
     */
    public static NetworkMember.BindResult bindDevice(ServerPlayer player, DashboardDevice device, Optional<UUID> target) {
        if (!(device instanceof NetworkMember member)) return NetworkMember.BindResult.NOT_SUPPORTED;
        return NetworkMember.move(networks(player.server), device.id(), member, target, member.canConfigure(player),
                network -> hasPermission(player, network, Permission.MANAGE_NETWORK));
    }

    /** Returns live devices for this server; call only on its server thread.
     * @param server owning server
     * @return server-scoped registry, cleared when the server stops
     */
    public static DeviceRegistry devices(MinecraftServer server) {
        return ServerRuntime.get(server).devices();
    }

    /** Registers a block entity adapter during mod setup.
     * @param type block entity type
     * @param provider logical-device factory
     * @param <T> block entity type
     */
    public static <T extends BlockEntity> void registerDeviceProvider(
            BlockEntityType<T> type,
            DeviceProvider<? super T> provider) {
        PROVIDERS.register(type, provider);
    }
}
