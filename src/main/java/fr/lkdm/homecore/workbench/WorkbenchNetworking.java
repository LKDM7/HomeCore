package fr.lkdm.homecore.workbench;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Menu-scoped requests; no client message can grant an item or complete a batch. */
public final class WorkbenchNetworking {
    public static final UUID NO_SESSION = new UUID(0, 0);
    public static final ResourceLocation NO_RECIPE = ResourceLocation.fromNamespaceAndPath("homecore", "empty");
    private WorkbenchNetworking() { }

    public record Action(int containerId, int action, ResourceLocation recipe, int quantity,
                         UUID session, int source, int target) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("homecore", "electronics_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.action);
            buffer.writeUtf(value.recipe.toString(), 128); buffer.writeVarInt(value.quantity);
            buffer.writeUUID(value.session); buffer.writeVarInt(value.source); buffer.writeVarInt(value.target);
        }, buffer -> new Action(buffer.readVarInt(), buffer.readVarInt(), ResourceLocation.parse(buffer.readUtf(128)),
                buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt()));
        @Override public Type<Action> type() { return TYPE; }
    }

    public record State(int containerId, ResourceLocation recipe, int quantity, int phase, int placedMask,
                        int progress, int duration, int maxCraftable, UUID session, boolean busy) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("homecore", "electronics_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeVarInt(value.containerId); buffer.writeUtf(value.recipe.toString(), 128);
            buffer.writeVarInt(value.quantity); buffer.writeVarInt(value.phase); buffer.writeVarInt(value.placedMask);
            buffer.writeVarInt(value.progress); buffer.writeVarInt(value.duration); buffer.writeVarInt(value.maxCraftable);
            buffer.writeUUID(value.session); buffer.writeBoolean(value.busy);
        }, buffer -> new State(buffer.readVarInt(), ResourceLocation.parse(buffer.readUtf(128)),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean()));
        @Override public Type<State> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Action.TYPE, Action.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof ElectronicsMenu menu
                    && menu.containerId == payload.containerId() && menu.stillValid(player)) menu.handle(player, payload);
        }));
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ElectronicsMenu menu && menu.containerId == payload.containerId()) {
                menu.receive(payload);
            }
        }));
    }
}
