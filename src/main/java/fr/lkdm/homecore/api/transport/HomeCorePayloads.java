package fr.lkdm.homecore.api.transport;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.event.DeviceEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Version 1 transport contracts. Decoding only produces data; handlers must authenticate requests. */
public final class HomeCorePayloads {
    /** Maximum device/network IDs per response page. */
    public static final int PAGE_SIZE = 16;
    /** Maximum encoded NBT bytes and accounted allocation for each snapshot. */
    public static final int MAX_SNAPSHOT_BYTES = 65_536;
    private HomeCorePayloads() { }

    /** Requests a page of network IDs when networkId is empty, otherwise a device page.
     * @param requestId correlation identity
     * @param networkId requested network, or empty to list authorized networks
     * @param offset nonnegative page offset
     */
    public record DeviceListRequest(UUID requestId, Optional<UUID> networkId, int offset) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<DeviceListRequest> TYPE = typeId("device_list_request");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, DeviceListRequest> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.requestId); optional(b, p.networkId); b.writeVarInt(p.offset); },
                b -> new DeviceListRequest(b.readUUID(), optional(b), b.readVarInt()));
        /** Validates the page request. */ public DeviceListRequest { Objects.requireNonNull(requestId); Objects.requireNonNull(networkId); if (offset < 0) throw new IllegalArgumentException("Negative page offset"); }
        @Override public Type<DeviceListRequest> type() { return TYPE; }
    }

    /** Authorized page response; nextOffset is -1 when no further page exists.
     * @param requestId correlation identity
     * @param networkId requested network or empty for network listing
     * @param ids immutable page of identifiers
     * @param nextOffset continuation offset or -1
     * @param result outcome
     */
    public record DeviceListResponse(UUID requestId, Optional<UUID> networkId, List<UUID> ids, int nextOffset, ActionResult.Code result) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<DeviceListResponse> TYPE = typeId("device_list_response");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, DeviceListResponse> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.requestId); optional(b, p.networkId); b.writeVarInt(p.ids.size()); p.ids.forEach(b::writeUUID); b.writeVarInt(p.nextOffset); b.writeByte(p.result.ordinal()); },
                b -> { UUID request = b.readUUID(); Optional<UUID> network = optional(b); int count = count(b, PAGE_SIZE); List<UUID> ids = new ArrayList<>(count); for (int i = 0; i < count; i++) ids.add(b.readUUID()); return new DeviceListResponse(request, network, ids, b.readVarInt(), resultCode(b)); });
        /** Copies and validates the page. */ public DeviceListResponse { Objects.requireNonNull(requestId); Objects.requireNonNull(networkId); ids = List.copyOf(ids); Objects.requireNonNull(result); if (ids.size() > PAGE_SIZE || nextOffset < -1) throw new IllegalArgumentException("Invalid response page"); }
        @Override public Type<DeviceListResponse> type() { return TYPE; }
    }

    /** Initial device schema and values; schema fields are described by the SDK.
     * @param networkId owning logical network
     * @param deviceId device identity
     * @param data bounded snapshot; copied on construction and access
     */
    public record DeviceSnapshot(UUID networkId, UUID deviceId, CompoundTag data) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<DeviceSnapshot> TYPE = typeId("device_snapshot");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, DeviceSnapshot> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.networkId); b.writeUUID(p.deviceId); tag(b, p.data); },
                b -> new DeviceSnapshot(b.readUUID(), b.readUUID(), tag(b)));
        /** Copies the mutable snapshot. */ public DeviceSnapshot { Objects.requireNonNull(networkId); Objects.requireNonNull(deviceId); data = Objects.requireNonNull(data).copy(); }
        /** Returns a defensive copy of the snapshot compound.
         * @return independent snapshot data
         */
        @Override public CompoundTag data() { return data.copy(); }
        @Override public Type<DeviceSnapshot> type() { return TYPE; }
    }

    /** Single changed metric, preserving its exact runtime wire representation.
     * @param networkId logical network
     * @param deviceId source device
     * @param metricId metric identity
     * @param revision nonnegative monotonic metric revision
     * @param value updated value
     */
    public record MetricUpdate(UUID networkId, UUID deviceId, ResourceLocation metricId, long revision, WireValue value) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<MetricUpdate> TYPE = typeId("metric_update");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, MetricUpdate> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.networkId); b.writeUUID(p.deviceId); id(b, p.metricId); b.writeLong(p.revision); WireValue.STREAM_CODEC.encode(b, p.value); },
                b -> new MetricUpdate(b.readUUID(), b.readUUID(), id(b), b.readLong(), WireValue.STREAM_CODEC.decode(b)));
        /** Validates the delta. */ public MetricUpdate { Objects.requireNonNull(networkId); Objects.requireNonNull(deviceId); checkedId(metricId); Objects.requireNonNull(value); if (revision < 0) throw new IllegalArgumentException("Negative metric revision"); }
        @Override public Type<MetricUpdate> type() { return TYPE; }
    }

    /** Untrusted request that must pass the server action executor.
     * @param requestId correlation identity
     * @param networkId logical network
     * @param deviceId target device
     * @param actionId target action
     * @param parameter bounded typed parameter
     */
    public record ExecuteActionRequest(UUID requestId, UUID networkId, UUID deviceId, ResourceLocation actionId, WireValue parameter) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<ExecuteActionRequest> TYPE = typeId("execute_action_request");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, ExecuteActionRequest> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.requestId); b.writeUUID(p.networkId); b.writeUUID(p.deviceId); id(b, p.actionId); WireValue.STREAM_CODEC.encode(b, p.parameter); },
                b -> new ExecuteActionRequest(b.readUUID(), b.readUUID(), b.readUUID(), id(b), WireValue.STREAM_CODEC.decode(b)));
        /** Validates request structure, without granting permission. */ public ExecuteActionRequest { Objects.requireNonNull(requestId); Objects.requireNonNull(networkId); Objects.requireNonNull(deviceId); checkedId(actionId); Objects.requireNonNull(parameter); }
        @Override public Type<ExecuteActionRequest> type() { return TYPE; }
    }

    /** Server result with an optional bounded plain-text explanation.
     * @param requestId correlation identity
     * @param result server outcome
     */
    public record ActionResultResponse(UUID requestId, ActionResult result) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<ActionResultResponse> TYPE = typeId("action_result_response");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, ActionResultResponse> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.requestId); b.writeByte(p.result.code().ordinal()); b.writeBoolean(p.result.message().isPresent()); p.result.message().ifPresent(message -> b.writeUtf(message.getString(), 4096)); },
                b -> { UUID request = b.readUUID(); ActionResult.Code code = resultCode(b); return new ActionResultResponse(request, bool(b) ? ActionResult.of(code, Component.literal(b.readUtf(4096))) : ActionResult.of(code)); });
        /** Validates the result. */ public ActionResultResponse { Objects.requireNonNull(requestId); Objects.requireNonNull(result); if (result.message().map(message -> message.getString().length() > 4096).orElse(false)) throw new IllegalArgumentException("Result message too long"); }
        @Override public Type<ActionResultResponse> type() { return TYPE; }
    }

    /** Authorized network metadata; device membership is paginated separately.
     * @param networkId network identity
     * @param data metadata copied on construction and access
     */
    public record HomeNetworkSnapshot(UUID networkId, CompoundTag data) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<HomeNetworkSnapshot> TYPE = typeId("home_network_snapshot");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, HomeNetworkSnapshot> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.networkId); tag(b, p.data); }, b -> new HomeNetworkSnapshot(b.readUUID(), tag(b)));
        /** Copies metadata. */ public HomeNetworkSnapshot { Objects.requireNonNull(networkId); data = Objects.requireNonNull(data).copy(); }
        /** Returns a defensive copy of the snapshot compound.
         * @return independent snapshot data
         */
        @Override public CompoundTag data() { return data.copy(); }
        @Override public Type<HomeNetworkSnapshot> type() { return TYPE; }
    }

    /** Server event for an authorized network subscription.
     * @param networkId network identity
     * @param event immutable event with bounded fields
     */
    public record DeviceEventNotification(UUID networkId, DeviceEvent event) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<DeviceEventNotification> TYPE = typeId("device_event_notification");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, DeviceEventNotification> STREAM_CODEC = StreamCodec.of(
                (b, p) -> { b.writeUUID(p.networkId); HomeCorePayloads.event(b, p.event); }, b -> new DeviceEventNotification(b.readUUID(), HomeCorePayloads.event(b)));
        /** Validates event metadata. */ public DeviceEventNotification { Objects.requireNonNull(networkId); Objects.requireNonNull(event); checkedId(event.type()); }
        @Override public Type<DeviceEventNotification> type() { return TYPE; }
    }

    /** Stops the sender's subscription to a network.
     * @param networkId network identity
     */
    public record Unsubscribe(UUID networkId) implements CustomPacketPayload {
        /** Payload registration type. */ public static final Type<Unsubscribe> TYPE = typeId("unsubscribe");
        /** Bounded transport codec. */ public static final StreamCodec<RegistryFriendlyByteBuf, Unsubscribe> STREAM_CODEC = StreamCodec.of((b, p) -> b.writeUUID(p.networkId), b -> new Unsubscribe(b.readUUID()));
        /** Validates the network identity. */ public Unsubscribe { Objects.requireNonNull(networkId); }
        @Override public Type<Unsubscribe> type() { return TYPE; }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeId(String path) { return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("homecore", path)); }
    private static void checkedId(ResourceLocation id) { if (Objects.requireNonNull(id).toString().length() > 256) throw new IllegalArgumentException("Identifier exceeds 256 characters"); }
    private static void id(RegistryFriendlyByteBuf buf, ResourceLocation id) { checkedId(id); buf.writeUtf(id.toString(), 256); }
    private static ResourceLocation id(RegistryFriendlyByteBuf buf) { return ResourceLocation.parse(buf.readUtf(256)); }
    private static boolean bool(RegistryFriendlyByteBuf buf) { int value = buf.readUnsignedByte(); if (value > 1) throw new IllegalArgumentException("Invalid boolean flag"); return value == 1; }
    private static void optional(RegistryFriendlyByteBuf buf, Optional<UUID> value) { buf.writeBoolean(value.isPresent()); value.ifPresent(buf::writeUUID); }
    private static Optional<UUID> optional(RegistryFriendlyByteBuf buf) { return bool(buf) ? Optional.of(buf.readUUID()) : Optional.empty(); }
    private static int count(RegistryFriendlyByteBuf buf, int max) { int count = buf.readVarInt(); if (count < 0 || count > max) throw new IllegalArgumentException("Collection exceeds wire bounds"); return count; }
    private static ActionResult.Code resultCode(RegistryFriendlyByteBuf buf) { int ordinal = buf.readUnsignedByte(); if (ordinal >= ActionResult.Code.values().length) throw new IllegalArgumentException("Unknown result code"); return ActionResult.Code.values()[ordinal]; }

    private static void tag(RegistryFriendlyByteBuf buf, CompoundTag value) {
        ByteBuf temporary = Unpooled.buffer(256, MAX_SNAPSHOT_BYTES);
        try {
            FriendlyByteBuf.writeNbt(temporary, value);
            int length = temporary.readableBytes();
            // Apply the same heap accounting as the decoder before sending a snapshot.
            FriendlyByteBuf.readNbt(temporary.duplicate(), NbtAccounter.create(MAX_SNAPSHOT_BYTES));
            buf.writeVarInt(length); buf.writeBytes(temporary);
        } finally { temporary.release(); }
    }

    private static CompoundTag tag(RegistryFriendlyByteBuf buf) {
        int length = count(buf, MAX_SNAPSHOT_BYTES);
        if (length == 0 || length > buf.readableBytes()) throw new IllegalArgumentException("Invalid snapshot length");
        ByteBuf slice = buf.readSlice(length);
        Tag tag = FriendlyByteBuf.readNbt(slice, NbtAccounter.create(MAX_SNAPSHOT_BYTES));
        if (!(tag instanceof CompoundTag compound) || slice.isReadable()) throw new IllegalArgumentException("Invalid snapshot compound");
        return compound;
    }

    private static void event(RegistryFriendlyByteBuf buf, DeviceEvent event) {
        id(buf, event.type()); buf.writeUUID(event.source()); buf.writeLong(event.timestamp().getEpochSecond()); buf.writeInt(event.timestamp().getNano());
        buf.writeByte(event.severity().ordinal()); buf.writeVarInt(event.data().size());
        event.data().forEach((key, value) -> { buf.writeUtf(key, 128); buf.writeUtf(value, 4096); });
    }

    private static DeviceEvent event(RegistryFriendlyByteBuf buf) {
        ResourceLocation type = id(buf); UUID source = buf.readUUID(); long seconds = buf.readLong(); int nanos = buf.readInt();
        if (nanos < 0 || nanos > 999_999_999) throw new IllegalArgumentException("Invalid timestamp nanos");
        int severity = buf.readUnsignedByte();
        if (severity >= DeviceEvent.Severity.values().length) throw new IllegalArgumentException("Unknown event severity");
        int fields = count(buf, 64); var data = new LinkedHashMap<String, String>();
        for (int i = 0; i < fields; i++) { String key = buf.readUtf(128); if (data.putIfAbsent(key, buf.readUtf(4096)) != null) throw new IllegalArgumentException("Duplicate event field"); }
        return new DeviceEvent(type, source, Instant.ofEpochSecond(seconds, nanos), DeviceEvent.Severity.values()[severity], data);
    }
}
