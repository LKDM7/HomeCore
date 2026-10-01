package fr.lkdm.homecore.api.recipe;

import java.util.Objects;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One material requirement for a single operation of a recipe.
 *
 * <p>The predicate is the real {@link Ingredient}, so tags and alternatives keep
 * their meaning: a requirement accepting any plank is satisfied by spruce as well
 * as oak, and a consumer must test it rather than compare display names.</p>
 *
 * @param ingredient accepted items for this requirement
 * @param count number of items consumed per operation
 */
public record RecipeIngredient(Ingredient ingredient, int count) {
    /** Largest requirement a single operation may declare. */
    public static final int MAX_COUNT = 576;

    /** Validates the requirement. */
    public RecipeIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (ingredient.isEmpty()) throw new IllegalArgumentException("A requirement must accept at least one item");
        if (count < 1 || count > MAX_COUNT) throw new IllegalArgumentException("Requirement count out of bounds: " + count);
    }
}
