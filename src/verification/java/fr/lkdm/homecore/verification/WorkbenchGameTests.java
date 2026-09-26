package fr.lkdm.homecore.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.registry.HomeCoreItems;
import fr.lkdm.homecore.registry.HomeCoreRecipes;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.workbench.ElectronicsBlockEntity;
import fr.lkdm.homecore.workbench.ElectronicsMenu;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live server checks of reservations, session ownership and persistent recovery. */
@GameTestHolder(HomeCoreValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkbenchGameTests {
    private record Fixture(GameTestHelper helper, ElectronicsBlockEntity block, FakePlayer player, ElectronicsMenu menu) {
        UUID sessionId() { return block.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("Assembly").getUUID("Id"); }
        RecipeHolder<ElectronicsRecipe> recipe(String name) {
            return helper.getLevel().getRecipeManager().getAllRecipesFor(HomeCoreRecipes.TYPE.get()).stream()
                    .filter(holder -> holder.id().equals(ResourceLocation.fromNamespaceAndPath("homecore", name))).findFirst().orElseThrow();
        }
        boolean start(int quantity) { return block.start(player, recipe("homelink_circuit_board"), quantity); }
        void tick() { ElectronicsBlockEntity.tick(helper.getLevel(), block.getBlockPos(), block.getBlockState(), block); }
        void validate() {
            UUID id = sessionId();
            int steps = block.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("Assembly").getInt("Steps");
            for (int part = 0; part < steps; part++) helper.assertTrue(block.place(player, id, part, part), "Correct prototype placement rejected");
            helper.assertTrue(block.phase() == 2, "Prototype did not begin batch production");
        }
        void finish() { for (int tick = 0; tick < 80; tick++) tick(); }
    }

    private static Fixture fixture(GameTestHelper helper) {
        return fixture(helper, helper.absolutePos(new BlockPos(1, 1, 1)));
    }

    private static Fixture fixture(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, HomeCoreWorkbench.BLOCK.get().defaultBlockState(), 3);
        ElectronicsBlockEntity block = (ElectronicsBlockEntity) helper.getLevel().getBlockEntity(pos);
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "WorkbenchTest"));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        ElectronicsMenu menu = new ElectronicsMenu(1, player.getInventory(), block);
        player.containerMenu = menu;
        return new Fixture(helper, block, player, menu);
    }

    private static void stockBoards(Fixture fixture, int assemblies) {
        int copper = assemblies * 4, redstone = assemblies * 4;
        fixture.block.setItem(0, new ItemStack(Items.COPPER_INGOT, Math.min(64, copper)));
        if (copper > 64) fixture.block.setItem(1, new ItemStack(Items.COPPER_INGOT, copper - 64));
        fixture.block.setItem(2, new ItemStack(Items.REDSTONE, Math.min(64, redstone)));
        if (redstone > 64) fixture.block.setItem(3, new ItemStack(Items.REDSTONE, redstone - 64));
        fixture.block.setItem(4, new ItemStack(Items.QUARTZ, assemblies));
    }

    private static int count(ElectronicsBlockEntity block, Item item) {
        int count = 0;
        for (int slot = 0; slot < block.getContainerSize(); slot++) if (block.getItem(slot).is(item)) count += block.getItem(slot).getCount();
        return count;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void batchReservationAndServerValidation(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        stockBoards(fixture, 32);
        helper.assertTrue(!fixture.start(0) && !fixture.start(3) && !fixture.start(65), "Invalid batch quantity accepted");
        fixture.block.setItem(9, new ItemStack(Items.STONE));
        helper.assertTrue(!fixture.start(64), "Incompatible output accepted");
        fixture.block.setItem(9, new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(), 1));
        helper.assertTrue(!fixture.start(64), "Output overflow accepted");
        fixture.block.setItem(9, ItemStack.EMPTY);
        helper.assertTrue(fixture.start(64), "Full Circuit Board batch rejected");
        helper.assertTrue(count(fixture.block, Items.COPPER_INGOT) == 0 && count(fixture.block, Items.REDSTONE) == 0
                && count(fixture.block, Items.QUARTZ) == 0, "Materials were not fully reserved");
        UUID session = fixture.sessionId();
        helper.assertTrue(!fixture.start(2), "Concurrent reservation accepted");
        helper.assertTrue(fixture.block.removeItem(0, 64).isEmpty(), "Reserved inventory is extractable");
        helper.assertTrue(!fixture.block.place(fixture.player, UUID.randomUUID(), 0, 0), "Stale session accepted");
        helper.assertTrue(!fixture.block.place(fixture.player, session, 0, 1), "Incorrect prototype placement accepted");
        helper.assertTrue(!fixture.block.place(fixture.player, session, -1, -1), "Invalid prototype index accepted");
        helper.assertTrue(fixture.block.place(fixture.player, session, 0, 0), "First placement rejected");
        helper.assertTrue(!fixture.block.place(fixture.player, session, 0, 0), "Replay placement accepted");
        FakePlayer intruder = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OtherTester"));
        intruder.setPos(fixture.player.getX(), fixture.player.getY(), fixture.player.getZ());
        intruder.containerMenu = new ElectronicsMenu(2, intruder.getInventory(), fixture.block);
        helper.assertTrue(!fixture.block.place(intruder, session, 1, 1), "Other player advanced reserved prototype");
        fixture.block.cancel(intruder, session);
        helper.assertTrue(fixture.block.locked(), "Other player canceled reservation");
        helper.assertTrue(fixture.block.place(fixture.player, session, 1, 1) && fixture.block.place(fixture.player, session, 2, 2), "Remaining placements rejected");
        fixture.block.cancel(fixture.player, session);
        helper.assertTrue(fixture.block.phase() == 2, "Validated production was canceled");
        fixture.finish();
        helper.assertTrue(count(fixture.block, HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()) == 64, "Batch did not produce exactly 64 boards");
        helper.assertTrue(!fixture.block.place(fixture.player, session, 2, 2), "Completed session replay accepted");
        fixture.finish();
        helper.assertTrue(count(fixture.block, HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()) == 64, "Completed batch produced twice");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cancelCloseDisconnectAndReloadConserveMaterials(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        stockBoards(fixture, 8);
        for (int scenario = 0; scenario < 4; scenario++) {
            helper.assertTrue(fixture.start(16), "Reservation rejected before recovery scenario " + scenario);
            UUID id = fixture.sessionId();
            if (scenario == 0) {
                fixture.block.cancel(fixture.player, id);
                fixture.block.cancel(fixture.player, id);
            } else if (scenario == 1) fixture.menu.removed(fixture.player);
            else if (scenario == 2) {
                // FakePlayer is deliberately absent from the connected-player roster.
                fixture.tick();
            } else {
                var saved = fixture.block.saveWithoutMetadata(helper.getLevel().registryAccess());
                fixture.block.loadWithComponents(saved, helper.getLevel().registryAccess());
                fixture.tick();
            }
            helper.assertTrue(!fixture.block.locked(), "Reservation stayed locked after recovery scenario " + scenario);
            helper.assertTrue(count(fixture.block, Items.COPPER_INGOT) == 32 && count(fixture.block, Items.REDSTONE) == 32
                    && count(fixture.block, Items.QUARTZ) == 8 && fixture.block.getItem(9).isEmpty(),
                    "Materials changed after recovery scenario " + scenario);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void productionResumesFromSavedDataAndMicroprocessorConsumesBoards(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        stockBoards(fixture, 8);
        helper.assertTrue(fixture.start(16), "Reservation rejected");
        fixture.validate();
        fixture.tick();
        var saved = fixture.block.saveWithoutMetadata(helper.getLevel().registryAccess());
        fixture.block.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(fixture.block.phase() == 2, "Validated production did not reload");
        fixture.finish();
        helper.assertTrue(fixture.block.getItem(9).getCount() == 16, "Restored batch output incorrect");
        fixture.block.clearContent();
        fixture.block.setItem(0, new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(), 16));
        fixture.block.setItem(1, new ItemStack(Items.GOLD_NUGGET, 64));
        fixture.block.setItem(2, new ItemStack(Items.COPPER_INGOT, 32));
        fixture.block.setItem(3, new ItemStack(Items.REDSTONE, 16));
        fixture.block.setItem(4, new ItemStack(Items.QUARTZ, 15));
        var recipe = fixture.recipe("homelink_microprocessor");
        helper.assertTrue(!fixture.block.start(fixture.player, recipe, 16), "Insufficient resources accepted");
        fixture.block.setItem(4, new ItemStack(Items.QUARTZ, 16));
        helper.assertTrue(fixture.block.start(fixture.player, recipe, 16), "Microprocessor reservation rejected");
        fixture.validate();
        fixture.menu.removed(fixture.player);
        fixture.finish();
        helper.assertTrue(fixture.block.getItem(9).is(HomeCoreItems.HOMELINK_MICROPROCESSOR.get())
                && fixture.block.getItem(9).getCount() == 16, "Microprocessor batch output incorrect");
        helper.assertTrue(fixture.block.materials().stream().allMatch(ItemStack::isEmpty), "Microprocessor consumption incorrect");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingDropsReservedInputsAndExistingOutputExactlyOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        stockBoards(fixture, 8);
        fixture.block.setItem(9, new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(), 2));
        helper.assertTrue(fixture.start(8), "Reservation rejected before breaking");
        BlockPos pos = fixture.block.getBlockPos();
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        fixture.block.dropContents();
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        helper.assertTrue(dropped(drops, Items.COPPER_INGOT) == 32 && dropped(drops, Items.REDSTONE) == 32
                && dropped(drops, Items.QUARTZ) == 8 && dropped(drops, HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()) == 2,
                "Breaking lost or duplicated reserved/input/output items");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    private static int dropped(java.util.List<ItemEntity> entities, Item item) {
        return entities.stream().map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static final String COMMUNICATION = "homelink_communication_module", CONTROL = "homelink_control_module";

    private static Item[] moduleMaterials(String name) {
        return name.equals(COMMUNICATION)
                ? new Item[]{Items.COPPER_INGOT, Items.REDSTONE, Items.QUARTZ, Items.AMETHYST_SHARD}
                : new Item[]{Items.COPPER_INGOT, Items.REDSTONE, Items.COMPARATOR, Items.IRON_INGOT};
    }

    private static void stockModule(Fixture fixture, String name, int assemblies) {
        Item[] materials = moduleMaterials(name);
        fixture.block.clearContent();
        fixture.block.setItem(0, new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(), assemblies));
        fixture.block.setItem(1, new ItemStack(HomeCoreItems.HOMELINK_MICROPROCESSOR.get(), assemblies));
        for (int material = 0; material < 2; material++) {
            fixture.block.setItem(2 + material * 2, new ItemStack(materials[material], Math.min(64, assemblies * 2)));
            if (assemblies > 32) fixture.block.setItem(3 + material * 2, new ItemStack(materials[material], assemblies * 2 - 64));
        }
        fixture.block.setItem(6, new ItemStack(materials[2], assemblies));
        fixture.block.setItem(7, new ItemStack(materials[3], assemblies));
        fixture.block.setItem(8, new ItemStack(Items.STONE, 5));
    }

    private static boolean moduleStock(Fixture fixture, String name, int assemblies) {
        Item[] materials = moduleMaterials(name);
        return count(fixture.block, HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()) == assemblies
                && count(fixture.block, HomeCoreItems.HOMELINK_MICROPROCESSOR.get()) == assemblies
                && count(fixture.block, materials[0]) == assemblies * 2 && count(fixture.block, materials[1]) == assemblies * 2
                && count(fixture.block, materials[2]) == assemblies && count(fixture.block, materials[3]) == assemblies
                && count(fixture.block, Items.STONE) == 5;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void communicationAndControlModulesProduceExactBatches(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        for (String name : new String[]{COMMUNICATION, CONTROL}) {
            var recipe = fixture.recipe(name);
            Item module = name.equals(COMMUNICATION) ? HomeCoreItems.HOMELINK_COMMUNICATION_MODULE.get()
                    : HomeCoreItems.HOMELINK_CONTROL_MODULE.get();
            helper.assertTrue(recipe.value().result().is(module) && recipe.value().result().getCount() == 1,
                    "Incorrect module recipe result: " + name);
            for (int quantity : new int[]{1, 8, 32, 64}) {
                stockModule(fixture, name, quantity);
                helper.assertTrue(!fixture.block.start(fixture.player, recipe, quantity + 1), "Insufficient module batch accepted: " + name);
                helper.assertTrue(fixture.block.start(fixture.player, recipe, quantity), "Module batch rejected: " + name + " x" + quantity);
                helper.assertTrue(fixture.block.materials().stream().allMatch(stack -> stack.isEmpty() || stack.is(Items.STONE)),
                        "Module materials were not fully reserved: " + name);
                UUID session = fixture.sessionId();
                var assembly = fixture.block.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("Assembly");
                helper.assertTrue(assembly.getInt("Duration") <= 80, "Batch animation exceeds four seconds: " + name);
                helper.assertTrue(!fixture.block.place(fixture.player, session, 0, 1)
                        && fixture.block.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("Assembly").getInt("Mask") == 0,
                        "Wrong module placement accepted: " + name);
                fixture.validate();
                fixture.finish();
                helper.assertTrue(fixture.block.phase() == 3 && fixture.block.getItem(9).is(module)
                        && fixture.block.getItem(9).getCount() == quantity, "Module batch output incorrect: " + name + " x" + quantity);
                helper.assertTrue(fixture.block.materials().stream().allMatch(stack -> stack.isEmpty() || stack.is(Items.STONE))
                        && count(fixture.block, Items.STONE) == 5, "Module batch consumption incorrect: " + name);
                fixture.finish();
                helper.assertTrue(fixture.block.getItem(9).getCount() == quantity, "Module batch produced twice: " + name);
                fixture.block.removeItem(9, 64);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void moduleReservationsRecoverAndRespectOutput(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        for (String name : new String[]{COMMUNICATION, CONTROL}) {
            var recipe = fixture.recipe(name);
            stockModule(fixture, name, 16);
            for (int scenario = 0; scenario < 4; scenario++) {
                helper.assertTrue(fixture.block.start(fixture.player, recipe, 16), "Module reservation rejected before scenario " + scenario);
                UUID id = fixture.sessionId();
                helper.assertTrue(fixture.block.place(fixture.player, id, 0, 0), "Correct module placement rejected");
                if (scenario == 0) fixture.block.cancel(fixture.player, id);
                else if (scenario == 1) fixture.menu.removed(fixture.player);
                else if (scenario == 2) fixture.tick(); // FakePlayer is absent from the connected-player roster.
                else {
                    var saved = fixture.block.saveWithoutMetadata(helper.getLevel().registryAccess());
                    fixture.block.loadWithComponents(saved, helper.getLevel().registryAccess());
                    fixture.tick();
                }
                helper.assertTrue(!fixture.block.locked() && moduleStock(fixture, name, 16) && fixture.block.getItem(9).isEmpty(),
                        "Module materials changed after recovery scenario " + scenario + ": " + name);
            }
            Item other = name.equals(COMMUNICATION) ? HomeCoreItems.HOMELINK_CONTROL_MODULE.get() : HomeCoreItems.HOMELINK_COMMUNICATION_MODULE.get();
            Item module = name.equals(COMMUNICATION) ? HomeCoreItems.HOMELINK_COMMUNICATION_MODULE.get() : HomeCoreItems.HOMELINK_CONTROL_MODULE.get();
            fixture.block.setItem(9, new ItemStack(other));
            helper.assertTrue(!fixture.block.start(fixture.player, recipe, 1), "Module batch accepted into a foreign output");
            fixture.block.setItem(9, new ItemStack(module, 60));
            helper.assertTrue(!fixture.block.start(fixture.player, recipe, 8), "Module batch overflowed the output");
            helper.assertTrue(moduleStock(fixture, name, 16), "Rejected module batch changed materials");
            helper.assertTrue(fixture.block.start(fixture.player, recipe, 4), "Fitting module batch rejected");
            fixture.validate();
            fixture.finish();
            helper.assertTrue(fixture.block.getItem(9).is(module) && fixture.block.getItem(9).getCount() == 64,
                    "Module output did not fill exactly: " + name);
            fixture.block.removeItem(9, 64);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void craftingRemaindersReturnOnlyAfterProduction(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var original = java.util.List.copyOf(manager.getRecipes());
        var temporary = new java.util.ArrayList<RecipeHolder<?>>(original);
        var recipe = new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(HomeCoreValidation.MOD_ID, "bucket_component"),
                new ElectronicsRecipe(java.util.List.of(new fr.lkdm.homecore.workbench.recipe.CountedIngredient(
                        net.minecraft.world.item.crafting.Ingredient.of(Items.WATER_BUCKET), 1)),
                        new ItemStack(Items.QUARTZ), java.util.List.of(new fr.lkdm.homecore.workbench.recipe.AssemblyPart(
                                "water", "item.minecraft.water_bucket", 0, 50, 50)), 20, "verification"));
        temporary.add(recipe);
        manager.replaceRecipes(temporary);
        try {
            Fixture fixture = fixture(helper);
            fixture.block.setItem(0, new ItemStack(Items.WATER_BUCKET));
            fixture.block.setItem(1, new ItemStack(Items.WATER_BUCKET));
            helper.assertTrue(fixture.block.start(fixture.player, recipe, 2), "Container ingredient recipe rejected");
            fixture.block.cancel(fixture.player, fixture.sessionId());
            helper.assertTrue(count(fixture.block, Items.WATER_BUCKET) == 2 && count(fixture.block, Items.BUCKET) == 0,
                    "Canceled recipe consumed containers or created remainders");
            helper.assertTrue(fixture.block.start(fixture.player, recipe, 2), "Container recipe restart rejected");
            fixture.validate();
            fixture.finish();
            helper.assertTrue(count(fixture.block, Items.WATER_BUCKET) == 0 && count(fixture.block, Items.BUCKET) == 2
                    && count(fixture.block, Items.QUARTZ) == 2, "Production did not preserve crafting containers");
            fixture.finish();
            helper.assertTrue(count(fixture.block, Items.BUCKET) == 2, "Crafting remainders were returned twice");
            helper.succeed();
        } finally { manager.replaceRecipes(original); }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void workbenchSurvivesActualRestart(GameTestHelper helper) {
        // A distant, unforced chunk stays outside the test structures and active player area.
        BlockPos pos = new BlockPos(-10000, 64, -10000);
        var level = helper.getLevel();
        level.getChunkAt(pos);
        String pass = System.getProperty("homecore.persistencePass", "");
        if (pass.equals("write")) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            Fixture fixture = fixture(helper, pos);
            var left = fixture.block.getBlockState().setValue(fr.lkdm.homecore.workbench.ElectronicsBlock.PART,
                    fr.lkdm.homecore.workbench.ElectronicsBlock.Part.LEFT);
            level.setBlock(pos, left, 3);
            HomeCoreWorkbench.BLOCK.get().setPlacedBy(level, pos, left, fixture.player,
                    new ItemStack(HomeCoreItems.ELECTRONICS_WORKBENCH.get()));
            stockBoards(fixture, 8);
            fixture.block.setItem(8, new ItemStack(Items.STONE, 7));
            helper.assertTrue(fixture.start(16), "Persistent production reservation rejected");
            fixture.validate();
            fixture.tick();
            level.getServer().saveEverything(false, true, true);
            com.mojang.logging.LogUtils.getLogger().info("HOMECORE_WORKBENCH_PERSISTENCE_WRITE_OK position={}", pos);
        } else if (pass.equals("read")) {
            helper.assertTrue(level.getBlockEntity(pos) instanceof ElectronicsBlockEntity,
                    "Workbench block entity was not persisted across server processes");
            ElectronicsBlockEntity block = (ElectronicsBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(level.getBlockState(pos).getValue(fr.lkdm.homecore.workbench.ElectronicsBlock.PART)
                            == fr.lkdm.homecore.workbench.ElectronicsBlock.Part.LEFT
                    && level.getBlockState(pos.east()).getValue(fr.lkdm.homecore.workbench.ElectronicsBlock.PART)
                            == fr.lkdm.homecore.workbench.ElectronicsBlock.Part.RIGHT
                    && level.getBlockEntity(pos.east()) == null
                    && fr.lkdm.homecore.workbench.ElectronicsBlock.workbench(level, pos.east()) == block,
                    "Two-block structure did not persist as a single inventory");
            helper.assertTrue(block.phase() == 2, "Saved prototype did not resume in production phase");
            helper.assertTrue(block.saveWithoutMetadata(level.registryAccess()).getCompound("Assembly").getInt("Progress") >= 1,
                    "Saved production progress was lost");
            for (int tick = 0; tick < 80; tick++) ElectronicsBlockEntity.tick(level, pos, block.getBlockState(), block);
            helper.assertTrue(block.phase() == 3 && block.getItem(9).is(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get())
                    && block.getItem(9).getCount() == 16, "Restarted production did not yield exactly 16 boards");
            helper.assertTrue(count(block, Items.COPPER_INGOT) == 0 && count(block, Items.REDSTONE) == 0
                    && count(block, Items.QUARTZ) == 0 && count(block, Items.STONE) == 7,
                    "Restart changed consumed materials or unrelated stored items");
            for (int tick = 0; tick < 80; tick++) ElectronicsBlockEntity.tick(level, pos, block.getBlockState(), block);
            helper.assertTrue(block.getItem(9).getCount() == 16, "Restarted production yielded twice");
            helper.assertTrue(level.getBlockState(pos.east()).getValue(fr.lkdm.homecore.workbench.ElectronicsBlock.STAGE) == 3,
                    "Restored right half did not reflect completed production");
            com.mojang.logging.LogUtils.getLogger().info("HOMECORE_WORKBENCH_PERSISTENCE_READ_OK position={}", pos);
        } else {
            helper.fail("Set homecore.persistencePass to write or read");
            return;
        }
        helper.succeed();
    }
}
