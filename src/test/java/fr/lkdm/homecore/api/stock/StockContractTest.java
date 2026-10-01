package fr.lkdm.homecore.api.stock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockContractTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    private static StockSourceId source(int x) { return new StockSourceId(OVERWORLD, new BlockPos(x, 64, 0)); }

    @Test void theSameChestSeenThroughTwoControllersCountsOnce() {
        var shared = source(10);
        var first = new StockEntry(new ItemStack(Items.DIAMOND), Map.of(shared, 8L));
        var second = new StockEntry(new ItemStack(Items.DIAMOND), Map.of(shared, 8L));
        var merged = new java.util.HashMap<StockSourceId, Long>();
        merged.putAll(first.bySource());
        second.bySource().forEach((id, count) -> merged.merge(id, count, Math::max));
        assertEquals(8L, new StockEntry(new ItemStack(Items.DIAMOND), merged).total());
        assertEquals(16L, first.total() + second.total(), "blind addition is exactly the mistake dedup prevents");
    }

    @Test void distinctSourcesAddUp() {
        var entry = new StockEntry(new ItemStack(Items.COPPER_INGOT), Map.of(source(1), 6L, source(2), 4L));
        assertEquals(10L, entry.total());
    }

    @Test void entriesAreCountOnePrototypesAndImmutable() {
        var entry = new StockEntry(new ItemStack(Items.COPPER_INGOT, 40), Map.of(source(1), 40L));
        assertEquals(1, entry.variant().getCount());
        entry.variant().setCount(99);
        assertEquals(1, entry.variant().getCount());
        assertThrows(UnsupportedOperationException.class, () -> entry.bySource().clear());
        assertThrows(IllegalArgumentException.class, () -> new StockEntry(ItemStack.EMPTY, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StockEntry(new ItemStack(Items.STONE), Map.of(source(1), -1L)));
    }

    @Test void unknownIsNeverZero() {
        var unavailable = StockSnapshot.unavailable(120L);
        assertEquals(StockAvailability.UNAVAILABLE, unavailable.availability());
        assertEquals(StockAccess.READ_ONLY, unavailable.access());
        assertTrue(unavailable.entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new StockSnapshot(StockAvailability.UNAVAILABLE,
                StockAccess.READ_ONLY, List.of(new StockEntry(new ItemStack(Items.STONE), Map.of())), 1L, 1L));
        var empty = new StockSnapshot(StockAvailability.COMPLETE, StockAccess.READ_AND_WITHDRAW, List.of(), 3L, 5L);
        assertTrue(empty.entries().isEmpty());
        assertEquals(StockAvailability.COMPLETE, empty.availability());
    }

    @Test void requestsAreBoundedAndNormalised() {
        var request = new StockRequest(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ItemStack(Items.COPPER_INGOT, 64)));
        assertEquals(1, request.variants().getFirst().getCount());
        request.variants().getFirst().setCount(50);
        assertEquals(1, request.variants().getFirst().getCount());
        UUID id = UUID.randomUUID();
        List<ItemStack> tooMany = java.util.stream.IntStream.range(0, StockRequest.MAX_VARIANTS + 1)
                .mapToObj(index -> new ItemStack(Items.STONE)).toList();
        assertThrows(IllegalArgumentException.class, () -> new StockRequest(id, id, tooMany));
        assertThrows(IllegalArgumentException.class, () -> new StockRequest(id, id, List.of(ItemStack.EMPTY)));
    }

    @Test void theCapabilityContractIsStable() {
        assertEquals(ResourceLocation.parse("homecore:stock_provider"), StockProvider.CAPABILITY.id());
        assertEquals(StockProvider.class, StockProvider.CAPABILITY.type());
    }
}
