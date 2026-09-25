package fr.lkdm.homecore.workbench.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record ElectronicsInput(List<ItemStack> items) implements RecipeInput {
    public ElectronicsInput { items = List.copyOf(items); }
    @Override public ItemStack getItem(int index) { return items.get(index); }
    @Override public int size() { return items.size(); }
}
