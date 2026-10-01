package fr.lkdm.homecore.api.stock;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Canonical identity of one physical inventory exposed to a stock reader.
 *
 * <p>Two providers describing the same block must produce equal identities, so a
 * consumer aggregating several providers can recognise a double chest or an
 * inventory reachable through two controllers and count it exactly once. A
 * provider that cannot produce a canonical identity must not report the source.</p>
 *
 * @param dimension dimension of the physical inventory
 * @param position canonical block position, for example the first half of a double chest
 */
public record StockSourceId(ResourceLocation dimension, BlockPos position) {
    /** Validates the identity and defensively immutabilises the position. */
    public StockSourceId {
        Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position").immutable();
    }

    /** Builds an identity from a level key and a canonical position.
     * @param level owning dimension
     * @param position canonical inventory position
     * @return stable source identity
     */
    public static StockSourceId of(ResourceKey<Level> level, BlockPos position) {
        return new StockSourceId(Objects.requireNonNull(level, "level").location(), position);
    }
}
