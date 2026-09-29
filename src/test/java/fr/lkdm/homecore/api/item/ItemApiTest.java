package fr.lkdm.homecore.api.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemApiTest {
    @Test void outputToInputTransferPreservesRemainderAndSimulation() {
        var sourceInventory = new ItemStackHandler(1);
        sourceInventory.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 32));
        var destinationInventory = new ItemStackHandler(1);
        destinationInventory.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 60));
        var source = ItemApi.of(sourceInventory, ItemPortType.OUTPUT);
        var destination = ItemApi.of(destinationInventory, ItemPortType.INPUT);

        ItemStack offer = source.extractItem(0, 32, true);
        ItemStack remainder = destination.insertItem(0, offer, true);
        assertEquals(28, remainder.getCount());
        assertEquals(32, offer.getCount(), "Insertion must not mutate the caller's stack");
        assertEquals(32, source.getStackInSlot(0).getCount());
        assertEquals(60, destination.getStackInSlot(0).getCount());
        ItemStack extracted = source.extractItem(0, offer.getCount() - remainder.getCount(), false);
        assertTrue(destination.insertItem(0, extracted, false).isEmpty());
        assertEquals(28, source.getStackInSlot(0).getCount());
        assertEquals(64, destination.getStackInSlot(0).getCount());
    }

    @Test void forbiddenOperationsNeverReachBackingInventory() {
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
        var input = ItemApi.of(inventory, ItemPortType.INPUT);
        var output = ItemApi.of(inventory, ItemPortType.OUTPUT);
        for (boolean simulate : new boolean[]{true, false}) {
            assertTrue(input.extractItem(0, 5, simulate).isEmpty());
            ItemStack offer = new ItemStack(Items.IRON_INGOT, 7);
            assertEquals(7, output.insertItem(0, offer, simulate).getCount());
            assertFalse(output.isItemValid(0, offer));
            assertEquals(5, inventory.getStackInSlot(0).getCount());
        }
    }

    @Test void bothKeepsInventoryFiltersAndLimits() {
        var inventory = new ItemStackHandler(1) {
            @Override public int getSlotLimit(int slot) { return 8; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(Items.WHEAT); }
        };
        var port = ItemApi.of(inventory, ItemPortType.BOTH);
        assertEquals(8, port.getSlotLimit(0));
        assertFalse(port.isItemValid(0, new ItemStack(Items.STONE)));
        assertEquals(3, port.insertItem(0, new ItemStack(Items.STONE, 3), false).getCount());
        assertEquals(4, port.insertItem(0, new ItemStack(Items.WHEAT, 12), false).getCount());
        assertEquals(8, port.extractItem(0, 20, false).getCount());
        assertTrue(inventory.getStackInSlot(0).isEmpty());
    }

    @Test void exposesOneSharedCapability() {
        assertEquals("homecore:item_port", ItemApi.BLOCK.name().toString());
        assertEquals(ItemPort.class, ItemApi.BLOCK.typeClass());
        assertThrows(NullPointerException.class, () -> ItemApi.of(null, ItemPortType.INPUT));
    }
}
