package fr.lkdm.homecore.compat.jei;

import fr.lkdm.homecore.HomeCore;
import fr.lkdm.homecore.compat.ViewerInfo;
import fr.lkdm.homecore.registry.HomeCoreRecipes;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Optional JEI integration, loaded by JEI only: Electronics Workbench recipes and information pages. */
@JeiPlugin
public final class ElectronicsJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HomeCore.MOD_ID, "jei");
    static final RecipeType<RecipeHolder<ElectronicsRecipe>> ELECTRONICS =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(HomeCore.MOD_ID, "electronics"));

    @Override public ResourceLocation getPluginUid() { return ID; }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ElectronicsJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        ViewerInfo.pages().forEach((item, lines) -> registration.addItemStackInfo(new ItemStack(item), lines.toArray(Component[]::new)));
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        registration.addRecipes(ELECTRONICS, level.getRecipeManager().getAllRecipesFor(HomeCoreRecipes.TYPE.get()));
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(HomeCoreWorkbench.BLOCK.get(), ELECTRONICS);
    }
}
