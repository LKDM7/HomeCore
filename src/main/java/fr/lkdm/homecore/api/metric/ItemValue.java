package fr.lkdm.homecore.api.metric;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Item identity and quantity without retaining a mutable ItemStack.
 * @param item registry identifier
 * @param count nonnegative item count
 */
public record ItemValue(ResourceLocation item, long count) {
    /** Validates the identifier and count. */
    public ItemValue { Objects.requireNonNull(item, "item"); if (count < 0) throw new IllegalArgumentException("Negative item count"); }
}
