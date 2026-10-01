package fr.lkdm.homecore.api.stock;

import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/**
 * Observed quantity of one item variant, attributed to the physical sources that hold it.
 *
 * <p>Counts are per source so a consumer merging several providers can deduplicate
 * exactly: the same {@link StockSourceId} reported twice describes one inventory,
 * not two. The variant is a count-one prototype; its data components are part of
 * its identity and must not be ignored when matching.</p>
 *
 * @param variant count-one prototype identifying the item and its components
 * @param bySource observed quantity held by each canonical source
 */
public record StockEntry(ItemStack variant, Map<StockSourceId, Long> bySource) {
    /** Maximum number of distinct sources described for one variant. */
    public static final int MAX_SOURCES = 4096;

    /** Copies the prototype and validates every per-source quantity. */
    public StockEntry {
        Objects.requireNonNull(variant, "variant");
        if (variant.isEmpty()) throw new IllegalArgumentException("Stock variant must not be empty");
        variant = variant.copyWithCount(1);
        bySource = Map.copyOf(Objects.requireNonNull(bySource, "bySource"));
        if (bySource.size() > MAX_SOURCES) throw new IllegalArgumentException("Too many stock sources for one variant");
        for (var source : bySource.entrySet()) {
            Objects.requireNonNull(source.getKey(), "source");
            if (source.getValue() == null || source.getValue() < 0) {
                throw new IllegalArgumentException("Stock quantities must not be negative");
            }
        }
    }

    /** Returns a fresh copy of the identifying prototype.
     * @return count-one prototype
     */
    @Override
    public ItemStack variant() { return variant.copy(); }

    /** Sums the observed quantity across this entry's own sources.
     * @return total observed quantity, saturating rather than overflowing
     */
    public long total() {
        long total = 0;
        for (long count : bySource.values()) {
            total += count;
            if (total < 0) return Long.MAX_VALUE;
        }
        return total;
    }
}
