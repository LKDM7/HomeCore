package fr.lkdm.homecore.network;

import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.transport.WireValue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

/** Creates bounded data-only descriptions. Handlers and Java class names are never transported. */
public final class SnapshotEncoder {
    private SnapshotEncoder() { }

    public static CompoundTag device(DashboardDevice device) {
        DeviceSchema schema = DeviceSchema.from(device);
        if (schema.metrics().size() > 128 || schema.actions().size() > 128
                || schema.events().size() > 128 || device.capabilities().size() > 128) {
            throw new IllegalArgumentException("Device schema exceeds snapshot limits");
        }
        CompoundTag result = new CompoundTag();
        result.putUUID("id", device.id());
        put(result, "type", device.deviceType().toString(), 256);
        put(result, "name", device.displayName().getString(), 256);
        var status = device.status();
        result.putString("status", status.state().name());
        status.message().ifPresent(message -> put(result, "message", message.getString(), 1024));
        device.position().ifPresent(position -> {
            result.putInt("x", position.getX()); result.putInt("y", position.getY()); result.putInt("z", position.getZ());
        });
        device.dimension().ifPresent(dimension -> put(result, "dimension", dimension.location().toString(), 256));
        ListTag metrics = new ListTag();
        result.put("metrics", metrics);
        for (var metric : schema.metrics()) {
            CompoundTag data = new CompoundTag();
            put(data, "id", metric.id().toString(), 256);
            put(data, "name", metric.displayName().getString(), 256);
            put(data, "type", metric.type().id().toString(), 256);
            put(data, "unit", metric.unit().id().toString(), 256);
            put(data, "unitSymbol", metric.unit().symbol(), 64);
            data.putString("policy", metric.updatePolicy().name());
            metric.range().ifPresent(range -> {
                CompoundTag bounds = new CompoundTag();
                put(bounds, "min", range.min().toString(), 128);
                put(bounds, "max", range.max().toString(), 128);
                put(bounds, "step", range.step().toString(), 128);
                data.put("range", bounds);
            });
            var snapshot = metric.snapshot();
            data.put("value", WireValue.from(snapshot.value()).toTag());
            data.putLong("revision", snapshot.revision());
            metrics.add(data);
            requireBudget(result);
        }
        result.put("metrics", metrics);
        ListTag actions = new ListTag();
        result.put("actions", actions);
        for (var action : schema.actions()) {
            CompoundTag data = new CompoundTag();
            put(data, "id", action.id().toString(), 256);
            put(data, "name", action.displayName().getString(), 256);
            put(data, "description", action.description().getString(), 1024);
            data.putString("type", action.type().name());
            put(data, "permission", action.requiredPermission().toString(), 256);
            action.min().ifPresent(value -> data.putDouble("min", value));
            action.max().ifPresent(value -> data.putDouble("max", value));
            action.step().ifPresent(value -> data.putDouble("step", value));
            data.putInt("maxLength", Math.min(action.maxLength(), 4096));
            ListTag options = new ListTag();
            data.put("options", options);
            actions.add(data);
            for (Object value : action.options()) {
                options.add(WireValue.from(value).toTag());
                requireBudget(result);
            }
            requireBudget(result);
        }
        result.put("actions", actions);
        ListTag events = new ListTag();
        for (var event : schema.events()) events.add(StringTag.valueOf(bounded(event.toString(), 256)));
        result.put("events", events);
        ListTag capabilities = new ListTag();
        for (var capability : device.capabilities()) capabilities.add(StringTag.valueOf(bounded(capability.toString(), 256)));
        result.put("capabilities", capabilities);
        return checked(result);
    }

    /** Network summary; potentially large member/device sets are discovered separately. */
    public static CompoundTag network(HomeNetwork network) {
        CompoundTag data = new CompoundTag();
        data.putUUID("id", network.id());
        put(data, "name", network.name(), 128);
        data.putUUID("owner", network.owner());
        data.putLong("createdAtSeconds", network.createdAt().getEpochSecond());
        data.putInt("createdAtNanos", network.createdAt().getNano());
        data.putInt("memberCount", network.members().size());
        data.putInt("deviceCount", network.devices().size());
        return checked(data);
    }

    /** Applies both serialized byte and decoded allocation limits before sending a snapshot. */
    public static CompoundTag checked(CompoundTag data) {
        requireBudget(data);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(data, new DataOutputStream(bytes));
            if (bytes.size() > 65_536) throw new IllegalArgumentException("Snapshot exceeds 64 KiB");
            NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), NbtAccounter.create(65_536));
            return data;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot encode device snapshot", exception);
        }
    }

    private static void requireBudget(CompoundTag data) {
        if (data.sizeInBytes() > 65_536) throw new IllegalArgumentException("Snapshot exceeds allocation budget");
    }

    private static void put(CompoundTag tag, String key, String value, int max) { tag.putString(key, bounded(value, max)); }
    private static String bounded(String value, int max) {
        if (value.length() > max) throw new IllegalArgumentException("Snapshot text exceeds limit");
        return value;
    }
}
