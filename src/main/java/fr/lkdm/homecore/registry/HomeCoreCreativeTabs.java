package fr.lkdm.homecore.registry;

import fr.lkdm.homecore.HomeCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The single creative inventory tab for HomeCore items. */
public final class HomeCoreCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HomeCore.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HOMECORE = TABS.register(
            "homecore", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.homecore.homecore"))
                    .icon(() -> new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get());
                        output.accept(HomeCoreItems.HOMELINK_MICROPROCESSOR.get());
                    })
                    .build());

    private HomeCoreCreativeTabs() {
    }
}
