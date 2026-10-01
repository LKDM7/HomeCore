package fr.lkdm.homecore.api.production;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionLogTest {
    private static final ResourceLocation SOURCE = ResourceLocation.parse("homecore:electronics_workbench");

    private static ProductionReceipt receipt(UUID transaction, int quantity, long started) {
        return new ProductionReceipt(transaction, UUID.randomUUID(), SOURCE,
                Optional.of(ResourceLocation.parse("homecore:circuit_board")),
                new ItemStack(Items.IRON_INGOT, quantity), started, started + 40, 0L, Optional.empty());
    }

    @Test void carriesTheWholeBatchNotOneOperation() {
        var produced = receipt(UUID.randomUUID(), 64, 100);
        assertEquals(64, produced.quantity());
        assertEquals(64, produced.result().getCount());
    }

    @Test void observationCannotMutateTheProducedStack() {
        ItemStack original = new ItemStack(Items.IRON_INGOT, 16);
        var produced = new ProductionReceipt(UUID.randomUUID(), UUID.randomUUID(), SOURCE, Optional.empty(),
                original, 1, 2, 0, Optional.empty());
        original.setCount(1);
        produced.result().setCount(99);
        assertEquals(16, produced.quantity());
    }

    @Test void rejectsEmptyResultsAndImpossibleOrdering() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new ProductionReceipt(id, id, SOURCE,
                Optional.empty(), ItemStack.EMPTY, 0, 0, 0, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ProductionReceipt(id, id, SOURCE,
                Optional.empty(), new ItemStack(Items.STONE), 10, 9, 0, Optional.empty()));
    }

    @Test void deliversInOrderAndKeepsRunningAfterAFailingListener() {
        var log = new ProductionLog();
        List<UUID> seen = new ArrayList<>();
        log.subscribe(produced -> { throw new IllegalStateException("listener is broken"); });
        log.subscribe(produced -> seen.add(produced.transactionId()));
        UUID first = UUID.randomUUID();
        log.publish(receipt(first, 4, 1));
        assertEquals(List.of(first), seen);
        assertEquals(2, log.listenerCount());
    }

    @Test void aClosedSubscriptionStopsReceivingAndSequencesStrictlyIncrease() {
        var log = new ProductionLog();
        List<UUID> seen = new ArrayList<>();
        var subscription = log.subscribe(produced -> seen.add(produced.transactionId()));
        log.publish(receipt(UUID.randomUUID(), 1, 1));
        subscription.close();
        subscription.close();
        log.publish(receipt(UUID.randomUUID(), 1, 1));
        assertEquals(1, seen.size());
        assertEquals(0, log.listenerCount());
        assertTrue(log.nextSequence() < log.nextSequence());
    }

    @Test void aClosedLogRefusesPublishingAndSubscribing() {
        var log = new ProductionLog();
        log.close();
        log.close();
        assertThrows(IllegalStateException.class, () -> log.publish(receipt(UUID.randomUUID(), 1, 1)));
        assertThrows(IllegalStateException.class, () -> log.subscribe(produced -> { }));
    }
}
