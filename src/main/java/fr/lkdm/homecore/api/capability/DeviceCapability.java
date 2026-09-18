package fr.lkdm.homecore.api.capability;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Typed capability contract shared by producers and consumers. A capability
 * describes an interface, not a built-in energy, inventory or other subsystem.
 *
 * @param id globally unique namespaced identifier owned by the providing mod
 * @param type runtime class of implementations
 * @param <T> capability implementation contract
 */
public record DeviceCapability<T>(ResourceLocation id, Class<T> type) {
    /** Creates a capability with non-null reference type metadata. */
    public DeviceCapability {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (type.isPrimitive()) throw new IllegalArgumentException("Capability requires a reference type");
    }
}
