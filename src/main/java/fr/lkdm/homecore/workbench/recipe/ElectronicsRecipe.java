package fr.lkdm.homecore.workbench.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lkdm.homecore.registry.HomeCoreRecipes;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Data-driven prototype recipe. Quantities always describe final output items. */
public record ElectronicsRecipe(List<CountedIngredient> materials, ItemStack result,
                                List<AssemblyPart> assemblyLayout, int processingTime,
                                String category) implements Recipe<ElectronicsInput> {
    public ElectronicsRecipe {
        materials = List.copyOf(materials);
        result = result.copy();
        assemblyLayout = List.copyOf(assemblyLayout);
        if (materials.isEmpty() || materials.size() > 9 || result.isEmpty()
                || result.getCount() > Math.min(64, result.getMaxStackSize())
                || assemblyLayout.isEmpty() || assemblyLayout.size() > 8
                || processingTime < 1 || processingTime > 80
                || category == null || category.isBlank() || category.length() > 64) {
            throw new IllegalArgumentException("Invalid electronics recipe bounds");
        }
        var ids = new HashSet<String>();
        for (AssemblyPart part : assemblyLayout) {
            if (part.ingredientIndex() >= materials.size() || !ids.add(part.id())) {
                throw new IllegalArgumentException("Assembly parts require unique IDs and valid material indices");
            }
        }
    }
    @Override public ItemStack result() { return result.copy(); }
    @Override public boolean matches(ElectronicsInput input, Level level) {
        return BatchPlanner.plan(materials, input.items(), 1).isPresent();
    }
    @Override public ItemStack assemble(ElectronicsInput input, HolderLookup.Provider registries) { return result(); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return HomeCoreRecipes.SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return HomeCoreRecipes.TYPE.get(); }
    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        materials.forEach(material -> ingredients.add(material.ingredient()));
        return ingredients;
    }

    public static final class Serializer implements RecipeSerializer<ElectronicsRecipe> {
        private record Data(List<CountedIngredient> materials, ItemStack result, List<AssemblyPart> layout,
                            int time, String category) { }
        public static final MapCodec<ElectronicsRecipe> CODEC = RecordCodecBuilder.<Data>mapCodec(instance -> instance.group(
                CountedIngredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(Data::materials),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Data::result),
                AssemblyPart.CODEC.listOf(1, 8).fieldOf("assembly_layout").forGetter(Data::layout),
                Codec.intRange(1, 80).optionalFieldOf("processing_time", 20).forGetter(Data::time),
                Codec.string(1, 64).optionalFieldOf("category", "components").forGetter(Data::category)
        ).apply(instance, Data::new)).flatXmap(data -> {
            try { return DataResult.success(new ElectronicsRecipe(data.materials(), data.result(), data.layout(), data.time(), data.category())); }
            catch (IllegalArgumentException exception) { return DataResult.error(exception::getMessage); }
        }, recipe -> DataResult.success(new Data(recipe.materials(), recipe.result(), recipe.assemblyLayout(), recipe.processingTime(), recipe.category())));

        public static final StreamCodec<RegistryFriendlyByteBuf, ElectronicsRecipe> STREAM_CODEC = new StreamCodec<>() {
            @Override public ElectronicsRecipe decode(RegistryFriendlyByteBuf buffer) {
                int materialsCount = bounded(buffer.readVarInt(), 1, 9);
                var materials = new java.util.ArrayList<CountedIngredient>(materialsCount);
                for (int i = 0; i < materialsCount; i++) {
                    materials.add(new CountedIngredient(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer), buffer.readVarInt()));
                }
                ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
                int partCount = bounded(buffer.readVarInt(), 1, 8);
                var parts = new java.util.ArrayList<AssemblyPart>(partCount);
                for (int i = 0; i < partCount; i++) {
                    parts.add(new AssemblyPart(buffer.readUtf(64), buffer.readUtf(128), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
                }
                return new ElectronicsRecipe(materials, result, parts, buffer.readVarInt(), buffer.readUtf(64));
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, ElectronicsRecipe recipe) {
                buffer.writeVarInt(recipe.materials().size());
                recipe.materials().forEach(material -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, material.ingredient());
                    buffer.writeVarInt(material.count());
                });
                ItemStack.STREAM_CODEC.encode(buffer, recipe.result());
                buffer.writeVarInt(recipe.assemblyLayout().size());
                recipe.assemblyLayout().forEach(part -> {
                    buffer.writeUtf(part.id(), 64); buffer.writeUtf(part.label(), 128);
                    buffer.writeVarInt(part.ingredientIndex()); buffer.writeVarInt(part.x()); buffer.writeVarInt(part.y());
                });
                buffer.writeVarInt(recipe.processingTime()); buffer.writeUtf(recipe.category(), 64);
            }
        };
        private static int bounded(int value, int minimum, int maximum) {
            if (value < minimum || value > maximum) throw new IllegalArgumentException("Invalid recipe collection size");
            return value;
        }
        @Override public MapCodec<ElectronicsRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ElectronicsRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
