package fr.lkdm.homecore.network;

import fr.lkdm.homecore.api.client.ClientDeviceCache;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homecore.internal.ServerRuntime;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Versioned direction-specific transport; world access occurs only on the game thread. */
public final class PayloadRegistration {
    private PayloadRegistration() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(HomeCorePayloads.NetworkWatchRequest.TYPE, HomeCorePayloads.NetworkWatchRequest.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) ServerRuntime.get(player.server).sync(player.server).watch(player, payload);
                }));
        registrar.playToClient(HomeCorePayloads.NetworkWatchResponse.TYPE, HomeCorePayloads.NetworkWatchResponse.STREAM_CODEC,
                PayloadRegistration::receive);
        registrar.playToServer(HomeCorePayloads.DeviceListRequest.TYPE, HomeCorePayloads.DeviceListRequest.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ServerRuntime.get(player.server).sync(player.server).request(player, payload);
                    }
                }));
        registrar.playToServer(HomeCorePayloads.ExecuteActionRequest.TYPE, HomeCorePayloads.ExecuteActionRequest.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ServerRuntime.get(player.server).sync(player.server).execute(player, payload);
                    }
                }));
        registrar.playToServer(HomeCorePayloads.Unsubscribe.TYPE, HomeCorePayloads.Unsubscribe.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ServerRuntime.get(player.server).sync(player.server).unsubscribe(player, payload);
                    }
                }));
        registrar.playToClient(HomeCorePayloads.DeviceListResponse.TYPE, HomeCorePayloads.DeviceListResponse.STREAM_CODEC,
                PayloadRegistration::receive);
        registrar.playToClient(HomeCorePayloads.DeviceSnapshot.TYPE, HomeCorePayloads.DeviceSnapshot.STREAM_CODEC,
                PayloadRegistration::receive);
        registrar.playToClient(HomeCorePayloads.MetricUpdate.TYPE, HomeCorePayloads.MetricUpdate.STREAM_CODEC,
                PayloadRegistration::receive);
        registrar.playToClient(HomeCorePayloads.ActionResultResponse.TYPE, HomeCorePayloads.ActionResultResponse.STREAM_CODEC,
                PayloadRegistration::receive);
        registrar.playToClient(HomeCorePayloads.HomeNetworkSnapshot.TYPE, HomeCorePayloads.HomeNetworkSnapshot.STREAM_CODEC,
                PayloadRegistration::receive);
        registrar.playToClient(HomeCorePayloads.DeviceEventNotification.TYPE, HomeCorePayloads.DeviceEventNotification.STREAM_CODEC,
                PayloadRegistration::receive);
    }

    private static void receive(CustomPacketPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDeviceCache.INSTANCE.accept(payload));
    }
}
