package fr.lkdm.homecore.api.capability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Thread-safe registry of unique capability descriptors; it holds no device implementations. */
public final class CapabilityRegistry {
    /** Creates an empty capability descriptor registry. */
    public CapabilityRegistry() { }

    private final Map<ResourceLocation, DeviceCapability<?>> capabilities = new LinkedHashMap<>();

    /**
     * Registers a descriptor; duplicate IDs are always rejected.
     *
     * @param capability descriptor to register
     * @param <T> implementation contract
     * @return registered descriptor
     * @throws IllegalArgumentException if the ID is already registered
     */
    public synchronized <T> DeviceCapability<T> register(DeviceCapability<T> capability) {
        Objects.requireNonNull(capability, "capability");
        if (capabilities.putIfAbsent(capability.id(), capability) != null) {
            throw new IllegalArgumentException("Duplicate capability ID: " + capability.id());
        }
        return capability;
    }

    /**
     * Retrieves a descriptor by identity.
     *
     * @param id capability identifier
     * @return registered descriptor, or empty
     */
    public synchronized Optional<DeviceCapability<?>> get(ResourceLocation id) {
        return Optional.ofNullable(capabilities.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Queries a descriptor using its exact runtime contract without unchecked casts.
     *
     * @param id capability identifier
     * @param type required exact contract class
     * @param <T> capability contract
     * @return equivalent typed descriptor, or empty if absent or the type differs
     */
    public synchronized <T> Optional<DeviceCapability<T>> query(ResourceLocation id, Class<T> type) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(type, "type");
        DeviceCapability<?> registered = capabilities.get(id);
        return registered != null && registered.type().equals(type)
                ? Optional.of(new DeviceCapability<>(id, type)) : Optional.empty();
    }

    /** Returns immutable descriptors in registration order.
     * @return descriptor snapshot
     */
    public synchronized List<DeviceCapability<?>> getAll() { return List.copyOf(capabilities.values()); }
}
