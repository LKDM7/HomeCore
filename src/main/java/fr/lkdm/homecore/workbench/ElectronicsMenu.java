package fr.lkdm.homecore.workbench;

import fr.lkdm.homecore.registry.HomeCoreRecipes;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.workbench.recipe.BatchPlanner;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

/** Inventory synchronization uses vanilla slots; prototype actions use scoped request packets. */
public final class ElectronicsMenu extends AbstractContainerMenu {
    public static final int WIDTH = 320, HEIGHT = 240;
    private final Player viewer;
    private final Container inventory;
    private final ElectronicsBlockEntity workbench;
    private final BlockPos position;
    private ResourceLocation selected = WorkbenchNetworking.NO_RECIPE;
    private int requested = 1;
    private WorkbenchNetworking.State state;
    private WorkbenchNetworking.State lastSent;
    private long actionTick = -1;
    private int actionsThisTick;

    public ElectronicsMenu(int id, Inventory player, FriendlyByteBuf data) {
        this(id, player, null, data.readBlockPos());
    }
    public ElectronicsMenu(int id, Inventory player, ElectronicsBlockEntity workbench) {
        this(id, player, workbench, workbench.getBlockPos());
    }
    private ElectronicsMenu(int id, Inventory player, ElectronicsBlockEntity workbench, BlockPos position) {
        super(HomeCoreWorkbench.MENU.get(), id);
        this.viewer = player.player;
        this.workbench = workbench;
        this.position = position;
        this.inventory = workbench == null ? new SimpleContainer(10) : workbench;
        var recipes = recipes();
        if (!recipes.isEmpty()) {
            selected = recipes.getFirst().id();
            requested = recipes.getFirst().value().result().getCount();
        }
        for (int slot = 0; slot < 10; slot++) {
            final int index = slot;
            addSlot(new Slot(inventory, slot, slot == 9 ? 30 : 12 + 18 * (slot % 3),
                    slot == 9 ? 117 : 43 + 18 * (slot / 3)) {
                @Override public boolean mayPlace(ItemStack stack) { return index < 9 && !locked(); }
                @Override public boolean mayPickup(Player player) { return !locked(); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(player, col + row * 9 + 9, 79 + col * 18, 163 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 79 + col * 18, 221));
    }

    public ElectronicsBlockEntity workbench() { return workbench; }
    public List<RecipeHolder<ElectronicsRecipe>> recipes() {
        return viewer.level().getRecipeManager().getAllRecipesFor(HomeCoreRecipes.TYPE.get()).stream()
                // Registration order keeps related components together and matches the creative tab.
                .sorted(Comparator.<RecipeHolder<ElectronicsRecipe>>comparingInt(holder -> BuiltInRegistries.ITEM.getId(holder.value().result().getItem()))
                        .thenComparing(holder -> holder.id().toString())).toList();
    }
    public Optional<RecipeHolder<ElectronicsRecipe>> selectedRecipe() {
        ResourceLocation id = state == null ? selected : state.recipe();
        return recipes().stream().filter(recipe -> recipe.id().equals(id)).findFirst();
    }
    public int quantity() { return state == null ? requested : state.quantity(); }
    public int phase() { return state == null ? 0 : state.phase(); }
    public int placedMask() { return state == null ? 0 : state.placedMask(); }
    public int progress() { return state == null ? 0 : state.progress(); }
    public int duration() { return state == null ? 20 : state.duration(); }
    public int maxCraftable() { return state == null ? 0 : state.maxCraftable(); }
    public boolean busy() { return state != null && state.busy(); }
    public UUID sessionId() { return state == null ? WorkbenchNetworking.NO_SESSION : state.session(); }
    private boolean locked() { return workbench == null ? phase() == 1 || phase() == 2 : workbench.locked(); }

    public void select(ResourceLocation recipe, int quantity) { send(0, recipe, quantity, -1, -1); }
    public void start() { send(1, selectedRecipe().map(RecipeHolder::id).orElse(WorkbenchNetworking.NO_RECIPE), quantity(), -1, -1); }
    public void place(int source, int target) { send(2, WorkbenchNetworking.NO_RECIPE, 0, source, target); }
    public void cancel() { send(3, WorkbenchNetworking.NO_RECIPE, 0, -1, -1); }
    public void take() { send(4, WorkbenchNetworking.NO_RECIPE, 0, -1, -1); }
    private void send(int action, ResourceLocation recipe, int quantity, int source, int target) {
        if (viewer.level().isClientSide) PacketDistributor.sendToServer(new WorkbenchNetworking.Action(
                containerId, action, recipe, quantity, sessionId(), source, target));
    }
    public void receive(WorkbenchNetworking.State update) { this.state = update; }

    public void handle(ServerPlayer player, WorkbenchNetworking.Action action) {
        if (workbench == null || player != viewer || player.containerMenu != this || !stillValid(player)) return;
        long tick = player.level().getGameTime();
        if (actionTick != tick) { actionTick = tick; actionsThisTick = 0; }
        if (++actionsThisTick > 12) return;
        switch (action.action()) {
            case 0 -> {
                if (workbench.locked()) break;
                recipes().stream().filter(recipe -> recipe.id().equals(action.recipe())).findFirst().ifPresent(recipe -> {
                    int yield = recipe.value().result().getCount();
                    if (action.quantity() >= yield && action.quantity() <= 64 && action.quantity() % yield == 0) {
                        selected = recipe.id(); requested = action.quantity();
                    }
                });
            }
            case 1 -> {
                // Start uses the server's selection, never an unconfirmed client output request.
                recipes().stream().filter(recipe -> recipe.id().equals(selected)).findFirst()
                        .ifPresent(recipe -> workbench.start(player, recipe, requested));
            }
            case 2 -> workbench.place(player, action.session(), action.source(), action.target());
            case 3 -> workbench.cancel(player, action.session());
            case 4 -> { if (!workbench.locked()) quickMoveStack(player, 9); }
            default -> { }
        }
        broadcastChanges();
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (workbench == null || !(viewer instanceof ServerPlayer player)) return;
        AssemblySession session = workbench.session();
        ResourceLocation recipeId = selected;
        int quantity = requested;
        if (session != null) { recipeId = session.recipe; quantity = session.result.getCount(); }
        else if (!inventory.getItem(9).isEmpty()) {
            recipeId = recipes().stream().filter(recipe -> ItemStack.isSameItemSameComponents(recipe.value().result(), inventory.getItem(9)))
                    .map(RecipeHolder::id).findFirst().orElse(selected);
            quantity = inventory.getItem(9).getCount();
        }
        ResourceLocation currentRecipeId = recipeId;
        int max = recipes().stream().filter(recipe -> recipe.id().equals(currentRecipeId)).findFirst()
                .map(recipe -> BatchPlanner.maxOutput(recipe.value(), workbench.materials(), inventory.getItem(9))).orElse(0);
        state = new WorkbenchNetworking.State(containerId, recipeId, quantity, workbench.phase(),
                session == null ? 0 : session.placedMask, session == null ? 0 : session.progress,
                session == null ? 20 : session.duration, max, session == null ? WorkbenchNetworking.NO_SESSION : session.id,
                session != null && !session.player.equals(player.getUUID()));
        if (!state.equals(lastSent)) { PacketDistributor.sendToPlayer(player, state); lastSent = state; }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || locked()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 10) {
            if (!moveItemStackTo(stack, 10, 46, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (original.getCount() == stack.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
    @Override public boolean stillValid(Player player) {
        return workbench == null ? player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) <= 64
                : workbench.stillValid(player);
    }
    @Override public void removed(Player player) {
        if (workbench != null && workbench.session() != null) workbench.cancel(player, workbench.session().id);
        super.removed(player);
    }
}
