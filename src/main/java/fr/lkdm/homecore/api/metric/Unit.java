package fr.lkdm.homecore.api.metric;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Extensible unit identified by a namespaced ID; external mods may construct their own units.
 * @param id stable unit identifier
 * @param symbol human-readable unit symbol
 */
public record Unit(ResourceLocation id, String symbol) {
    /** Dimensionless. */ public static final Unit NONE = of("none", "");
    /** Percent. */ public static final Unit PERCENT = of("percent", "%");
    /** Blocks. */ public static final Unit BLOCK = of("block", "blocks");
    /** Items. */ public static final Unit ITEM = of("item", "items");
    /** Item throughput. */ public static final Unit ITEMS_PER_HOUR = of("items_per_hour", "items/h");
    /** Forge energy. */ public static final Unit FE = of("fe", "FE");
    /** Energy throughput. */ public static final Unit FE_PER_TICK = of("fe_per_tick", "FE/t");
    /** Game ticks. */ public static final Unit TICKS = of("ticks", "ticks");
    /** Seconds. */ public static final Unit SECONDS = of("seconds", "s");
    /** Celsius temperature. */ public static final Unit CELSIUS = of("celsius", "°C");
    /** Revolutions per minute. */ public static final Unit RPM = of("rpm", "RPM");

    /** Creates a non-null unit. */
    public Unit { Objects.requireNonNull(id, "id"); Objects.requireNonNull(symbol, "symbol"); }

    private static Unit of(String path, String symbol) {
        return new Unit(ResourceLocation.fromNamespaceAndPath("homecore", path), symbol);
    }
}
