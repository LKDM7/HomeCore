package fr.lkdm.homecore.api.transport;

import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.action.Unit;
import fr.lkdm.homecore.api.metric.Duration;
import fr.lkdm.homecore.api.metric.Energy;
import fr.lkdm.homecore.api.metric.FluidValue;
import fr.lkdm.homecore.api.metric.ItemValue;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Position;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable transport value with a closed set of safe types. Enum values carry
 * only a constant name; decoding never loads classes named by the sender.
 * @param kind wire type
 * @param value immutable value matching the type
 */
public record WireValue(Kind kind, Object value) {
    /** Wire value types. Ordinals are part of protocol version 1. */
    public enum Kind {
        /** Boolean. */ BOOLEAN,
        /** Signed integer. */ INTEGER,
        /** Exact signed long. */ LONG,
        /** Finite double. */ DOUBLE,
        /** Bounded text. */ STRING,
        /** Enum constant name. */ ENUM,
        /** Percentage. */ PERCENTAGE,
        /** Duration in ticks. */ DURATION,
        /** Metric block coordinates. */ POSITION,
        /** Item and quantity. */ ITEM,
        /** Fluid and quantity. */ FLUID,
        /** Stored energy and capacity. */ ENERGY,
        /** Explicit button parameter. */ UNIT,
        /** Action block coordinates. */ BLOCK_POS
    }

    /** Enum name without a Java class reference.
     * @param name exact constant name, at most 4096 characters
     */
    public record EnumName(String name) {
        /** Validates the constant name. */
        public EnumName { bounded(name); if (name.isEmpty()) throw new IllegalArgumentException("Empty enum name"); }
    }

    /** Modern bounded stream codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, WireValue> STREAM_CODEC = StreamCodec.of(WireValue::encode, WireValue::decode);

    /** Validates and canonicalizes the known immutable value. */
    public WireValue {
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(value, "value");
        Class<?> expected = switch (kind) {
            case BOOLEAN -> Boolean.class; case INTEGER -> Integer.class; case LONG -> Long.class;
            case DOUBLE -> Double.class; case STRING -> String.class; case ENUM -> EnumName.class;
            case PERCENTAGE -> Percentage.class; case DURATION -> Duration.class; case POSITION -> Position.class;
            case ITEM -> ItemValue.class; case FLUID -> FluidValue.class; case ENERGY -> Energy.class;
            case UNIT -> Unit.class; case BLOCK_POS -> BlockPos.class;
        };
        if (!expected.isInstance(value)) throw new IllegalArgumentException("Value does not match wire kind " + kind);
        if (value instanceof Double number && !Double.isFinite(number)) throw new IllegalArgumentException("Nonfinite wire value");
        if (value instanceof String text) bounded(text);
        if (value instanceof ItemValue item) identifier(item.item().toString());
        if (value instanceof FluidValue fluid) identifier(fluid.fluid().toString());
        if (value instanceof BlockPos position) value = position.immutable();
    }

    /** Converts a supported immutable API value.
     * @param value value to transport
     * @return canonical wire value
     * @throws IllegalArgumentException for unsupported types
     */
    public static WireValue from(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof Enum<?> enumValue && !(value instanceof Unit)) return new WireValue(Kind.ENUM, new EnumName(enumValue.name()));
        Kind kind = switch (value) {
            case Boolean ignored -> Kind.BOOLEAN; case Integer ignored -> Kind.INTEGER; case Long ignored -> Kind.LONG;
            case Double ignored -> Kind.DOUBLE; case String ignored -> Kind.STRING; case EnumName ignored -> Kind.ENUM;
            case Percentage ignored -> Kind.PERCENTAGE; case Duration ignored -> Kind.DURATION; case Position ignored -> Kind.POSITION;
            case ItemValue ignored -> Kind.ITEM; case FluidValue ignored -> Kind.FLUID; case Energy ignored -> Kind.ENERGY;
            case Unit ignored -> Kind.UNIT; case BlockPos ignored -> Kind.BLOCK_POS;
            default -> throw new IllegalArgumentException("Unsupported wire value: " + value.getClass().getName());
        };
        return new WireValue(kind, value);
    }

    /** Maps enum names only to the action's explicitly declared options.
     * @param action trusted server action definition
     * @return checked runtime parameter; normal action validation must still run
     * @throws IllegalArgumentException when the parameter type or enum name is incompatible
     */
    public Object toActionValue(DeviceAction<?> action) {
        Objects.requireNonNull(action, "action");
        if (kind == Kind.ENUM) {
            String name = ((EnumName) value).name();
            return action.options().stream().filter(option -> option instanceof Enum<?> constant && constant.name().equals(name))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown enum option"));
        }
        if (!action.valueType().isInstance(value)) throw new IllegalArgumentException("Incompatible action parameter type");
        return value;
    }

    /** Converts this value to snapshot NBT; compound ownership belongs to the caller.
     * @return fresh structured value tag
     */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag(); tag.putString("kind", kind.name());
        switch (kind) {
            case BOOLEAN -> tag.putBoolean("value", (Boolean) value);
            case INTEGER -> tag.putInt("value", (Integer) value);
            case LONG -> tag.putLong("value", (Long) value);
            case DOUBLE -> tag.putDouble("value", (Double) value);
            case STRING -> tag.putString("value", (String) value);
            case ENUM -> tag.putString("value", ((EnumName) value).name());
            case PERCENTAGE -> tag.putDouble("value", ((Percentage) value).value());
            case DURATION -> tag.putLong("value", ((Duration) value).ticks());
            case POSITION -> { Position pos = (Position) value; position(tag, pos.x(), pos.y(), pos.z()); }
            case BLOCK_POS -> { BlockPos pos = (BlockPos) value; position(tag, pos.getX(), pos.getY(), pos.getZ()); }
            case ITEM -> { ItemValue item = (ItemValue) value; tag.putString("id", item.item().toString()); tag.putLong("amount", item.count()); }
            case FLUID -> { FluidValue fluid = (FluidValue) value; tag.putString("id", fluid.fluid().toString()); tag.putLong("amount", fluid.amount()); }
            case ENERGY -> { Energy energy = (Energy) value; tag.putLong("stored", energy.stored()); tag.putLong("capacity", energy.capacity()); }
            case UNIT -> { }
        }
        return tag;
    }

    /** Reads a known value from bounded snapshot NBT and validates exact field types.
     * @param tag snapshot value tag
     * @return decoded immutable value
     */
    public static WireValue fromTag(CompoundTag tag) {
        require(tag, "kind", Tag.TAG_STRING);
        Kind kind = Kind.valueOf(tag.getString("kind"));
        Object value = switch (kind) {
            case BOOLEAN -> { require(tag, "value", Tag.TAG_BYTE); byte b = tag.getByte("value"); if (b != 0 && b != 1) throw new IllegalArgumentException("Invalid boolean"); yield b == 1; }
            case INTEGER -> { require(tag, "value", Tag.TAG_INT); yield tag.getInt("value"); }
            case LONG -> { require(tag, "value", Tag.TAG_LONG); yield tag.getLong("value"); }
            case DOUBLE -> { require(tag, "value", Tag.TAG_DOUBLE); yield tag.getDouble("value"); }
            case STRING -> { require(tag, "value", Tag.TAG_STRING); yield tag.getString("value"); }
            case ENUM -> { require(tag, "value", Tag.TAG_STRING); yield new EnumName(tag.getString("value")); }
            case PERCENTAGE -> { require(tag, "value", Tag.TAG_DOUBLE); yield new Percentage(tag.getDouble("value")); }
            case DURATION -> { require(tag, "value", Tag.TAG_LONG); yield new Duration(tag.getLong("value")); }
            case POSITION, BLOCK_POS -> {
                require(tag, "x", Tag.TAG_INT); require(tag, "y", Tag.TAG_INT); require(tag, "z", Tag.TAG_INT);
                yield kind == Kind.POSITION ? new Position(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")) : new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            }
            case ITEM, FLUID -> {
                require(tag, "id", Tag.TAG_STRING); require(tag, "amount", Tag.TAG_LONG);
                ResourceLocation id = identifier(tag.getString("id")); long amount = tag.getLong("amount");
                yield kind == Kind.ITEM ? new ItemValue(id, amount) : new FluidValue(id, amount);
            }
            case ENERGY -> { require(tag, "stored", Tag.TAG_LONG); require(tag, "capacity", Tag.TAG_LONG); yield new Energy(tag.getLong("stored"), tag.getLong("capacity")); }
            case UNIT -> Unit.INSTANCE;
        };
        return new WireValue(kind, value);
    }

    private static void position(CompoundTag tag, int x, int y, int z) { tag.putInt("x", x); tag.putInt("y", y); tag.putInt("z", z); }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing or incorrect value field: " + key); }
    private static void bounded(String text) { if (Objects.requireNonNull(text).length() > 4096) throw new IllegalArgumentException("Wire string exceeds 4096 characters"); }
    private static ResourceLocation identifier(String id) { if (id.length() > 256) throw new IllegalArgumentException("Identifier too long"); return ResourceLocation.parse(id); }

    private static void encode(RegistryFriendlyByteBuf buf, WireValue wire) {
        buf.writeByte(wire.kind.ordinal()); Object value = wire.value;
        switch (wire.kind) {
            case BOOLEAN -> buf.writeBoolean((Boolean) value); case INTEGER -> buf.writeInt((Integer) value); case LONG -> buf.writeLong((Long) value);
            case DOUBLE -> buf.writeDouble((Double) value); case STRING -> buf.writeUtf((String) value, 4096);
            case ENUM -> buf.writeUtf(((EnumName) value).name(), 4096); case PERCENTAGE -> buf.writeDouble(((Percentage) value).value());
            case DURATION -> buf.writeLong(((Duration) value).ticks());
            case POSITION -> { Position pos = (Position) value; buf.writeInt(pos.x()); buf.writeInt(pos.y()); buf.writeInt(pos.z()); }
            case BLOCK_POS -> { BlockPos pos = (BlockPos) value; buf.writeInt(pos.getX()); buf.writeInt(pos.getY()); buf.writeInt(pos.getZ()); }
            case ITEM -> { ItemValue item = (ItemValue) value; buf.writeUtf(item.item().toString(), 256); buf.writeLong(item.count()); }
            case FLUID -> { FluidValue fluid = (FluidValue) value; buf.writeUtf(fluid.fluid().toString(), 256); buf.writeLong(fluid.amount()); }
            case ENERGY -> { Energy energy = (Energy) value; buf.writeLong(energy.stored()); buf.writeLong(energy.capacity()); }
            case UNIT -> { }
        }
    }

    private static WireValue decode(RegistryFriendlyByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        if (ordinal >= Kind.values().length) throw new IllegalArgumentException("Unknown wire value kind");
        Kind kind = Kind.values()[ordinal];
        Object value = switch (kind) {
            case BOOLEAN -> { int b = buf.readUnsignedByte(); if (b > 1) throw new IllegalArgumentException("Invalid boolean"); yield b == 1; }
            case INTEGER -> buf.readInt(); case LONG -> buf.readLong(); case DOUBLE -> buf.readDouble(); case STRING -> buf.readUtf(4096);
            case ENUM -> new EnumName(buf.readUtf(4096)); case PERCENTAGE -> new Percentage(buf.readDouble()); case DURATION -> new Duration(buf.readLong());
            case POSITION -> new Position(buf.readInt(), buf.readInt(), buf.readInt()); case BLOCK_POS -> new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            case ITEM -> new ItemValue(identifier(buf.readUtf(256)), buf.readLong()); case FLUID -> new FluidValue(identifier(buf.readUtf(256)), buf.readLong());
            case ENERGY -> new Energy(buf.readLong(), buf.readLong()); case UNIT -> Unit.INSTANCE;
        };
        return new WireValue(kind, value);
    }
}
