package fr.lkdm.homecore.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.registry.HomeCoreCreativeTabs;
import fr.lkdm.homecore.registry.HomeCoreItems;
import fr.lkdm.homecore.registry.HomeCoreRecipes;
import fr.lkdm.homecore.workbench.recipe.ElectronicsInput;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the registered components against Minecraft's live crafting system. */
@GameTestHolder(HomeCoreValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeLinkComponentsGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void componentsAndRecipes(GameTestHelper helper) {
        checkItem(helper, HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(),
                "homelink_circuit_board");
        checkItem(helper, HomeCoreItems.HOMELINK_MICROPROCESSOR.get(),
                "homelink_microprocessor");
        checkItem(helper, HomeCoreItems.HOMELINK_COMMUNICATION_MODULE.get(),
                "homelink_communication_module");
        checkItem(helper, HomeCoreItems.HOMELINK_CONTROL_MODULE.get(),
                "homelink_control_module");
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(HomeCoreCreativeTabs.HOMECORE.get())
                        .equals(ResourceLocation.fromNamespaceAndPath("homecore", "homecore")),
                "HomeCore creative tab was not registered");
        checkRecipe(helper, "homelink_circuit_board", List.of(
                stack(Items.COPPER_INGOT), stack(Items.REDSTONE), stack(Items.COPPER_INGOT),
                stack(Items.REDSTONE), stack(Items.QUARTZ), stack(Items.REDSTONE),
                stack(Items.COPPER_INGOT), stack(Items.REDSTONE), stack(Items.COPPER_INGOT)),
                HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(), 2);
        checkRecipe(helper, "homelink_microprocessor", List.of(
                stack(Items.GOLD_NUGGET), stack(Items.REDSTONE), stack(Items.GOLD_NUGGET),
                stack(Items.COPPER_INGOT), stack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()),
                stack(Items.COPPER_INGOT), stack(Items.GOLD_NUGGET), stack(Items.QUARTZ),
                stack(Items.GOLD_NUGGET)), HomeCoreItems.HOMELINK_MICROPROCESSOR.get(), 1);
        checkRecipe(helper, "homelink_communication_module", List.of(
                stack(Items.COPPER_INGOT), stack(Items.AMETHYST_SHARD), stack(Items.COPPER_INGOT),
                stack(Items.REDSTONE), stack(HomeCoreItems.HOMELINK_MICROPROCESSOR.get()), stack(Items.REDSTONE),
                stack(Items.QUARTZ), stack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()), ItemStack.EMPTY),
                HomeCoreItems.HOMELINK_COMMUNICATION_MODULE.get(), 1);
        checkRecipe(helper, "homelink_control_module", List.of(
                stack(Items.COPPER_INGOT), stack(HomeCoreItems.HOMELINK_MICROPROCESSOR.get()), stack(Items.COPPER_INGOT),
                stack(Items.REDSTONE), stack(Items.COMPARATOR), stack(Items.REDSTONE),
                stack(Items.IRON_INGOT), stack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()), ItemStack.EMPTY),
                HomeCoreItems.HOMELINK_CONTROL_MODULE.get(), 1);
        checkExactMaterials(helper, "homelink_communication_module", List.of(
                stack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()), stack(HomeCoreItems.HOMELINK_MICROPROCESSOR.get()),
                new ItemStack(Items.COPPER_INGOT, 2), new ItemStack(Items.REDSTONE, 2),
                stack(Items.QUARTZ), stack(Items.AMETHYST_SHARD)));
        checkExactMaterials(helper, "homelink_control_module", List.of(
                stack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()), stack(HomeCoreItems.HOMELINK_MICROPROCESSOR.get()),
                stack(Items.COMPARATOR), new ItemStack(Items.COPPER_INGOT, 2), new ItemStack(Items.REDSTONE, 2),
                stack(Items.IRON_INGOT)));
        var tab = HomeCoreCreativeTabs.HOMECORE.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(helper.getLevel().enabledFeatures(), false,
                helper.getLevel().registryAccess()));
        var tabItems = tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
        helper.assertTrue(tabItems.size() >= 4 && tabItems.subList(0, 4).equals(List.of(
                        HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(), HomeCoreItems.HOMELINK_MICROPROCESSOR.get(),
                        HomeCoreItems.HOMELINK_COMMUNICATION_MODULE.get(), HomeCoreItems.HOMELINK_CONTROL_MODULE.get())),
                "Creative tab component order changed");

        LogUtils.getLogger().info("HOMECORE_HOMELINK_COMPONENTS_OK");
        helper.succeed();
    }

    private static void checkItem(GameTestHelper helper, Item item, String name) {
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(item)
                        .equals(ResourceLocation.fromNamespaceAndPath("homecore", name)),
                "Incorrect item registry ID: " + name);
        helper.assertTrue(stack.getMaxStackSize() == 64, "Incorrect stack size: " + name);
        helper.assertTrue(stack.getRarity() == Rarity.COMMON, "Incorrect rarity: " + name);
        helper.assertTrue(!stack.isDamageableItem(), "Crafting component has durability: " + name);
        helper.assertTrue(!stack.isEnchantable(), "Crafting component is enchantable: " + name);
        helper.assertTrue(stack.get(DataComponents.LORE) != null
                        && stack.get(DataComponents.LORE).lines().size() == 1
                        && stack.get(DataComponents.LORE).lines().getFirst().getContents()
                                instanceof TranslatableContents translated
                        && translated.getKey().equals("item.homecore." + name + ".tooltip"),
                "Missing translated tooltip: " + name);
    }

    private static void checkRecipe(GameTestHelper helper, String name, List<ItemStack> ingredients,
                                    Item result, int count) {
        CraftingInput input = CraftingInput.of(3, 3, ingredients);
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isEmpty(),
                "Component remains available in vanilla crafting: " + name);
        ElectronicsInput electronics = new ElectronicsInput(ingredients);
        var recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(HomeCoreRecipes.TYPE.get(), electronics, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Electronics recipe not loaded: " + name);
        var holder = recipe.orElseThrow();
        helper.assertTrue(holder.id().equals(ResourceLocation.fromNamespaceAndPath("homecore", name)),
                "Wrong recipe matched: " + name);
        ItemStack output = holder.value().assemble(electronics, helper.getLevel().registryAccess());
        helper.assertTrue(output.is(result) && output.getCount() == count,
                "Incorrect crafting output: " + name);
    }

    /** Each listed stack is required as a whole; removing one unit must make the recipe fail. */
    private static void checkExactMaterials(GameTestHelper helper, String name, List<ItemStack> materials) {
        var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(HomeCoreRecipes.TYPE.get()).stream()
                .filter(holder -> holder.id().equals(ResourceLocation.fromNamespaceAndPath("homecore", name)))
                .findFirst().orElseThrow().value();
        helper.assertTrue(recipe.materials().size() == materials.size(), "Unexpected material count: " + name);
        helper.assertTrue(recipe.matches(new ElectronicsInput(materials), helper.getLevel()), "Exact materials rejected: " + name);
        for (int index = 0; index < materials.size(); index++) {
            var reduced = new java.util.ArrayList<>(materials.stream().map(ItemStack::copy).toList());
            reduced.get(index).shrink(1);
            helper.assertTrue(!recipe.matches(new ElectronicsInput(reduced), helper.getLevel()),
                    "Recipe accepted a missing " + materials.get(index).getItem() + ": " + name);
        }
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }
}
