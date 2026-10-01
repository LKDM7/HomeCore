package fr.lkdm.homecore.workbench.recipe;

import fr.lkdm.homecore.api.recipe.RecipeDescriptor;
import fr.lkdm.homecore.api.recipe.RecipeIngredient;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Describes electronics recipes for consumers that never import the workbench itself. */
public final class ElectronicsDescriptors {
    /** Workstation every electronics recipe requires. */
    public static final ResourceLocation STATION =
            ResourceLocation.fromNamespaceAndPath("homecore", "electronics_workbench");

    private ElectronicsDescriptors() { }

    /**
     * Translates one electronics recipe into the neutral description.
     *
     * <p>Counts stay per assembly and the batch bound is the workbench's own, so a
     * consumer planning a large quantity cannot widen it by asking for more.</p>
     *
     * @param holder recipe resolved from the current server recipe manager
     * @return description, or empty for any other recipe type
     */
    public static Optional<RecipeDescriptor> describe(RecipeHolder<? extends Recipe<?>> holder) {
        if (!(holder.value() instanceof ElectronicsRecipe recipe)) return Optional.empty();
        List<RecipeIngredient> ingredients = recipe.materials().stream()
                .map(material -> new RecipeIngredient(material.ingredient(), material.count()))
                .toList();
        ItemStack result = recipe.result();
        return Optional.of(new RecipeDescriptor(holder.id(), STATION, ingredients, result,
                Math.min(64, result.getMaxStackSize()), Optional.empty()));
    }
}
