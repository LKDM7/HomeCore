package fr.lkdm.homecore.workbench.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchPlannerTest {
    private static CountedIngredient copper(int count) {
        return new CountedIngredient(Ingredient.of(Items.COPPER_INGOT), count);
    }
    private static ElectronicsRecipe recipe(ItemStack result) {
        return new ElectronicsRecipe(List.of(copper(4)), result,
                List.of(new AssemblyPart("copper", "test.copper", 0, 50, 50)), 20, "components");
    }

    @Test void reroutesBroadIngredientToPreserveNarrowIngredient() {
        var materials = List.of(new CountedIngredient(Ingredient.of(Items.COPPER_INGOT, Items.GOLD_INGOT), 1), copper(1));
        var inventory = List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.GOLD_INGOT));
        assertArrayEquals(new int[] {1, 1}, BatchPlanner.plan(materials, inventory, 1).orElseThrow());
        assertEquals(1, inventory.getFirst().getCount());
        assertTrue(BatchPlanner.plan(materials, List.of(new ItemStack(Items.COPPER_INGOT)), 1).isEmpty());
    }

    @Test void combinesSplitStacksAndRejectsIncompleteBatch() {
        var inventory = List.of(new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(Items.COPPER_INGOT, 64));
        assertArrayEquals(new int[] {64, 64}, BatchPlanner.plan(List.of(copper(4)), inventory, 32).orElseThrow());
        assertTrue(BatchPlanner.plan(List.of(copper(4)), inventory, 33).isEmpty());
        assertTrue(BatchPlanner.plan(List.of(copper(4)), inventory, 0).isEmpty());
        assertEquals(64, inventory.getFirst().getCount());
    }

    @Test void finalQuantityRespectsYieldInventoryAndOutputSpace() {
        var recipe = recipe(new ItemStack(Items.QUARTZ, 2));
        assertEquals(10, BatchPlanner.maxOutput(recipe, List.of(new ItemStack(Items.COPPER_INGOT, 23)), ItemStack.EMPTY));
        assertEquals(2, BatchPlanner.maxOutput(recipe, List.of(new ItemStack(Items.COPPER_INGOT, 64)), new ItemStack(Items.QUARTZ, 61)));
        assertEquals(0, BatchPlanner.maxOutput(recipe, List.of(new ItemStack(Items.COPPER_INGOT, 64)), new ItemStack(Items.REDSTONE)));
        assertEquals(0, BatchPlanner.maxOutput(recipe, List.of(new ItemStack(Items.COPPER_INGOT, 64)), new ItemStack(Items.QUARTZ, 64)));
        assertEquals(64, BatchPlanner.maxOutput(recipe, List.of(new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(Items.COPPER_INGOT, 64)), ItemStack.EMPTY));
    }

    @Test void futureOutputsRespectSmallerStackSizesAndGenericYields() {
        var input = List.of(new ItemStack(Items.COPPER_INGOT, 64));
        assertEquals(1, BatchPlanner.maxOutput(recipe(new ItemStack(Items.IRON_PICKAXE)), input, ItemStack.EMPTY));
        assertEquals(15, BatchPlanner.maxOutput(recipe(new ItemStack(Items.ENDER_PEARL, 3)), input, ItemStack.EMPTY));
    }
}
