package fr.lkdm.homecore.compat.rei;

import fr.lkdm.homecore.compat.ViewerInfo;
import fr.lkdm.homecore.registry.HomeCoreRecipes;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.world.item.ItemStack;

/** Optional REI integration, loaded by REI only: Electronics Workbench recipes and information pages. */
@REIPluginClient
public final class ElectronicsReiPlugin implements REIClientPlugin {
    @Override public void registerCategories(CategoryRegistry registry) {
        registry.add(new ElectronicsReiCategory());
        registry.addWorkstations(ElectronicsReiDisplay.CATEGORY, EntryStacks.of(HomeCoreWorkbench.BLOCK.get()));
    }

    @Override public void registerDisplays(DisplayRegistry registry) {
        registry.registerRecipeFiller(ElectronicsRecipe.class, HomeCoreRecipes.TYPE.get(), ElectronicsReiDisplay::new);
        ViewerInfo.pages().forEach((item, lines) -> registry.add(DefaultInformationDisplay
                .createFromEntry(EntryStacks.of(item), new ItemStack(item).getHoverName()).lines(lines)));
    }
}
