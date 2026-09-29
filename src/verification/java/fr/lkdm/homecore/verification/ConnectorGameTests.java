package fr.lkdm.homecore.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homecore.registry.HomeCoreItems;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual connector interactions and permission checks on a running server. */
@GameTestHolder(HomeCoreValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConnectorGameTests {
    private static final class Machine implements DashboardDevice, NetworkMember {
        private final UUID id = UUID.randomUUID();
        private final UUID owner;
        private Optional<UUID> network = Optional.empty();
        private Machine(UUID owner) { this.owner = owner; }
        @Override public UUID id() { return id; }
        @Override public ResourceLocation deviceType() { return ResourceLocation.parse("homecore_validation:connector_machine"); }
        @Override public Component displayName() { return Component.literal("Connector fixture"); }
        @Override public DeviceStatus status() { return DeviceStatus.ONLINE; }
        @Override public Optional<UUID> homeNetwork() { return network; }
        @Override public Optional<UUID> owner() { return Optional.of(owner); }
        @Override public boolean canConfigure(ServerPlayer player) { return owner.equals(player.getUUID()); }
        @Override public void homeNetworkChanged(Optional<HomeNetwork> value) { network = value.map(HomeNetwork::id); }
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HomeCoreItems.HOMELINK_CONNECTOR.get()));
        return player;
    }

    private static Optional<UUID> selection(FakePlayer player) {
        var tag = player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID("homecore_network") ? Optional.of(tag.getUUID("homecore_network")) : Optional.empty();
    }

    private static void use(FakePlayer player) {
        HomeCoreItems.HOMELINK_CONNECTOR.get().use(player.level(), player, InteractionHand.MAIN_HAND);
    }

    private static void click(FakePlayer player, BlockPos position) {
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        HomeCoreItems.HOMELINK_CONNECTOR.get().onItemUseFirst(player.getMainHandItem(), context);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void selectsOnlyManageableNetworksAndPreservesOtherItemData(GameTestHelper helper) {
        var player = player(helper, "ConnectorOwner");
        var networks = DashboardAPI.networks(helper.getLevel().getServer());
        var first = networks.createNetwork("First", player.getUUID());
        var second = networks.createNetwork("Second", player.getUUID());
        var foreign = networks.createNetwork("Foreign", UUID.randomUUID());
        networks.setMember(foreign.id(), player.getUUID(), NetworkRole.VIEWER);
        try {
            use(player);
            helper.assertTrue(selection(player).equals(Optional.of(first.id())), "First managed network was not selected");
            use(player);
            helper.assertTrue(selection(player).equals(Optional.of(second.id())), "Network selection did not advance");
            use(player);
            helper.assertTrue(selection(player).equals(Optional.of(first.id())), "Read-only network was selectable");
            CustomData.update(DataComponents.CUSTOM_DATA, player.getMainHandItem(), tag -> tag.putString("other", "preserved"));
            player.setShiftKeyDown(true);
            use(player);
            helper.assertTrue(selection(player).isEmpty(), "Sneaking did not clear selection");
            helper.assertTrue(player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag().getString("other").equals("preserved"),
                    "Clearing selection removed unrelated item data");
            helper.succeed();
        } finally {
            networks.deleteNetwork(first.id());
            networks.deleteNetwork(second.id());
            networks.deleteNetwork(foreign.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bindingRechecksMachineOwnershipAndBothNetworkPermissions(GameTestHelper helper) {
        var owner = player(helper, "MachineOwner");
        var intruder = player(helper, "MachineVisitor");
        var networks = DashboardAPI.networks(helper.getLevel().getServer());
        var first = networks.createNetwork("Owner network", owner.getUUID());
        var second = networks.createNetwork("Delegated network", intruder.getUUID());
        networks.setMember(second.id(), owner.getUUID(), NetworkRole.ADMIN);
        var machine = new Machine(owner.getUUID());
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        DashboardAPI.registerDeviceProvider(BlockEntityType.CHEST, block -> machine);
        try {
            click(owner, position);
            helper.assertTrue(machine.homeNetwork().isEmpty(), "Unselected connector changed membership");
            use(owner);
            click(owner, position);
            helper.assertTrue(machine.homeNetwork().equals(Optional.of(first.id())) && networks.getDevices(first.id()).contains(machine.id()),
                    "Connector did not bind both block and network");
            use(intruder);
            click(intruder, position);
            helper.assertTrue(machine.homeNetwork().equals(Optional.of(first.id())), "Network ownership bypassed machine ownership");
            intruder.setShiftKeyDown(true);
            click(intruder, position);
            helper.assertTrue(selection(intruder).equals(Optional.of(second.id())), "Unauthorized player copied a machine network");
            use(owner);
            networks.setMember(second.id(), owner.getUUID(), NetworkRole.VIEWER);
            click(owner, position);
            helper.assertTrue(machine.homeNetwork().equals(Optional.of(first.id())), "Revoked destination permission remained usable");
            networks.setMember(second.id(), owner.getUUID(), NetworkRole.ADMIN);
            click(owner, position);
            helper.assertTrue(machine.homeNetwork().equals(Optional.of(second.id())) && !networks.getDevices(first.id()).contains(machine.id()),
                    "Moving networks did not remove old membership");
            owner.setShiftKeyDown(true);
            use(owner);
            click(owner, position);
            helper.assertTrue(selection(owner).equals(Optional.of(second.id())), "Authorized copy did not select the device network");
            owner.setShiftKeyDown(false);
            use(owner);
            networks.setMember(second.id(), owner.getUUID(), NetworkRole.VIEWER);
            click(owner, position);
            helper.assertTrue(machine.homeNetwork().equals(Optional.of(second.id())), "Moving ignored revoked source permission");
            networks.deleteNetwork(first.id());
            click(owner, position);
            helper.assertTrue(machine.homeNetwork().equals(Optional.of(second.id())), "Deleted destination changed membership");
            helper.succeed();
        } finally {
            DashboardAPI.providers().unregister(BlockEntityType.CHEST);
            networks.deleteNetwork(first.id());
            networks.deleteNetwork(second.id());
        }
    }
}
