package fr.lkdm.homecore.registry;

import fr.lkdm.homecore.HomeCore;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HomeCoreRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, HomeCore.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, HomeCore.MOD_ID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ElectronicsRecipe>> TYPE = TYPES.register("electronics", () -> new RecipeType<>() {
        @Override public String toString() { return "homecore:electronics"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, ElectronicsRecipe.Serializer> SERIALIZER =
            SERIALIZERS.register("electronics", ElectronicsRecipe.Serializer::new);
    private HomeCoreRecipes() { }
}
