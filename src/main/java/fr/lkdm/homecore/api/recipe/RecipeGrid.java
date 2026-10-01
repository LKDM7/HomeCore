package fr.lkdm.homecore.api.recipe;

import java.util.List;
import java.util.Objects;

/**
 * Placement of a shaped recipe's requirements on a crafting grid.
 *
 * <p>Each slot holds the index of the {@link RecipeDescriptor#ingredients()} entry
 * it consumes, or {@code -1} when the slot stays empty. A recipe without a fixed
 * shape has no grid, and a consumer must render it as a list instead of inventing
 * positions.</p>
 *
 * @param width grid width in slots
 * @param height grid height in slots
 * @param slots row-major requirement index per slot, {@code -1} for an empty slot
 */
public record RecipeGrid(int width, int height, List<Integer> slots) {
    /** Largest grid side a descriptor may declare. */
    public static final int MAX_SIDE = 3;

    /** Validates the shape and copies its slot mapping. */
    public RecipeGrid {
        if (width < 1 || width > MAX_SIDE || height < 1 || height > MAX_SIDE) {
            throw new IllegalArgumentException("Unsupported grid size " + width + "x" + height);
        }
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        if (slots.size() != width * height) throw new IllegalArgumentException("Grid slots must match its size");
        for (Integer slot : slots) {
            if (slot == null || slot < -1) throw new IllegalArgumentException("Invalid grid slot reference");
        }
    }
}
