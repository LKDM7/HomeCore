package fr.lkdm.homecore.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.registry.HomeCoreItems;
import fr.lkdm.homecore.workbench.ElectronicsBlockEntity;
import fr.lkdm.homecore.workbench.client.ElectronicsScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Connected inventory, input-handler and framebuffer verification, excluded from releases. */
@EventBusSubscriber(modid = HomeCoreValidation.MOD_ID, value = Dist.CLIENT)
public final class WorkbenchClientSmoke {
    private static int stage, age, part, cycle;
    private static long started;
    private static volatile Throwable serverFailure;
    private static boolean productionCaptured;

    private WorkbenchClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homecore.workbenchSmoke") || !Boolean.getBoolean("homecore.networkSmoke") || stage == 9) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (client.player == null || client.getSingleplayerServer() == null) return;
                started = System.nanoTime();
                stage = 1;
                var playerId = client.player.getUUID();
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(playerId);
                        if (player == null) throw new IllegalStateException("Workbench fixture player missing");
                        var crafting = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, java.util.List.of(
                                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT),
                                new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.COPPER_INGOT),
                                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS), new ItemStack(Items.SPRUCE_PLANKS)));
                        var workbenchRecipe = player.serverLevel().getRecipeManager().getRecipeFor(
                                net.minecraft.world.item.crafting.RecipeType.CRAFTING, crafting, player.serverLevel()).orElseThrow();
                        var crafted = workbenchRecipe.value().assemble(crafting, player.registryAccess());
                        if (!crafted.is(HomeCoreWorkbench.BLOCK.get().asItem()) || crafted.getCount() != 1) {
                            throw new IllegalStateException("Vanilla workbench recipe did not produce one workbench");
                        }
                        if (server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "homecore", "recipes/decorations/electronics_workbench")) == null) {
                            throw new IllegalStateException("Workbench recipe unlock missing");
                        }
                        BlockPos position = player.blockPosition().offset(2, 0, 0);
                        player.serverLevel().setBlockAndUpdate(position, HomeCoreWorkbench.BLOCK.get().defaultBlockState());
                        var block = (ElectronicsBlockEntity) player.serverLevel().getBlockEntity(position);
                        if (block == null) throw new IllegalStateException("Workbench fixture block missing");
                        player.getInventory().setItem(0, new ItemStack(Items.COPPER_INGOT, 64));
                        block.setItem(1, new ItemStack(Items.COPPER_INGOT, 64));
                        block.setItem(2, new ItemStack(Items.REDSTONE, 64));
                        block.setItem(3, new ItemStack(Items.REDSTONE, 64));
                        block.setItem(4, new ItemStack(Items.QUARTZ, 32));
                        player.openMenu(block, position);
                        player.containerMenu.quickMoveStack(player, 37);
                        if (block.getItem(0).getCount() != 64 || !player.getInventory().getItem(0).isEmpty()) {
                            throw new IllegalStateException("Shift transfer into material storage failed");
                        }
                    } catch (Throwable failure) { serverFailure = failure; }
                });
                return;
            }
            if (serverFailure != null) throw new IllegalStateException("Workbench fixture failed", serverFailure);
            if (System.nanoTime() - started > 120_000_000_000L) throw new IllegalStateException("Workbench screen timed out stage=" + stage);
            if (!(client.screen instanceof ElectronicsScreen screen)) return;
            var menu = screen.getMenu();
            int left = (screen.width - 320) / 2, top = (screen.height - 240) / 2;
            age++;
            if (stage == 1 && age > 20 && menu.selectedRecipe().isPresent()) {
                click(screen, left + 123, top + 91); // final output preset 64
                stage = 2;
                age = 0;
            } else if (stage == 2 && age > 12 && menu.quantity() == 64) {
                if (menu.maxCraftable() != 64) throw new IllegalStateException("Expected max 64, got " + menu.maxCraftable());
                capture(client, "workbench-selection.png");
                click(screen, left + 260, top + 121);
                stage = 3;
                age = 0;
            } else if (stage == 3 && age > 12 && menu.phase() == 1) {
                capture(client, "workbench-assembly.png");
                stage = 4;
                age = 0;
                part = 0;
            } else if (stage == 4 && age > 5 && menu.phase() == 1) {
                var layout = menu.selectedRecipe().orElseThrow().value().assemblyLayout();
                if (part > 0 && (menu.placedMask() & (1 << (part - 1))) == 0) return;
                if (part >= layout.size()) return;
                var target = layout.get(part);
                double sourceX = left + 40, sourceY = top + 49 + part * 13;
                double targetX = left + 129 + target.x() * 156 / 100;
                double targetY = top + 54 + target.y() * 48 / 100;
                screen.mouseClicked(sourceX, sourceY, 0);
                if (part % 2 == 0) {
                    screen.mouseDragged(targetX, targetY, 0, targetX - sourceX, targetY - sourceY);
                    screen.mouseReleased(targetX, targetY, 0);
                } else {
                    screen.mouseReleased(sourceX, sourceY, 0);
                    click(screen, targetX, targetY);
                }
                part++;
                age = 0;
            } else if (stage == 4 && menu.phase() == 2 && menu.progress() > 5 && !productionCaptured) {
                capture(client, "workbench-production.png");
                productionCaptured = true;
            } else if (stage == 4 && menu.phase() == 3) {
                if (menu.getSlot(9).getItem().getCount() != 64) throw new IllegalStateException("Completed output is not 64");
                if (!menu.getSlot(9).getItem().is(cycle == 0 ? HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get()
                        : HomeCoreItems.HOMELINK_MICROPROCESSOR.get())) throw new IllegalStateException("Wrong completed component");
                stage = 6;
                age = 0;
            } else if (stage == 6 && age > 10) {
                capture(client, "workbench-complete.png");
                click(screen, left + 260, top + 121);
                stage = 5;
                age = 0;
            } else if (stage == 5 && age > 10 && menu.getSlot(9).getItem().isEmpty()) {
                if (!productionCaptured) throw new IllegalStateException("Production framebuffer was not captured");
                if (cycle == 0) {
                    cycle = 1;
                    productionCaptured = false;
                    var playerId = client.player.getUUID();
                    var server = client.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            var player = server.getPlayerList().getPlayer(playerId);
                            var serverMenu = (fr.lkdm.homecore.workbench.ElectronicsMenu) player.containerMenu;
                            var block = serverMenu.workbench();
                            block.setItem(0, new ItemStack(HomeCoreItems.HOMELINK_CIRCUIT_BOARD.get(), 64));
                            for (int i = 1; i <= 4; i++) block.setItem(i, new ItemStack(Items.GOLD_NUGGET, 64));
                            block.setItem(5, new ItemStack(Items.COPPER_INGOT, 64));
                            block.setItem(6, new ItemStack(Items.COPPER_INGOT, 64));
                            block.setItem(7, new ItemStack(Items.REDSTONE, 64));
                            block.setItem(8, new ItemStack(Items.QUARTZ, 64));
                        } catch (Throwable failure) { serverFailure = failure; }
                    });
                    click(screen, left + 300, top + 36);
                    stage = 1;
                    age = 0;
                } else {
                    LogUtils.getLogger().info("HOMECORE_WORKBENCH_SCREEN_OK batch=64 recipes=2 drag=true click=true output=true screenshots=8");
                    finish(client);
                }
            }
        } catch (Throwable failure) {
            LogUtils.getLogger().error("HOMECORE_WORKBENCH_SCREEN_FAILED", failure);
            finish(client);
        }
    }

    private static void click(ElectronicsScreen screen, double x, double y) {
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void capture(Minecraft client, String filename) throws java.io.IOException {
        Path path = client.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(path);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(path.resolve(cycle == 0 ? filename : filename.replace("workbench-", "workbench-microprocessor-")));
        }
    }

    private static void finish(Minecraft client) {
        stage = 9;
        if (client.level != null) client.level.disconnect();
        client.disconnect();
        client.stop();
    }
}
