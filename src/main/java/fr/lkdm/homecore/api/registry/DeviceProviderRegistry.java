package fr.lkdm.homecore.api.registry;

import fr.lkdm.homecore.api.device.DashboardDevice;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Explicit adapters keyed by block entity type. No world or chunk scan occurs.
 * Register during setup; call discovery on the owning server thread from your
 * block entity's lifecycle. Providers must return the same persistent UUID across loads.
 */
public final class DeviceProviderRegistry {
    /** Creates an empty explicit adapter registry. */
    public DeviceProviderRegistry() { }

    private final Map<BlockEntityType<?>, Function<BlockEntity, DashboardDevice>> providers = new IdentityHashMap<>();

    /** Registers a type-safe adapter using an explicit runtime class.
     * @param type Minecraft block entity type
     * @param sourceClass runtime class of this type
     * @param provider adapter
     * @param <T> block entity class
     * @throws IllegalArgumentException if the type is already registered
     */
    public synchronized <T extends BlockEntity> void register(BlockEntityType<T> type, Class<T> sourceClass,
                                                              DeviceProvider<? super T> provider) {
        Objects.requireNonNull(sourceClass);
        add(type, source -> provider.create(sourceClass.cast(source)), provider);
    }

    /** Registers an adapter. Minecraft guarantees the block entity type's generic association.
     * @param type Minecraft block entity type
     * @param provider adapter for instances created by that type
     * @param <T> block entity class
     */
    public synchronized <T extends BlockEntity> void register(BlockEntityType<T> type, DeviceProvider<? super T> provider) {
        add(type, source -> provider.create(castSource(type, source)), provider);
    }

    private void add(BlockEntityType<?> type, Function<BlockEntity, DashboardDevice> adapter, Object provider) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(provider);
        if (providers.putIfAbsent(type, adapter) != null) throw new IllegalArgumentException("Provider already registered");
    }

    // The heterogeneous map erases T. This is the only cast, guarded by identity
    // against the exact BlockEntityType<T> supplied during typed registration.
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> T castSource(BlockEntityType<T> expected, BlockEntity source) {
        if (source.getType() != expected) throw new IllegalArgumentException("Wrong block entity type");
        return (T) source;
    }

    /** Adapts one explicitly supplied block entity, without automatically registering it.
     * @param source source on its owning server thread
     * @return device, or empty for an unsupported/removed source
     */
    public synchronized Optional<DashboardDevice> discover(BlockEntity source) {
        Objects.requireNonNull(source);
        if (source.isRemoved()) return Optional.empty();
        var provider = providers.get(source.getType());
        if (provider == null) return Optional.empty();
        DashboardDevice device = Objects.requireNonNull(provider.apply(source), "Provider returned null");
        return device.isValid() ? Optional.of(device) : Optional.empty();
    }

    /** Removes an adapter; existing devices remain the caller's responsibility.
     * @param type registered type
     * @return whether a provider was removed
     */
    public synchronized boolean unregister(BlockEntityType<?> type) { return providers.remove(type) != null; }

    /** Returns whether a type has an adapter.
     * @param type block entity type
     * @return registration state
     */
    public synchronized boolean contains(BlockEntityType<?> type) { return providers.containsKey(type); }
}
