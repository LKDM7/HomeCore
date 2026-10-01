package fr.lkdm.homecore.api.recipe;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeDescriptorTest {
    private static final ResourceLocation ID = ResourceLocation.parse("homecore:circuit_board");
    private static final ResourceLocation STATION = ResourceLocation.parse("homecore:electronics_workbench");

    private static RecipeDescriptor descriptor(int yield) {
        return new RecipeDescriptor(ID, STATION,
                List.of(new RecipeIngredient(Ingredient.of(Items.COPPER_INGOT), 3)),
                new ItemStack(Items.IRON_INGOT, yield), 64, Optional.empty());
    }

    @Test void tenRemainingWithAYieldOfFourNeedsThreeOperations() {
        var recipe = descriptor(4);
        assertEquals(4, recipe.outputPerOperation());
        assertEquals(3, recipe.operationsFor(10));
        assertEquals(12, recipe.operationsFor(10) * recipe.outputPerOperation(), "two items are surplus");
    }

    @Test void afinishedObjectiveNeedsNoFurtherOperation() {
        var recipe = descriptor(4);
        assertEquals(0, recipe.operationsFor(0));
        assertEquals(0, recipe.operationsFor(-5));
        assertEquals(1, recipe.operationsFor(1));
    }

    @Test void aYieldOfOneStillCountsFinishedItems() {
        assertEquals(64, descriptor(1).operationsFor(64));
    }

    @Test void theResultIsCopiedSoConsumersCannotMutateIt() {
        var recipe = descriptor(4);
        recipe.result().setCount(99);
        assertEquals(4, recipe.outputPerOperation());
    }

    @Test void aBatchMustFitAtLeastOneOperation() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeDescriptor(ID, STATION,
                List.of(new RecipeIngredient(Ingredient.of(Items.COPPER_INGOT), 1)),
                new ItemStack(Items.IRON_INGOT, 4), 2, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RecipeDescriptor(ID, STATION, List.of(),
                new ItemStack(Items.IRON_INGOT), 1, Optional.empty()));
    }

    @Test void requirementsKeepTheirRealPredicate() {
        var planks = new RecipeIngredient(Ingredient.of(Items.SPRUCE_PLANKS, Items.OAK_PLANKS), 2);
        assertTrue(planks.ingredient().test(new ItemStack(Items.SPRUCE_PLANKS)));
        assertTrue(planks.ingredient().test(new ItemStack(Items.OAK_PLANKS)));
        assertFalse(planks.ingredient().test(new ItemStack(Items.STONE)));
        assertThrows(IllegalArgumentException.class, () -> new RecipeIngredient(Ingredient.of(Items.STONE), 0));
    }

    @Test void aShapedGridMustMatchItsSizeAndRequirements() {
        var grid = new RecipeGrid(2, 2, List.of(0, -1, -1, 0));
        assertEquals(4, grid.slots().size());
        var shaped = new RecipeDescriptor(ID, ResourceLocation.parse("minecraft:crafting_table"),
                List.of(new RecipeIngredient(Ingredient.of(Items.STICK), 1)),
                new ItemStack(Items.TORCH, 4), 64, Optional.of(grid));
        assertTrue(shaped.grid().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new RecipeGrid(2, 2, List.of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new RecipeDescriptor(ID, STATION,
                List.of(new RecipeIngredient(Ingredient.of(Items.STICK), 1)), new ItemStack(Items.TORCH, 4), 64,
                Optional.of(new RecipeGrid(1, 1, List.of(3)))));
    }
}
