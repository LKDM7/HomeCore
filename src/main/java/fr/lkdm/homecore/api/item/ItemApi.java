package fr.lkdm.homecore.api.item;

import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.items.IItemHandler;

/** Shared HomeLink item ports; providers register the capability during RegisterCapabilitiesEvent. */
public final class ItemApi {
    /** Sided machine port. A null side requests the machine's unsided port. */
    public static final BlockCapability<ItemPort, Direction> BLOCK = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("homecore", "item_port"), ItemPort.class);

    private ItemApi() { }

    /**
     * Exposes a live inventory with enforced direction, preserving its slot restrictions and callbacks.
     * Cache the returned adapter in the block entity. This does not register a capability automatically.
     * @param inventory backing inventory
     * @param type allowed movement relative to the machine
     * @return direction-restricted live port
     */
    public static ItemPort of(IItemHandler inventory, ItemPortType type) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(type, "type");
        return new ItemPort() {
            @Override public ItemPortType type() { return type; }
            @Override public int getSlots() { return inventory.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return type.canReceive() && inventory.isItemValid(slot, stack);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return type.canReceive() ? inventory.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return type.canSend() ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
        };
    }
}
