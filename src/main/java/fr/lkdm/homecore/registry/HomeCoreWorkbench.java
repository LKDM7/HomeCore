package fr.lkdm.homecore.registry;

import fr.lkdm.homecore.HomeCore;
import fr.lkdm.homecore.workbench.ElectronicsBlock;
import fr.lkdm.homecore.workbench.ElectronicsBlockEntity;
import fr.lkdm.homecore.workbench.ElectronicsMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Vanilla registries used by the electronics workbench. */
public final class HomeCoreWorkbench {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HomeCore.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HomeCore.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, HomeCore.MOD_ID);

    public static final DeferredBlock<ElectronicsBlock> BLOCK = BLOCKS.register("electronics_workbench",
            () -> new ElectronicsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(ElectronicsBlock.STAGE) >= 2 ? 3 : 0)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectronicsBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("electronics_workbench",
                    () -> BlockEntityType.Builder.of(ElectronicsBlockEntity::new, BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ElectronicsMenu>> MENU =
            MENUS.register("electronics_workbench", () -> IMenuTypeExtension.create(ElectronicsMenu::new));

    private HomeCoreWorkbench() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
