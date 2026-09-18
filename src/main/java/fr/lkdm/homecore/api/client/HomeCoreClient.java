package fr.lkdm.homecore.api.client;

import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homecore.api.transport.WireValue;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only entry point for dashboard consumers. Invoke on the client thread.
 * Payloads are requests; all authorization and mutations happen on the server.
 */
@EventBusSubscriber(modid = "homecore", value = Dist.CLIENT)
public final class HomeCoreClient {
    private HomeCoreClient() { }
    /** Requests a directory page or device page and its initial snapshots.
     * @param network empty for the network directory, otherwise target network
     * @param offset zero-based page offset
     * @return correlation identifier
     */
    public static UUID requestDevices(Optional<UUID> network, int offset) {
        requireConnection();
        UUID request = UUID.randomUUID();
        PacketDistributor.sendToServer(new HomeCorePayloads.DeviceListRequest(request, network, offset));
        return request;
    }
    /** Requests an action using only the connected player's authenticated identity.
     * @param network containing network
     * @param device target device
     * @param action target action
     * @param parameter typed value supported by the wire format
     * @return correlation identifier
     */
    public static UUID executeAction(UUID network, UUID device, ResourceLocation action, Object parameter) {
        requireConnection();
        UUID request = UUID.randomUUID();
        PacketDistributor.sendToServer(new HomeCorePayloads.ExecuteActionRequest(request, network, device, action, WireValue.from(parameter)));
        return request;
    }
    /** Stops updates and releases cached network data.
     * @param network network identity
     */
    public static void unsubscribe(UUID network) {
        requireConnection();
        PacketDistributor.sendToServer(new HomeCorePayloads.Unsubscribe(network));
        ClientDeviceCache.INSTANCE.forgetNetwork(network);
    }
    private static void requireConnection() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) throw new IllegalStateException("HomeCore client calls require the client thread");
        if (minecraft.getConnection() == null || minecraft.player == null) throw new IllegalStateException("Not connected to a server");
    }
    /** Releases session data and listeners on logout.
     * @param event client logout event
     */
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientDeviceCache.INSTANCE.clear(); }
}
