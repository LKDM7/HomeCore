package fr.lkdm.homecore.api.energy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnergyApiTest {
    /** Minimal bounded store used only to exercise the contract's default methods. */
    private static final class Store implements EnergyPort {
        private final EnergyPortType type;
        private long stored;
        private final long capacity;
        Store(EnergyPortType type, long stored, long capacity) { this.type = type; this.stored = stored; this.capacity = capacity; }
        @Override public EnergyRole role() { return EnergyRole.STORAGE; }
        @Override public EnergyPortType type() { return type; }
        @Override public long stored() { return stored; }
        @Override public long capacity() { return capacity; }
        @Override public long insert(long amount, boolean simulate) {
            if (!type.canReceive() || amount <= 0) return 0;
            long accepted = Math.min(amount, capacity - stored);
            if (!simulate) stored += accepted;
            return accepted;
        }
        @Override public long extract(long amount, boolean simulate) {
            if (!type.canSend() || amount <= 0) return 0;
            long extracted = Math.min(amount, stored);
            if (!simulate) stored -= extracted;
            return extracted;
        }
    }

    @Test void portTypesDescribeInputAndOutput() {
        assertTrue(EnergyPortType.INPUT.canReceive());
        assertFalse(EnergyPortType.INPUT.canSend());
        assertFalse(EnergyPortType.OUTPUT.canReceive());
        assertTrue(EnergyPortType.OUTPUT.canSend());
        assertTrue(EnergyPortType.BOTH.canReceive() && EnergyPortType.BOTH.canSend());
    }

    @Test void defaultsReportRequestAndAvailabilityWithoutMoving() {
        var store = new Store(EnergyPortType.BOTH, 30, 100);
        assertEquals(70, store.requested());
        assertEquals(30, store.available());
        assertEquals(30, store.stored());
        assertEquals(0, new Store(EnergyPortType.OUTPUT, 30, 100).requested());
        assertEquals(0, new Store(EnergyPortType.INPUT, 30, 100).available());
    }

    @Test void simulatedThenRealTransferIsExact() {
        var from = new Store(EnergyPortType.OUTPUT, 50, 100);
        var to = new Store(EnergyPortType.INPUT, 90, 100);
        long offered = from.extract(Long.MAX_VALUE, true);
        long accepted = to.insert(offered, true);
        long moved = to.insert(from.extract(accepted, false), false);
        assertEquals(10, moved);
        assertEquals(40, from.stored());
        assertEquals(100, to.stored());
        assertEquals(140, from.stored() + to.stored());
    }

    @Test void saturatedAddNeverOverflows() {
        assertEquals(5, EnergyApi.saturatedAdd(2, 3));
        assertEquals(Long.MAX_VALUE, EnergyApi.saturatedAdd(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, EnergyApi.saturatedAdd(Long.MAX_VALUE - 1, Long.MAX_VALUE));
    }

    @Test void unitsAndCapabilityAreNamespacedByHomeCore() {
        assertEquals("HE", EnergyApi.HE.symbol());
        assertEquals("HE/t", EnergyApi.HE_PER_TICK.symbol());
        assertEquals("homecore:energy_port", EnergyApi.BLOCK.name().toString());
        assertEquals(EnergyPort.class, EnergyApi.BLOCK.typeClass());
    }
}
