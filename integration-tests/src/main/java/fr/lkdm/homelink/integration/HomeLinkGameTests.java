package fr.lkdm.homelink.integration;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.item.ItemApi;
import fr.lkdm.homecore.api.item.ItemPortType;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.energy.block.SolarPanelBlock;
import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import fr.lkdm.homelink.quarry.blockentity.QuarryControllerBlockEntity;
import fr.lkdm.homelink.quarry.registry.QuarryRegistries;
import fr.lkdm.homelink.storage.block.StorageBlock;
import fr.lkdm.homelink.storage.blockentity.DepositBlockEntity;
import fr.lkdm.homelink.storage.blockentity.StorageBlockEntity;
import fr.lkdm.homelink.storage.registry.StorageRegistries;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** Vérifie les vrais appareils des cinq mods sur un même serveur dédié. */
@GameTestHolder(HomeLinkIntegration.ID)
@PrefixGameTestTemplate(false)
public final class HomeLinkGameTests {
    private static final BlockPos MACHINE = new BlockPos(3, 1, 3);

    private HomeLinkGameTests() { }

    private static int count(IItemHandler inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static DepositBlockEntity deposit(GameTestHelper helper, BlockPos pos, Direction front) {
        helper.setBlock(pos, StorageRegistries.DEPOSIT.get().defaultBlockState().setValue(StorageBlock.TARGET, front));
        return helper.getBlockEntity(pos);
    }

    private static SolarPanelBlockEntity solar(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setDayTime(6_000);
        level.setWeatherParameters(1_000_000, 0, false, false);
        level.setRainLevel(0);
        level.setThunderLevel(0);
        SolarPanelBlock block = EnergyRegistries.SOLAR_PANEL_3.get();
        helper.setBlock(pos, block);
        BlockPos absolute = helper.absolutePos(pos);
        block.setPlacedBy(level, absolute, level.getBlockState(absolute), null, ItemStack.EMPTY);
        // Le serveur GameTest peut couvrir la structure. Le panneau exige réellement le ciel libre
        // sur chacune de ses tuiles : ouvrir ces quatre colonnes sans modifier sa règle de production.
        for (BlockPos tile : block.positions(absolute, level.getBlockState(absolute))) {
            for (int y = tile.getY() + 1; y < level.getMaxBuildHeight(); y++) {
                BlockPos above = new BlockPos(tile.getX(), y, tile.getZ());
                if (!level.isEmptyBlock(above)) level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
            }
        }
        SolarPanelBlockEntity panel = helper.getBlockEntity(pos);
        panel.invalidateSky();
        return panel;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void panneauEnergyAlimenteStationFarm(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.FARMBOT_STATION.get());
        FarmBotStationBlockEntity station = helper.getBlockEntity(MACHINE);
        var panel = solar(helper, MACHINE.above());
        helper.assertTrue(station.energyPort().stored() == 0, "La station doit démarrer sans HE");
        helper.succeedWhen(() -> {
            helper.assertTrue(panel.core().generated() > 0, "Le vrai panneau solaire ne produit pas : " + solarDiagnostic(panel));
            helper.assertTrue(station.energyPort().stored() > 0, "Energy n'a pas alimenté Farm par son transfert automatique");
            helper.assertTrue(panel.exported() == station.energyPort().stored(), "Le transfert a perdu ou créé des HE");
            helper.assertTrue(panel.core().generated() == panel.core().buffer().stored() + station.energyPort().stored(),
                    "La production, le tampon solaire et la station ne conservent pas les HE");
        });
    }

    private static String solarDiagnostic(SolarPanelBlockEntity panel) {
        var level = panel.getLevel();
        var block = (SolarPanelBlock) panel.getBlockState().getBlock();
        String heights = block.positions(panel.getBlockPos(), panel.getBlockState()).stream()
                .map(pos -> pos + " surface=" + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        pos.getX(), pos.getZ()) + " bloc=" + level.getBlockState(pos))
                .collect(java.util.stream.Collectors.joining(" ; "));
        return "statut=" + panel.status() + ", ciel=" + panel.skyVisible() + ", complet="
                + block.complete(level, panel.getBlockPos(), panel.getBlockState()) + ", retiré=" + panel.isRemoved()
                + ", heure=" + level.getDayTime() + ", dimension=" + level.dimension().location()
                + ", nominal=" + panel.nominalPerCycle() + ", tuiles=" + heights;
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void farmBotStationDeposeDansStorage(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.FARMBOT_STATION.get());
        FarmBotStationBlockEntity station = helper.getBlockEntity(MACHINE);
        var input = deposit(helper, MACHINE.east(), Direction.NORTH);
        station.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 20));
        station.output().setStackInSlot(1, new ItemStack(Items.WHEAT_SEEDS, 64));
        var source = helper.getLevel().getCapability(ItemApi.BLOCK, helper.absolutePos(MACHINE), Direction.EAST);
        var target = helper.getLevel().getCapability(ItemApi.BLOCK, input.getBlockPos(), Direction.WEST);
        helper.assertTrue(source != null && source.type() == ItemPortType.OUTPUT, "La station n'expose pas OUTPUT");
        helper.assertTrue(target != null && target.type() == ItemPortType.INPUT, "Le Deposit n'expose pas INPUT");
        helper.assertTrue(source.insertItem(2, new ItemStack(Items.DIRT), false).getCount() == 1, "OUTPUT accepte une insertion");
        helper.succeedWhen(() -> {
            helper.assertTrue(count(station.output(), Items.WHEAT) == 0 && count(station.output(), Items.WHEAT_SEEDS) == 0,
                    "La station n'a pas vidé sa sortie automatiquement");
            helper.assertTrue(count(input.inventory(), Items.WHEAT) == 20 && count(input.inventory(), Items.WHEAT_SEEDS) == 64,
                    "Le vrai Deposit a perdu ou dupliqué les récoltes");
            helper.assertTrue(target.extractItem(0, 64, false).isEmpty(), "INPUT autorise une extraction");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void quarryDeposeSansPerteQuandStorageEstPresquePlein(GameTestHelper helper) {
        helper.setBlock(MACHINE, QuarryRegistries.QUARRY_II.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        QuarryControllerBlockEntity quarry = helper.getBlockEntity(MACHINE);
        var input = deposit(helper, MACHINE.south(), Direction.SOUTH);
        for (int slot = 0; slot < input.inventory().getSlots(); slot++) input.inventory().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        input.inventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 60));
        quarry.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(quarry.outputConnected(), "Quarry ne détecte pas le vrai Deposit derrière elle");
            helper.assertTrue(count(input.inventory(), Items.COBBLESTONE) == 64, "Storage doit accepter exactement quatre objets");
            helper.assertTrue(count(quarry.buffer(), Items.COBBLESTONE) == 60, "Quarry doit garder les soixante objets restants");
            helper.assertTrue(count(input.inventory(), Items.DIRT) == 26 * 64, "Le transfert a modifié les autres objets");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void faceFermeeStorageBloquePuisPermetLeTransfert(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.FARMBOT_STATION.get());
        FarmBotStationBlockEntity station = helper.getBlockEntity(MACHINE);
        var input = deposit(helper, MACHINE.east(), Direction.WEST);
        station.output().setStackInSlot(0, new ItemStack(Items.CARROT, 17));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(count(station.output(), Items.CARROT) == 17 && count(input.inventory(), Items.CARROT) == 0,
                    "La face écran fermée a laissé passer des objets");
            helper.getLevel().setBlockAndUpdate(input.getBlockPos(), input.getBlockState().setValue(StorageBlock.TARGET, Direction.NORTH));
            helper.succeedWhen(() -> helper.assertTrue(count(station.output(), Items.CARROT) == 0
                    && count(input.inventory(), Items.CARROT) == 17, "L'ouverture du port n'a pas repris le transfert"));
        });
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void memesPermissionsPourEnergyFarmQuarryEtStorage(GameTestHelper helper) {
        var owner = player(helper, "proprietaire");
        var networkOwner = player(helper, "reseaux");
        var stranger = player(helper, "visiteur");
        helper.setBlock(MACHINE, ModBlocks.FARMBOT_STATION.get());
        FarmBotStationBlockEntity farm = helper.getBlockEntity(MACHINE);
        farm.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        helper.setBlock(MACHINE.east(3), QuarryRegistries.QUARRY_I.get());
        QuarryControllerBlockEntity quarry = helper.getBlockEntity(MACHINE.east(3));
        quarry.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        helper.setBlock(MACHINE.south(3), StorageRegistries.CONTROLLER.get());
        StorageBlockEntity storage = helper.getBlockEntity(MACHINE.south(3));
        storage.setOwner(owner.getUUID());
        storage.ensureHomeCore();
        var energy = solar(helper, new BlockPos(7, 3, 7));
        energy.setOwner(owner.getUUID());
        helper.runAfterDelay(10, () -> {
            var networks = DashboardAPI.networks(helper.getLevel().getServer());
            UUID oldStorageNetwork = storage.networkId();
            var first = networks.createNetwork("Premier", networkOwner.getUUID());
            var second = networks.createNetwork("Second", networkOwner.getUUID());
            networks.setMember(first.id(), owner.getUUID(), NetworkRole.ADMIN);
            networks.setMember(second.id(), owner.getUUID(), NetworkRole.ADMIN);
            try {
                for (BlockEntity block : List.of(farm, quarry, storage, energy)) {
                    var device = DashboardAPI.providers().discover(block).orElseThrow();
                    helper.assertTrue(device instanceof NetworkMember, "Appareil sans NetworkMember : " + device.deviceType());
                    var member = (NetworkMember) device;
                    helper.assertTrue(DashboardAPI.bindDevice(stranger, device, Optional.of(first.id())) == NetworkMember.BindResult.DENIED,
                            "Un visiteur a déplacé " + device.deviceType());
                    helper.assertTrue(DashboardAPI.bindDevice(owner, device, Optional.of(first.id())) == NetworkMember.BindResult.BOUND,
                            "L'administrateur ne peut pas lier " + device.deviceType());
                    networks.setMember(second.id(), owner.getUUID(), NetworkRole.MEMBER);
                    helper.assertTrue(DashboardAPI.bindDevice(owner, device, Optional.of(second.id())) == NetworkMember.BindResult.DENIED,
                            "L'entrée dans le réseau exige MANAGE_NETWORK");
                    helper.assertTrue(member.homeNetwork().equals(Optional.of(first.id())) && networks.getDevices(first.id()).contains(device.id())
                            && !networks.getDevices(second.id()).contains(device.id()), "Le refus sur la destination a changé la liaison");
                    networks.setMember(second.id(), owner.getUUID(), NetworkRole.ADMIN);
                    networks.setMember(first.id(), owner.getUUID(), NetworkRole.MEMBER);
                    helper.assertTrue(DashboardAPI.bindDevice(owner, device, Optional.of(second.id())) == NetworkMember.BindResult.DENIED,
                            "Le départ du réseau exige MANAGE_NETWORK");
                    helper.assertTrue(member.homeNetwork().equals(Optional.of(first.id())) && networks.getDevices(first.id()).contains(device.id())
                            && !networks.getDevices(second.id()).contains(device.id()), "Le refus a changé la liaison");
                    networks.setMember(first.id(), owner.getUUID(), NetworkRole.ADMIN);
                    helper.assertTrue(DashboardAPI.bindDevice(owner, device, Optional.of(second.id())) == NetworkMember.BindResult.BOUND,
                            "La migration autorisée a échoué");
                    helper.assertTrue(member.homeNetwork().equals(Optional.of(second.id())) && !networks.getDevices(first.id()).contains(device.id())
                            && networks.getDevices(second.id()).contains(device.id()), "La migration désynchronise HomeNetwork et l'appareil");
                    helper.assertTrue(member.owner().equals(Optional.of(owner.getUUID())), "La migration a changé le propriétaire");
                    helper.assertTrue(DashboardAPI.bindDevice(owner, device, Optional.empty()) == NetworkMember.BindResult.UNBOUND,
                            "Le détachement autorisé a échoué");
                }
                storage.ensureHomeCore();
                helper.assertTrue(storage.networkId() == null, "Storage recrée un réseau après détachement");
                var registries = helper.getLevel().registryAccess();
                var saved = storage.saveWithFullMetadata(registries);
                var state = storage.getBlockState();
                helper.destroyBlock(MACHINE.south(3));
                helper.setBlock(MACHINE.south(3), state);
                StorageBlockEntity restored = helper.getBlockEntity(MACHINE.south(3));
                restored.loadWithComponents(saved, registries);
                restored.ensureHomeCore();
                helper.assertTrue(restored.networkId() == null && restored.device() != null,
                        "Storage recrée un réseau après sauvegarde, remplacement et réenregistrement");
                helper.succeed();
            } finally {
                networks.deleteNetwork(first.id());
                networks.deleteNetwork(second.id());
                if (oldStorageNetwork != null) networks.deleteNetwork(oldStorageNetwork);
            }
        });
    }
}
