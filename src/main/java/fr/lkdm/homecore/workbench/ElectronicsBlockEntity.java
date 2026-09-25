package fr.lkdm.homecore.workbench;

import fr.lkdm.homecore.registry.HomeCoreRecipes;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.workbench.recipe.BatchPlanner;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent inventory with exclusive, refundable reservations owned by the server. */
public final class ElectronicsBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private final NonNullList<ItemStack> items = NonNullList.withSize(10, ItemStack.EMPTY);
    private final NonNullList<ItemStack> reserved = NonNullList.withSize(9, ItemStack.EMPTY);
    private AssemblySession session;
    private boolean recoverReservation;

    public ElectronicsBlockEntity(BlockPos pos, BlockState state) {
        super(HomeCoreWorkbench.BLOCK_ENTITY.get(), pos, state);
    }

    public boolean locked() { return session != null || recoverReservation; }
    public int phase() { return session != null ? session.phase : items.get(9).isEmpty() ? 0 : 3; }
    public AssemblySession session() { return session; }
    public List<ItemStack> materials() { return List.copyOf(items.subList(0, 9)); }

    public boolean start(ServerPlayer player, RecipeHolder<ElectronicsRecipe> requested, int quantity) {
        if (level == null || level.isClientSide || locked() || !ownsMenu(player)) return false;
        // Resolve the current recipe again, so a stale client cannot select an obsolete definition.
        var current = level.getRecipeManager().getAllRecipesFor(HomeCoreRecipes.TYPE.get()).stream()
                .filter(recipe -> recipe.id().equals(requested.id())).findFirst();
        if (current.isEmpty()) return false;
        ElectronicsRecipe recipe = current.get().value();
        ItemStack result = recipe.result();
        if (quantity < 1 || quantity > 64 || quantity % result.getCount() != 0
                || quantity > BatchPlanner.maxOutput(recipe, materials(), items.get(9))) return false;
        var plan = BatchPlanner.plan(recipe.materials(), materials(), quantity / result.getCount());
        if (plan.isEmpty()) return false;
        int[] consumed = plan.get();
        for (int slot = 0; slot < 9; slot++) reserved.set(slot, items.get(slot).split(consumed[slot]));
        int duration = Math.min(80, Math.max(20, recipe.processingTime() + (quantity - 1) * 60 / 63));
        session = new AssemblySession(UUID.randomUUID(), player.getUUID(), current.get().id(),
                result.copyWithCount(quantity), recipe.assemblyLayout().size(), duration);
        changed();
        return true;
    }

    public boolean place(ServerPlayer player, UUID sessionId, int source, int target) {
        if (!ownsMenu(player) || session == null || !session.place(player.getUUID(), sessionId, source, target)) return false;
        changed();
        return true;
    }

    public void cancel(Player player, UUID sessionId) {
        if (session != null && session.phase == 1 && session.player.equals(player.getUUID()) && session.id.equals(sessionId)) refund();
    }

    private boolean ownsMenu(Player player) {
        return stillValid(player) && player.containerMenu instanceof ElectronicsMenu menu && menu.workbench() == this;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ElectronicsBlockEntity block) {
        if (block.recoverReservation) { block.refund(); return; }
        AssemblySession active = block.session;
        if (active == null) return;
        if (active.phase == 1) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(active.player);
            if (owner == null || !block.ownsMenu(owner)) block.refund();
        } else if (active.phase == 2) {
            active.progress++;
            if (active.progress >= active.duration) {
                ItemStack output = block.items.get(9);
                if ((!output.isEmpty() && !ItemStack.isSameItemSameComponents(output, active.result))
                        || output.getCount() + active.result.getCount() > Math.min(64, active.result.getMaxStackSize())) {
                    // A foreign inventory modification must never destroy the reservation.
                    block.refund();
                    return;
                }
                block.items.set(9, active.result.copyWithCount(output.getCount() + active.result.getCount()));
                // Compute per consumed unit, as vanilla crafting does (buckets, bottles, etc.).
                List<ItemStack> remainders = new ArrayList<>();
                for (ItemStack consumed : block.reserved) {
                    for (int unit = 0; unit < consumed.getCount(); unit++) {
                        ItemStack remainder = consumed.copyWithCount(1).getCraftingRemainingItem();
                        if (!remainder.isEmpty()) remainders.add(remainder);
                    }
                }
                block.reserved.clear();
                block.session = null;
                for (ItemStack remainder : remainders) {
                    for (int slot = 0; slot < 9 && !remainder.isEmpty(); slot++) block.returnToSlot(slot, remainder);
                    if (!remainder.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5,
                            pos.getY() + 0.5, pos.getZ() + 0.5, remainder);
                }
                block.changed();
            } else block.setChanged();
        }
    }

    private void refund() {
        // Slots were locked during reservation. Preserve a fallback for foreign inventory edits.
        List<ItemStack> overflow = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = reserved.get(slot);
            if (stack.isEmpty()) continue;
            reserved.set(slot, ItemStack.EMPTY);
            returnToSlot(slot, stack);
            for (int target = 0; target < 9 && !stack.isEmpty(); target++) returnToSlot(target, stack);
            if (!stack.isEmpty()) overflow.add(stack);
        }
        session = null;
        recoverReservation = false;
        changed();
        if (level != null && !level.isClientSide) {
            overflow.forEach(stack -> Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack));
        }
    }

    private void returnToSlot(int slot, ItemStack stack) {
        ItemStack existing = items.get(slot);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return;
        int room = Math.min(64, stack.getMaxStackSize()) - existing.getCount();
        int amount = Math.min(room, stack.getCount());
        if (amount > 0) {
            items.set(slot, stack.copyWithCount(existing.getCount() + amount));
            stack.shrink(amount);
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        List<ItemStack> drops = new ArrayList<>();
        items.forEach(stack -> { if (!stack.isEmpty()) drops.add(stack.copy()); });
        reserved.forEach(stack -> { if (!stack.isEmpty()) drops.add(stack.copy()); });
        items.clear(); reserved.clear(); session = null; recoverReservation = false;
        setChanged();
        drops.forEach(stack -> Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack));
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide && getBlockState().getBlock() instanceof ElectronicsBlock
                && getBlockState().getValue(ElectronicsBlock.STAGE) != phase()) {
            level.setBlock(worldPosition, getBlockState().setValue(ElectronicsBlock.STAGE, phase()), 3);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.put("Reserved", ContainerHelper.saveAllItems(new CompoundTag(), reserved, registries));
        if (session != null) {
            CompoundTag active = new CompoundTag();
            active.putUUID("Id", session.id); active.putUUID("Player", session.player);
            active.putString("Recipe", session.recipe.toString()); active.put("Result", session.result.save(registries));
            active.putInt("Steps", session.steps); active.putInt("Mask", session.placedMask);
            active.putInt("Duration", session.duration); active.putInt("Progress", session.progress);
            active.putInt("Phase", session.phase);
            tag.put("Assembly", active);
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear(); reserved.clear(); session = null;
        ContainerHelper.loadAllItems(tag, items, registries);
        ContainerHelper.loadAllItems(tag.getCompound("Reserved"), reserved, registries);
        recoverReservation = reserved.stream().anyMatch(stack -> !stack.isEmpty());
        CompoundTag active = tag.getCompound("Assembly");
        // Unvalidated prototypes are always refunded after reload; completed prototypes resume.
        if (active.getInt("Phase") == 2 && active.hasUUID("Id") && active.hasUUID("Player")) {
            ResourceLocation recipe = ResourceLocation.tryParse(active.getString("Recipe"));
            ItemStack result = ItemStack.parseOptional(registries, active.getCompound("Result"));
            if (recipe != null && !result.isEmpty() && result.getCount() <= Math.min(64, result.getMaxStackSize())
                    && recoverReservation) {
                session = new AssemblySession(active.getUUID("Id"), active.getUUID("Player"), recipe, result,
                        Math.clamp(active.getInt("Steps"), 1, 8), Math.clamp(active.getInt("Duration"), 20, 80));
                session.phase = 2;
                session.placedMask = (1 << session.steps) - 1;
                session.progress = Math.clamp(active.getInt("Progress"), 0, session.duration);
                recoverReservation = false;
            }
        }
    }

    @Override public Component getDisplayName() { return Component.translatable("block.homecore.electronics_workbench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ElectronicsMenu(id, inventory, this); }
    @Override public int getContainerSize() { return 10; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (locked()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) changed();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }
    @Override public void setItem(int slot, ItemStack stack) {
        if (locked()) return;
        items.set(slot, stack);
        stack.limitSize(Math.min(64, stack.getMaxStackSize()));
        changed();
    }
    @Override public void clearContent() { if (!locked()) { items.clear(); changed(); } }
    @Override public boolean stillValid(Player player) {
        return level != null && !isRemoved() && player.level() == level && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot < 9 && !locked(); }
    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.DOWN ? new int[]{9} : new int[]{0,1,2,3,4,5,6,7,8}; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == 9 && !locked(); }
}
