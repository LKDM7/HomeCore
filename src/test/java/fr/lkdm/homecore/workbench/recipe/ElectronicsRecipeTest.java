package fr.lkdm.homecore.workbench.recipe;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElectronicsRecipeTest {
    private static ElectronicsRecipe example() {
        return new ElectronicsRecipe(List.of(new CountedIngredient(Ingredient.of(Items.COPPER_INGOT), 4)),
                new ItemStack(Items.QUARTZ, 2), List.of(new AssemblyPart("copper", "test.copper", 0, 50, 50)), 20, "components");
    }

    @Test void jsonAndStreamRoundTripsPreserveMaterialCountsAndLayout() {
        var recipe = example();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var codec = ElectronicsRecipe.Serializer.CODEC.codec();
        var json = codec.encodeStart(ops, recipe).getOrThrow();
        var decoded = codec.parse(ops, json).getOrThrow();
        assertRecipe(decoded);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        try {
            ElectronicsRecipe.Serializer.STREAM_CODEC.encode(buffer, recipe);
            assertRecipe(ElectronicsRecipe.Serializer.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void codecsRejectOversizedListsAndCrossFieldErrors() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var codec = ElectronicsRecipe.Serializer.CODEC.codec();
        var json = codec.encodeStart(ops, example()).getOrThrow().getAsJsonObject();
        json.getAsJsonArray("assembly_layout").get(0).getAsJsonObject().addProperty("ingredient_index", 2);
        assertTrue(codec.parse(ops, json).error().isPresent());
        json.add("assembly_layout", JsonParser.parseString("[]"));
        assertTrue(codec.parse(ops, json).error().isPresent());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        try {
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> ElectronicsRecipe.Serializer.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void recipeOwnsItsResultAndRejectsInvalidParts() {
        var recipe = example();
        recipe.result().setCount(64);
        assertEquals(2, recipe.result().getCount());
        assertThrows(IllegalArgumentException.class, () -> new ElectronicsRecipe(recipe.materials(), recipe.result(),
                List.of(new AssemblyPart("same", "test.label", 0, 20, 20), new AssemblyPart("same", "test.label", 0, 80, 80)), 20, "components"));
    }

    private static void assertRecipe(ElectronicsRecipe recipe) {
        assertEquals(4, recipe.materials().getFirst().count());
        assertTrue(recipe.materials().getFirst().ingredient().test(new ItemStack(Items.COPPER_INGOT)));
        assertTrue(recipe.result().is(Items.QUARTZ));
        assertEquals(2, recipe.result().getCount());
        assertEquals(example().assemblyLayout(), recipe.assemblyLayout());
        assertEquals(20, recipe.processingTime());
    }
}
