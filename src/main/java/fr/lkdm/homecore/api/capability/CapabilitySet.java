package fr.lkdm.homecore.api.capability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable typed capability bindings for a device. Implementations remain live
 * objects and must be called on their owning server thread when they access worlds.
 * Create the set once and delegate DashboardDevice capability methods to it.
 */
public final class CapabilitySet {
    private final Map<ResourceLocation, Binding> bindings;

    private CapabilitySet(Map<ResourceLocation, Binding> bindings) { this.bindings = Map.copyOf(bindings); }

    /** Creates an empty bindings builder.
     * @return builder
     */
    public static Builder builder() { return new Builder(); }

    /** Returns an empty capability set.
     * @return empty bindings
     */
    public static CapabilitySet empty() { return new CapabilitySet(Map.of()); }

    /** Returns immutable IDs suitable for a device schema.
     * @return exposed capability IDs
     */
    public Set<ResourceLocation> ids() { return bindings.keySet(); }

    /**
     * Queries a binding only when both its identifier and exact contract match.
     *
     * @param capability requested descriptor
     * @param <T> capability contract
     * @return implementation or empty if the contract is not exposed
     */
    public <T> Optional<T> query(DeviceCapability<T> capability) {
        Objects.requireNonNull(capability, "capability");
        Binding binding = bindings.get(capability.id());
        if (binding == null || !binding.capability().equals(capability)) return Optional.empty();
        return Optional.of(capability.type().cast(binding.implementation()));
    }

    private record Binding(DeviceCapability<?> capability, Object implementation) { }

    /** Builder for immutable per-device bindings. */
    public static final class Builder {
        private final Map<ResourceLocation, Binding> bindings = new LinkedHashMap<>();
        private Builder() { }

        /**
         * Binds a capability to a checked implementation. Duplicate IDs are rejected.
         *
         * @param capability public descriptor
         * @param implementation non-null implementation
         * @param <T> capability contract
         * @return this builder
         */
        public <T> Builder add(DeviceCapability<T> capability, T implementation) {
            Objects.requireNonNull(capability, "capability");
            T checked = capability.type().cast(Objects.requireNonNull(implementation, "implementation"));
            if (bindings.putIfAbsent(capability.id(), new Binding(capability, checked)) != null) {
                throw new IllegalArgumentException("Duplicate capability binding: " + capability.id());
            }
            return this;
        }

        /** Copies the current bindings into an immutable set.
         * @return capability bindings
         */
        public CapabilitySet build() { return new CapabilitySet(bindings); }
    }
}
