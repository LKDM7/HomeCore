package fr.lkdm.homecore.api.metric;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Fluid identity and amount without retaining a mutable fluid stack.
 * @param fluid registry identifier
 * @param amount nonnegative amount in millibuckets
 */
public record FluidValue(ResourceLocation fluid, long amount) {
    /** Validates the identifier and amount. */
    public FluidValue { Objects.requireNonNull(fluid, "fluid"); if (amount < 0) throw new IllegalArgumentException("Negative fluid amount"); }
}
