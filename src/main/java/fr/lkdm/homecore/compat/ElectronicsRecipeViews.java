package fr.lkdm.homecore.compat;

import fr.lkdm.homecore.workbench.recipe.CountedIngredient;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Loader-neutral helpers shared by the optional JEI and REI integrations. */
public final class ElectronicsRecipeViews {
    public static final int SLOT = 18, COLUMNS = 3, GRID = SLOT * COLUMNS;
    public static final int ARROW_X = GRID + 6, OUTPUT_X = GRID + 36, WIDTH = OUTPUT_X + SLOT, HEIGHT = GRID;

    private ElectronicsRecipeViews() { }

    /** Every accepted item for a material, carrying the quantity needed for one assembly. */
    public static List<ItemStack> stacks(CountedIngredient material) {
        return Arrays.stream(material.ingredient().getItems())
                .map(stack -> stack.copyWithCount(material.count()))
                .toList();
    }

    public static int slotX(int index) { return (index % COLUMNS) * SLOT; }

    public static int slotY(int index) { return (index / COLUMNS) * SLOT; }
}
