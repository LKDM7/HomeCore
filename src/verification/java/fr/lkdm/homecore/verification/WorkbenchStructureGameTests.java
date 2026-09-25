package fr.lkdm.homecore.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.registry.HomeCoreItems;
import fr.lkdm.homecore.registry.HomeCoreRecipes;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.workbench.ElectronicsBlock;
import fr.lkdm.homecore.workbench.ElectronicsBlockEntity;
import fr.lkdm.homecore.workbench.ElectronicsMenu;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HomeCoreValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkbenchStructureGameTests {
    private static FakePlayer player(GameTestHelper helper, BlockPos pos, Direction facing) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StructureTest"));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
        player.setYRot(facing.getOpposite().toYRot());
        return player;
    }
    private static BlockPlaceContext context(FakePlayer player, BlockPos pos, ItemStack stack) {
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
    private static void clear(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos.relative(facing.getClockWise()), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
    }
    private static ElectronicsBlockEntity place(GameTestHelper helper, BlockPos pos, Direction facing, FakePlayer player) {
        clear(helper, pos, facing);
        ItemStack item = new ItemStack(HomeCoreItems.ELECTRONICS_WORKBENCH.get());
        var result = HomeCoreItems.ELECTRONICS_WORKBENCH.get().place(context(player, pos, item));
        helper.assertTrue(result.consumesAction() && item.isEmpty(), "Placement did not consume exactly one workbench");
        var left = helper.getLevel().getBlockState(pos);
        var rightPos = pos.relative(facing.getClockWise());
        var right = helper.getLevel().getBlockState(rightPos);
        helper.assertTrue(left.is(HomeCoreWorkbench.BLOCK.get()) && right.is(HomeCoreWorkbench.BLOCK.get())
                && left.getValue(ElectronicsBlock.PART) == ElectronicsBlock.Part.LEFT
                && right.getValue(ElectronicsBlock.PART) == ElectronicsBlock.Part.RIGHT
                && left.getValue(ElectronicsBlock.FACING) == facing && right.getValue(ElectronicsBlock.FACING) == facing,
                "Placement did not create a correctly oriented matching pair");
        ElectronicsBlockEntity block = ElectronicsBlock.workbench(helper.getLevel(), pos);
        helper.assertTrue(block != null && ElectronicsBlock.workbench(helper.getLevel(), rightPos) == block
                && helper.getLevel().getBlockEntity(rightPos) == null, "Halves do not resolve to one inventory");
        return block;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placementAllDirectionsAndObstruction(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            FakePlayer player = player(helper, pos, facing);
            ElectronicsBlockEntity block = place(helper, pos, facing, player);
            block.setItem(9, new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()));
            helper.assertTrue(helper.getLevel().getBlockState(pos.relative(facing.getClockWise())).getValue(ElectronicsBlock.STAGE) == 3,
                    "Production state did not propagate to secondary half");
            clear(helper, pos, facing);
            BlockPos right = pos.relative(facing.getClockWise());
            helper.getLevel().setBlock(right, Blocks.STONE.defaultBlockState(), 3);
            ItemStack stack = new ItemStack(HomeCoreItems.ELECTRONICS_WORKBENCH.get());
            helper.assertTrue(!HomeCoreItems.ELECTRONICS_WORKBENCH.get().place(context(player, pos, stack)).consumesAction()
                    && stack.getCount() == 1 && helper.getLevel().getBlockState(pos).isAir()
                    && helper.getLevel().getBlockState(right).is(Blocks.STONE), "Obstructed placement overwrote neighbor or consumed item");
            helper.getLevel().setBlock(right, Blocks.AIR.defaultBlockState(), 3);
        }
        FakePlayer player = player(helper, pos, Direction.NORTH);
        var entity = new net.minecraft.world.entity.decoration.ArmorStand(helper.getLevel(),
                pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
        helper.getLevel().addFreshEntity(entity);
        try {
            ItemStack stack = new ItemStack(HomeCoreItems.ELECTRONICS_WORKBENCH.get());
            helper.assertTrue(HomeCoreWorkbench.BLOCK.get().getStateForPlacement(context(player, pos, stack)) == null,
                    "Second half placement ignored entity collision");
        } finally { entity.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eitherHalfBreaksWithOneDropAndNoLostReservation(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        Direction facing = Direction.NORTH;
        for (boolean creative : List.of(false, true)) for (boolean secondary : List.of(false, true)) {
            FakePlayer player = player(helper, pos, facing);
            ElectronicsBlockEntity block = place(helper, pos, facing, player);
            stockAndReserve(helper, player, block);
            player.gameMode.changeGameModeForPlayer(creative ? GameType.CREATIVE : GameType.SURVIVAL);
            helper.assertTrue(player.gameMode.destroyBlock(secondary ? pos.east() : pos), "Player could not break workbench half");
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir() && helper.getLevel().getBlockState(pos.east()).isAir(),
                    "Destruction left an orphaned half");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
            helper.assertTrue(count(drops, HomeCoreItems.ELECTRONICS_WORKBENCH.get()) == (creative ? 0 : 1),
                    "Workbench block drop count incorrect for creative=" + creative + ", secondary=" + secondary);
            assertMaterials(helper, drops, "player creative=" + creative + ", secondary=" + secondary);
            drops.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void explosionPreservesInventoryWithoutDuplicateMachine(GameTestHelper helper) {
        // Stay in the tracked test chunk so entity queries include newly spawned drops.
        // Height keeps a radius-four blast clear of the structures below.
        BlockPos pos = helper.absolutePos(new BlockPos(1, 12, 1));
        for (boolean secondary : List.of(false, true)) {
            FakePlayer player = player(helper, pos, Direction.NORTH);
            ElectronicsBlockEntity block = place(helper, pos, Direction.NORTH, player);
            stockAndReserve(helper, player, block);
            BlockPos center = secondary ? pos.east() : pos;
            helper.getLevel().explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                    4.0F, Level.ExplosionInteraction.BLOCK);
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir() && helper.getLevel().getBlockState(pos.east()).isAir(),
                    "Explosion left a workbench half");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
            helper.assertTrue(count(drops, HomeCoreItems.ELECTRONICS_WORKBENCH.get()) <= 1, "Explosion duplicated workbench loot");
            assertMaterials(helper, drops, "explosion secondary=" + secondary);
            drops.forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyStateAndNearbyIndependentInventoryRemainIntact(GameTestHelper helper) {
        CompoundTag stateTag = new CompoundTag(), properties = new CompoundTag();
        stateTag.putString("Name", "homecore:electronics_workbench");
        properties.putString("facing", "north"); properties.putString("stage", "0");
        stateTag.put("Properties", properties);
        var legacy = NbtUtils.readBlockState(helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK), stateTag);
        helper.assertTrue(legacy.getValue(ElectronicsBlock.PART) == ElectronicsBlock.Part.SINGLE,
                "Old blockstate without part was not migrated to compact legacy state");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(pos, legacy, 3);
        helper.getLevel().setBlock(pos.east(), legacy, 3);
        ElectronicsBlockEntity independent = ElectronicsBlock.workbench(helper.getLevel(), pos.east());
        independent.setItem(0, new ItemStack(Items.DIAMOND, 11));
        var saved = independent.saveWithoutMetadata(helper.getLevel().registryAccess());
        independent.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.getLevel().destroyBlock(pos, true);
        helper.assertTrue(ElectronicsBlock.workbench(helper.getLevel(), pos.east()) == independent
                && independent.getItem(0).is(Items.DIAMOND) && independent.getItem(0).getCount() == 11,
                "Legacy neighboring inventory was destroyed or changed");
        helper.getLevel().setBlock(pos, legacy.setValue(ElectronicsBlock.PART, ElectronicsBlock.Part.LEFT), 3);
        helper.getLevel().destroyBlock(pos, true);
        helper.assertTrue(ElectronicsBlock.workbench(helper.getLevel(), pos.east()) == independent,
                "Breaking half treated adjacent legacy workbench as partner");
        independent.clearContent();
        helper.getLevel().setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, legacy.setValue(ElectronicsBlock.PART, ElectronicsBlock.Part.LEFT), 3);
        helper.getLevel().setBlock(pos.east(), legacy.setValue(ElectronicsBlock.PART, ElectronicsBlock.Part.RIGHT)
                .setValue(ElectronicsBlock.FACING, Direction.SOUTH), 3);
        helper.getLevel().destroyBlock(pos, true);
        helper.assertTrue(helper.getLevel().getBlockState(pos.east()).is(HomeCoreWorkbench.BLOCK.get()),
                "Breaking half removed mismatched-facing neighbor");
        helper.succeed();
    }

    private static void stockAndReserve(GameTestHelper helper, FakePlayer player, ElectronicsBlockEntity block) {
        block.setItem(0, new ItemStack(Items.COPPER_INGOT, 8));
        block.setItem(1, new ItemStack(Items.REDSTONE, 8));
        block.setItem(2, new ItemStack(Items.QUARTZ, 2));
        block.setItem(9, new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(), 2));
        player.containerMenu = new ElectronicsMenu(1, player.getInventory(), block);
        var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(HomeCoreRecipes.TYPE.get()).stream()
                .filter(holder -> holder.value().result().is(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get())).findFirst().orElseThrow();
        helper.assertTrue(block.start(player, recipe, 2), "Could not reserve before destruction");
    }
    private static int count(List<ItemEntity> entities, Item item) {
        return entities.stream().map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void assertMaterials(GameTestHelper helper, List<ItemEntity> drops, String scenario) {
        helper.assertTrue(count(drops, Items.COPPER_INGOT) == 8 && count(drops, Items.REDSTONE) == 8
                && count(drops, Items.QUARTZ) == 2 && count(drops, HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()) == 2,
                "Destruction lost or duplicated items (" + scenario + "): copper=" + count(drops, Items.COPPER_INGOT)
                        + ", redstone=" + count(drops, Items.REDSTONE) + ", quartz=" + count(drops, Items.QUARTZ)
                        + ", boards=" + count(drops, HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get())
                        + ", total entities=" + drops.size());
    }
}
