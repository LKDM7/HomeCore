package fr.lkdm.homecore.api.transport;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.ActionType;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.action.Unit;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.Duration;
import fr.lkdm.homecore.api.metric.Energy;
import fr.lkdm.homecore.api.metric.FluidValue;
import fr.lkdm.homecore.api.metric.ItemValue;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Position;
import io.netty.buffer.Unpooled;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static fr.lkdm.homecore.api.transport.HomeCorePayloads.*;

class PayloadCodecTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
        var buf = buffer();
        try { codec.encode(buf, payload); T result = codec.decode(buf); assertFalse(buf.isReadable()); return result; }
        finally { buf.release(); }
    }

    @Test void roundTripsEveryPayload() {
        UUID request = UUID.randomUUID(), network = UUID.randomUUID(), device = UUID.randomUUID();
        ResourceLocation id = ResourceLocation.parse("test:value");
        CompoundTag tag = new CompoundTag(); tag.putString("name", "Machine");
        var listRequest = new DeviceListRequest(request, Optional.of(network), 16);
        assertEquals(listRequest, roundTrip(DeviceListRequest.STREAM_CODEC, listRequest));
        var listResponse = new DeviceListResponse(request, Optional.of(network), List.of(device), -1, ActionResult.Code.SUCCESS);
        assertEquals(listResponse, roundTrip(DeviceListResponse.STREAM_CODEC, listResponse));
        var watchRequest = new NetworkWatchRequest(request, network);
        assertEquals(watchRequest, roundTrip(NetworkWatchRequest.STREAM_CODEC, watchRequest));
        var watchResponse = new NetworkWatchResponse(request, network, List.of(device), 1, false, ActionResult.Code.SUCCESS);
        assertEquals(watchResponse, roundTrip(NetworkWatchResponse.STREAM_CODEC, watchResponse));
        var snapshot = new DeviceSnapshot(network, device, tag);
        assertEquals(snapshot, roundTrip(DeviceSnapshot.STREAM_CODEC, snapshot));
        var delta = new MetricUpdate(network, device, id, 7, WireValue.from(Long.MAX_VALUE));
        assertEquals(delta, roundTrip(MetricUpdate.STREAM_CODEC, delta));
        var execute = new ExecuteActionRequest(request, network, device, id, WireValue.from(Unit.INSTANCE));
        assertEquals(execute, roundTrip(ExecuteActionRequest.STREAM_CODEC, execute));
        var result = new ActionResultResponse(request, ActionResult.of(ActionResult.Code.DENIED, Component.literal("Denied")));
        assertEquals(result, roundTrip(ActionResultResponse.STREAM_CODEC, result));
        var home = new HomeNetworkSnapshot(network, tag);
        assertEquals(home, roundTrip(HomeNetworkSnapshot.STREAM_CODEC, home));
        var event = new DeviceEventNotification(network, new DeviceEvent(id, device, Instant.now(), DeviceEvent.Severity.INFO, Map.of("key", "value")));
        assertEquals(event, roundTrip(DeviceEventNotification.STREAM_CODEC, event));
        var unsubscribe = new Unsubscribe(network);
        assertEquals(unsubscribe, roundTrip(Unsubscribe.STREAM_CODEC, unsubscribe));
    }

    @Test void watchRosterBoundsAndTruncationAreValidated() {
        UUID request = UUID.randomUUID(), network = UUID.randomUUID(), device = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new NetworkWatchResponse(request, network, List.of(device, device), 2, false, ActionResult.Code.SUCCESS));
        assertThrows(IllegalArgumentException.class, () -> new NetworkWatchResponse(request, network, List.of(device), 2, false, ActionResult.Code.SUCCESS));
        List<UUID> ids = java.util.stream.IntStream.range(0, 129).mapToObj(index -> new UUID(0, index)).toList();
        assertThrows(IllegalArgumentException.class, () -> new NetworkWatchResponse(request, network, ids, 129, false, ActionResult.Code.SUCCESS));
        var capped = new NetworkWatchResponse(request, network, ids.subList(0, 128), 129, true, ActionResult.Code.SUCCESS);
        assertEquals(capped, roundTrip(NetworkWatchResponse.STREAM_CODEC, capped));
    }

    @Test void itemAndFluidIdentifiersHaveSymmetricConstructionAndCodecBounds() {
        ResourceLocation longest = ResourceLocation.fromNamespaceAndPath("test", "a".repeat(251));
        ResourceLocation excessive = ResourceLocation.fromNamespaceAndPath("test", "a".repeat(252));
        for (Object valid : List.of(new ItemValue(longest, 1), new FluidValue(longest, 1000))) {
            WireValue wire = WireValue.from(valid);
            assertEquals(wire, roundTrip(WireValue.STREAM_CODEC, wire));
            assertEquals(wire, WireValue.fromTag(wire.toTag()));
        }
        assertThrows(IllegalArgumentException.class, () -> WireValue.from(new ItemValue(excessive, 1)));
        assertThrows(IllegalArgumentException.class, () -> WireValue.from(new FluidValue(excessive, 1000)));
    }

    private enum Choice { ALPHA, BETA }

    @Test void roundTripsAllValueTypesWithoutLongPrecisionLoss() {
        List<Object> values = List.of(true, 42, Long.MAX_VALUE, 22.5, "hello", Choice.ALPHA,
                new Percentage(73), new Duration(Long.MAX_VALUE), new Position(Integer.MIN_VALUE, 22, Integer.MAX_VALUE),
                new ItemValue(ResourceLocation.parse("minecraft:stone"), 100), new FluidValue(ResourceLocation.parse("minecraft:water"), 1000),
                new Energy(4500, 10000), Unit.INSTANCE, new BlockPos(1, 2, 3));
        for (Object value : values) {
            WireValue wire = WireValue.from(value);
            assertEquals(wire, roundTrip(WireValue.STREAM_CODEC, wire));
            assertEquals(wire, WireValue.fromTag(wire.toTag()));
        }
        assertEquals(Long.MAX_VALUE, roundTrip(WireValue.STREAM_CODEC, WireValue.from(Long.MAX_VALUE)).value());
    }

    @Test void enumConversionUsesOnlyDeclaredOptionsAndExactOtherTypes() {
        var action = DeviceAction.builder(ResourceLocation.parse("test:select"), Component.literal("Select"), ActionType.SELECT, Choice.class)
                .options(List.of(Choice.ALPHA)).handler((context, choice) -> ActionResult.success()).build();
        assertEquals(Choice.ALPHA, WireValue.from(Choice.ALPHA).toActionValue(action));
        assertThrows(IllegalArgumentException.class, () -> WireValue.from(Choice.BETA).toActionValue(action));
        assertThrows(IllegalArgumentException.class, () -> WireValue.from("ALPHA").toActionValue(action));
        assertThrows(IllegalArgumentException.class, () -> WireValue.from(new Object()));
    }

    @Test void snapshotsAndPositionsAreDefensivelyCopied() {
        CompoundTag original = new CompoundTag(); original.putString("name", "Before");
        var snapshot = new DeviceSnapshot(UUID.randomUUID(), UUID.randomUUID(), original);
        var home = new HomeNetworkSnapshot(UUID.randomUUID(), original);
        original.putString("name", "After"); snapshot.data().putString("name", "Mutation");
        assertEquals("Before", snapshot.data().getString("name"));
        assertEquals("Before", home.data().getString("name"));
        var position = new BlockPos.MutableBlockPos(1, 2, 3);
        WireValue wire = WireValue.from(position); position.set(4, 5, 6);
        assertEquals(new BlockPos(1, 2, 3), wire.value());
    }

    @Test void rejectsUnknownTagsNonfiniteValuesAndOversizedStrings() {
        assertThrows(IllegalArgumentException.class, () -> WireValue.from(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> WireValue.from("a".repeat(4097)));
        CompoundTag bad = new CompoundTag(); bad.putString("kind", "LONG"); bad.putString("value", "42");
        assertThrows(IllegalArgumentException.class, () -> WireValue.fromTag(bad));
        var buf = buffer();
        try {
            buf.writeByte(255);
            assertThrows(IllegalArgumentException.class, () -> WireValue.STREAM_CODEC.decode(buf));
            buf.clear(); buf.writeByte(WireValue.Kind.DOUBLE.ordinal()); buf.writeDouble(Double.NaN);
            assertThrows(IllegalArgumentException.class, () -> WireValue.STREAM_CODEC.decode(buf));
            buf.clear(); buf.writeByte(WireValue.Kind.STRING.ordinal()); buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(RuntimeException.class, () -> WireValue.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }

    @Test void rejectsAllocationAbuseBeforeReadingCollectionsOrNbt() {
        var buf = buffer();
        try {
            buf.writeUUID(UUID.randomUUID()); buf.writeBoolean(false); buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> DeviceListResponse.STREAM_CODEC.decode(buf));
            buf.clear(); buf.writeUUID(UUID.randomUUID()); buf.writeVarInt(MAX_SNAPSHOT_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> HomeNetworkSnapshot.STREAM_CODEC.decode(buf));
            buf.clear(); buf.writeUUID(UUID.randomUUID()); buf.writeVarInt(-1);
            assertThrows(IllegalArgumentException.class, () -> HomeNetworkSnapshot.STREAM_CODEC.decode(buf));
            buf.clear(); buf.writeUUID(UUID.randomUUID()); buf.writeVarInt(1); buf.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> HomeNetworkSnapshot.STREAM_CODEC.decode(buf));
            CompoundTag large = new CompoundTag(); large.putByteArray("bytes", new byte[MAX_SNAPSHOT_BYTES]);
            var payload = new HomeNetworkSnapshot(UUID.randomUUID(), large);
            buf.clear();
            assertThrows(RuntimeException.class, () -> HomeNetworkSnapshot.STREAM_CODEC.encode(buf, payload));
        } finally { buf.release(); }
    }
}
