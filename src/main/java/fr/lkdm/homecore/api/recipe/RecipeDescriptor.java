package fr.lkdm.homecore.api.recipe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Neutral description of a recipe a consumer can plan against.
 *
 * <p>Quantities describe one operation: {@link #ingredients()} are consumed once and
 * {@link #result()} carries the yield of that single operation. A consumer planning
 * a remaining quantity computes its own operation count; it must not assume the
 * yield is one, and it must not exceed {@link #maxBatchOutput()}, which is the
 * producer's own limit and is unaffected by how many items a task still wants.</p>
 *
 * <p>Describing a recipe says nothing about whether the workstation is reachable,
 * powered or has room for the output.</p>
 *
 * @param recipeId identity of the described recipe
 * @param station namespaced workstation required to run it
 * @param ingredients requirements for one operation
 * @param result items produced by one operation; its count is the yield
 * @param maxBatchOutput largest number of items the producer will finish in one batch
 * @param grid slot placement for a shaped recipe, empty when the recipe has no fixed shape
 */
public record RecipeDescriptor(ResourceLocation recipeId, ResourceLocation station,
                               List<RecipeIngredient> ingredients, ItemStack result,
                               int maxBatchOutput, Optional<RecipeGrid> grid) {
    /** Largest number of distinct requirements a described recipe may declare. */
    public static final int MAX_INGREDIENTS = 9;

    /** Validates the description and copies its result stack. */
    public RecipeDescriptor {
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(grid, "grid");
        ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        if (ingredients.isEmpty() || ingredients.size() > MAX_INGREDIENTS) {
            throw new IllegalArgumentException("A descriptor declares 1 to " + MAX_INGREDIENTS + " requirements");
        }
        Objects.requireNonNull(result, "result");
        if (result.isEmpty()) throw new IllegalArgumentException("A descriptor must produce items");
        result = result.copy();
        if (maxBatchOutput < result.getCount()) {
            throw new IllegalArgumentException("A batch must fit at least one operation");
        }
        int requirements = ingredients.size();
        if (grid.isPresent()) {
            for (Integer slot : grid.get().slots()) {
                if (slot >= requirements) throw new IllegalArgumentException("Grid slot references a missing requirement");
            }
        }
    }

    /** Returns a fresh copy of the single-operation result.
     * @return produced stack for one operation
     */
    @Override
    public ItemStack result() { return result.copy(); }

    /** Returns the yield of one operation.
     * @return items produced per operation, always at least one
     */
    public int outputPerOperation() { return result.getCount(); }

    /**
     * Returns the number of operations needed to finish a remaining quantity.
     *
     * @param remaining items still wanted, negative values treated as none
     * @return operation count, rounded up because a partial operation produces nothing
     */
    public int operationsFor(int remaining) {
        if (remaining <= 0) return 0;
        int yield = outputPerOperation();
        return (remaining + yield - 1) / yield;
    }
}
