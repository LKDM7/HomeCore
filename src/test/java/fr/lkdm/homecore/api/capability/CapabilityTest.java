package fr.lkdm.homecore.api.capability;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:label");
    private static final DeviceCapability<CharSequence> LABEL = new DeviceCapability<>(ID, CharSequence.class);

    @Test void registersAndQueriesExactContracts() {
        var registry = new CapabilityRegistry();
        assertSame(LABEL, registry.register(LABEL));
        assertEquals(LABEL, registry.get(ID).orElseThrow());
        assertEquals(LABEL, registry.query(ID, CharSequence.class).orElseThrow());
        assertTrue(registry.query(ID, String.class).isEmpty());
        assertTrue(registry.get(ResourceLocation.parse("test:absent")).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> registry.getAll().clear());
    }

    @Test void rejectsDuplicateIdRegardlessOfContract() {
        var registry = new CapabilityRegistry();
        registry.register(LABEL);
        assertThrows(IllegalArgumentException.class, () -> registry.register(LABEL));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new DeviceCapability<>(ID, String.class)));
        assertEquals(1, registry.getAll().size());
    }

    @Test void bindingsPreserveTypeSafetyAndAreImmutableSnapshots() {
        var builder = CapabilitySet.builder().add(LABEL, "Machine");
        var set = builder.build();
        assertEquals("Machine", set.query(LABEL).orElseThrow());
        assertTrue(set.query(new DeviceCapability<>(ID, String.class)).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> set.ids().clear());
        assertThrows(IllegalArgumentException.class, () -> builder.add(LABEL, "Other"));
        var extra = new DeviceCapability<>(ResourceLocation.parse("test:extra"), Integer.class);
        builder.add(extra, 1);
        assertTrue(set.query(extra).isEmpty());
        assertTrue(CapabilitySet.empty().query(LABEL).isEmpty());
    }

    @Test void rejectsNullAndPrimitiveBindings() {
        assertThrows(NullPointerException.class, () -> CapabilitySet.builder().add(LABEL, null));
        assertThrows(IllegalArgumentException.class, () -> new DeviceCapability<>(ID, int.class));
    }
}
