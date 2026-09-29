package fr.lkdm.homecore.compat.jei;

import static fr.lkdm.homecore.compat.ElectronicsRecipeViews.*;

import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

final class ElectronicsJeiCategory extends AbstractRecipeCategory<RecipeHolder<ElectronicsRecipe>> {
    ElectronicsJeiCategory(IGuiHelper guiHelper) {
        super(ElectronicsJeiPlugin.ELECTRONICS,
                Component.translatable(HomeCoreWorkbench.BLOCK.get().getDescriptionId()),
                guiHelper.createDrawableItemStack(new ItemStack(HomeCoreWorkbench.BLOCK.get())),
                WIDTH, HEIGHT);
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ElectronicsRecipe> holder, IFocusGroup focuses) {
        ElectronicsRecipe recipe = holder.value();
        for (int i = 0; i < recipe.materials().size(); i++) {
            builder.addInputSlot(slotX(i) + 1, slotY(i) + 1)
                    .setStandardSlotBackground()
                    .addItemStacks(stacks(recipe.materials().get(i)));
        }
        builder.addOutputSlot(OUTPUT_X + 1, (HEIGHT - SLOT) / 2 + 1)
                .setOutputSlotBackground()
                .addItemStacks(List.of(recipe.result()));
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<ElectronicsRecipe> holder, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(ARROW_X, (HEIGHT - 17) / 2);
    }
}
