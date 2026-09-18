package fr.lkdm.homecore.api.metric;

import java.math.BigDecimal;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/** Built-in immutable metric value contracts. */
public final class MetricTypes {
    /** Boolean. */ public static final MetricType<Boolean> BOOLEAN = simple("boolean", Boolean.class);
    /** Signed integer. */ public static final MetricType<Integer> INTEGER = new MetricType<>(id("integer"), Integer.class, v -> true, v -> BigDecimal.valueOf(v.longValue()));
    /** Signed long, preserving all 64 bits during validation. */ public static final MetricType<Long> LONG = new MetricType<>(id("long"), Long.class, v -> true, BigDecimal::valueOf);
    /** Finite double. */ public static final MetricType<Double> DOUBLE = new MetricType<>(id("double"), Double.class, Double::isFinite, BigDecimal::valueOf);
    /** Text. */ public static final MetricType<String> STRING = simple("string", String.class);
    /** Percentage from 0 through 100. */ public static final MetricType<Percentage> PERCENTAGE = new MetricType<>(id("percentage"), Percentage.class, v -> true, v -> BigDecimal.valueOf(v.value()));
    /** Duration in ticks. */ public static final MetricType<Duration> DURATION = new MetricType<>(id("duration"), Duration.class, v -> true, v -> BigDecimal.valueOf(v.ticks()));
    /** Block coordinates. */ public static final MetricType<Position> POSITION = simple("position", Position.class);
    /** Item identity and count. */ public static final MetricType<ItemValue> ITEM = simple("item", ItemValue.class);
    /** Fluid identity and amount. */ public static final MetricType<FluidValue> FLUID = simple("fluid", FluidValue.class);
    /** Stored energy and capacity. */ public static final MetricType<Energy> ENERGY = new MetricType<>(id("energy"), Energy.class, v -> true, v -> BigDecimal.valueOf(v.stored()));

    private MetricTypes() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("homecore", path); }
    private static <T> MetricType<T> simple(String path, Class<T> type) { return new MetricType<>(id(path), type, v -> true); }

    /** Creates an enum contract with a namespaced ID supplied by its owning mod.
     * @param id stable type identifier
     * @param type enum class
     * @param <E> enum type
     * @return enum metric type
     */
    public static <E extends Enum<E>> MetricType<E> enumeration(ResourceLocation id, Class<E> type) {
        if (!type.isEnum()) throw new IllegalArgumentException("Expected enum class");
        return new MetricType<>(id, type, value -> true);
    }

    /** Creates an enum contract using its fully qualified Java class name as a local identifier.
     * @param type enum class
     * @param <E> enum type
     * @return enum metric type
     */
    public static <E extends Enum<E>> MetricType<E> enumeration(Class<E> type) {
        return enumeration(id("enum/" + type.getName().toLowerCase(Locale.ROOT).replace('$', '/')), type);
    }
}
