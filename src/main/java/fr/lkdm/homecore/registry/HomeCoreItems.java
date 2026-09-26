package fr.lkdm.homecore.registry;

import fr.lkdm.homecore.HomeCore;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Crafting components shared by HomeLink mods. */
public final class HomeCoreItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HomeCore.MOD_ID);

    public static final DeferredItem<Item> HOMELINK_CIRCUIT_BOARD = ITEMS.registerSimpleItem(
            "homelink_circuit_board", component("homelink_circuit_board"));
    public static final DeferredItem<Item> HOMELINK_MICROPROCESSOR = ITEMS.registerSimpleItem(
            "homelink_microprocessor", component("homelink_microprocessor"));
    public static final DeferredItem<Item> HOMELINK_COMMUNICATION_MODULE = ITEMS.registerSimpleItem(
            "homelink_communication_module", component("homelink_communication_module"));
    public static final DeferredItem<Item> HOMELINK_CONTROL_MODULE = ITEMS.registerSimpleItem(
            "homelink_control_module", component("homelink_control_module"));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ELECTRONICS_WORKBENCH =
            ITEMS.registerSimpleBlockItem("electronics_workbench", HomeCoreWorkbench.BLOCK);

    private HomeCoreItems() {
    }

    private static Item.Properties component(String name) {
        return new Item.Properties()
                .stacksTo(64)
                .rarity(Rarity.COMMON)
                .component(DataComponents.LORE, new ItemLore(List.of(
                        Component.translatable("item.homecore." + name + ".tooltip")
                                .withStyle(ChatFormatting.GRAY))));
    }
}
