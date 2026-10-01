package fr.lkdm.homecore.api.stock;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * A bounded, read-only stock question asked on behalf of an authenticated player.
 *
 * <p>Reading never extracts, moves or reserves anything. The provider answers only
 * about the variants asked for, so a consumer never receives a catalogue of a
 * network it did not ask about. The provider re-checks the player's own rights;
 * passing an identity here does not grant one.</p>
 *
 * @param network network context the question is scoped to
 * @param player authenticated player the answer is computed for, never an identity taken from a packet
 * @param variants count-one prototypes of interest, at most {@link #MAX_VARIANTS}
 */
public record StockRequest(UUID network, UUID player, List<ItemStack> variants) {
    /** Maximum number of variants one request may ask about. */
    public static final int MAX_VARIANTS = 64;

    /** Validates the scope and copies the requested prototypes. */
    public StockRequest {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(variants, "variants");
        if (variants.size() > MAX_VARIANTS) throw new IllegalArgumentException("At most " + MAX_VARIANTS + " variants per request");
        variants = variants.stream().map(stack -> {
            Objects.requireNonNull(stack, "variant");
            if (stack.isEmpty()) throw new IllegalArgumentException("Requested variant must not be empty");
            return stack.copyWithCount(1);
        }).toList();
    }

    /** Returns fresh copies of the requested prototypes.
     * @return count-one prototypes
     */
    @Override
    public List<ItemStack> variants() { return variants.stream().map(ItemStack::copy).toList(); }
}
