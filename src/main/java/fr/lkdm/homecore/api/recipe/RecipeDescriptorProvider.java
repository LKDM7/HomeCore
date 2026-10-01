package fr.lkdm.homecore.api.recipe;

import java.util.Optional;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Translates a recipe a mod owns into the neutral description consumers read. */
@FunctionalInterface
public interface RecipeDescriptorProvider {
    /**
     * Describes a recipe of the type this provider was registered for.
     *
     * @param holder recipe resolved from the current server recipe manager
     * @return description, or empty when this recipe cannot be described safely
     */
    Optional<RecipeDescriptor> describe(RecipeHolder<? extends Recipe<?>> holder);
}
