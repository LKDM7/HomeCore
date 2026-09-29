package fr.lkdm.homecore.item;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homecore.api.security.Permission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Common network-binding tool for every registered NetworkMember device. */
public final class HomeLinkConnectorItem extends Item {
    private static final String NETWORK = "homecore_network";

    public HomeLinkConnectorItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // Handle the tool before a machine opens its own menu.
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        var block = context.getLevel().getBlockEntity(context.getClickedPos());
        var device = block == null ? Optional.<fr.lkdm.homecore.api.device.DashboardDevice>empty()
                : DashboardAPI.providers().discover(block);
        if (device.isEmpty() || !(device.get() instanceof NetworkMember member)) {
            message(player, "not_supported");
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            var network = member.homeNetwork();
            if (network.isEmpty()) message(player, "no_network");
            else if (!member.canConfigure(player)
                    || !DashboardAPI.hasPermission(player, network.get(), Permission.MANAGE_NETWORK)) message(player, "denied");
            else select(player, context.getItemInHand(), network.get());
        } else {
            var network = selected(context.getItemInHand());
            if (network.isEmpty()) message(player, "no_selection");
            else {
                var result = DashboardAPI.bindDevice(player, device.get(), network);
                message(player, result.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!level.isClientSide && user instanceof ServerPlayer player) {
            if (player.isShiftKeyDown()) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(NETWORK));
                message(player, "cleared");
            } else {
                var networks = DashboardAPI.networks(player.server).getNetworksForPlayer(player.getUUID()).stream()
                        .filter(network -> DashboardAPI.hasPermission(player, network.id(), Permission.MANAGE_NETWORK)).toList();
                if (networks.isEmpty()) message(player, "no_network");
                else {
                    var ids = networks.stream().map(fr.lkdm.homecore.api.network.HomeNetwork::id).toList();
                    int next = selected(stack).map(ids::indexOf).orElse(-1) + 1;
                    select(player, stack, ids.get(next % ids.size()));
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static Optional<UUID> selected(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(NETWORK) ? Optional.of(tag.getUUID(NETWORK)) : Optional.empty();
    }

    private static void select(ServerPlayer player, ItemStack stack, UUID network) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(NETWORK, network));
        String name = DashboardAPI.networks(player.server).getNetwork(network).orElseThrow().name();
        player.displayClientMessage(Component.translatable("message.homecore.connector.selected", name), true);
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.homecore.connector." + key), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.homecore.homelink_connector.tooltip"));
        selected(stack).ifPresent(network -> tooltip.add(Component.literal(network.toString())));
    }
}
