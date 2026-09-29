package fr.lkdm.homecore.compat.rei;

import fr.lkdm.homecore.HomeCore;
import fr.lkdm.homecore.compat.ElectronicsRecipeViews;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.world.item.crafting.RecipeHolder;

final class ElectronicsReiDisplay extends BasicDisplay {
    static final CategoryIdentifier<ElectronicsReiDisplay> CATEGORY = CategoryIdentifier.of(HomeCore.MOD_ID, "electronics");

    ElectronicsReiDisplay(RecipeHolder<ElectronicsRecipe> holder) {
        super(holder.value().materials().stream()
                        .map(material -> EntryIngredients.ofItemStacks(ElectronicsRecipeViews.stacks(material)))
                        .toList(),
                List.of(EntryIngredients.of(holder.value().result())),
                Optional.of(holder.id()));
    }

    @Override public CategoryIdentifier<?> getCategoryIdentifier() { return CATEGORY; }
}
