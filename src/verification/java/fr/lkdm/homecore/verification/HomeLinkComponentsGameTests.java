package fr.lkdm.homecore.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.registry.HomeCoreCreativeTabs;
import fr.lkdm.homecore.registry.HomeCoreItems;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
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
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(HomeCoreCreativeTabs.HOMECORE.get())
                        .equals(ResourceLocation.fromNamespaceAndPath("homecore", "homecore")),
                "HomeCore creative tab was not registered");
        for (String name : List.of("homelink_circuit_board", "homelink_microprocessor")) {
            helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(
                            ResourceLocation.fromNamespaceAndPath("homecore", "recipes/misc/" + name)) != null,
                    "Recipe Book advancement was not loaded: " + name);
        }

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
        var recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Crafting recipe not loaded: " + name);
        var holder = recipe.orElseThrow();
        helper.assertTrue(holder.id().equals(ResourceLocation.fromNamespaceAndPath("homecore", name)),
                "Wrong recipe matched: " + name);
        ItemStack output = holder.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(output.is(result) && output.getCount() == count,
                "Incorrect crafting output: " + name);
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }
}
