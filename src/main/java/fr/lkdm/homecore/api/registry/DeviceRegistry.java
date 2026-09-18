package fr.lkdm.homecore.api.registry;

import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Explicit registry of logical devices, preserving registration order.
 * It never scans worlds or block entities. Invalid devices are removed lazily on
 * lookup; integrations should also unregister removed devices promptly.
 *
 * <p>Map operations are synchronized, but callbacks access device implementations.
 * Call this registry on the owning server thread whenever devices access Minecraft
 * state. Synchronization does not make a backing world safe for asynchronous access.
 * Device callbacks must not mutate this registry.</p>
 */
public final class DeviceRegistry {
    /** Creates an empty live device registry. */
    public DeviceRegistry() { }

    private final Map<UUID, DashboardDevice> devices = new LinkedHashMap<>();

    /**
     * Registers a valid device after checking its identity and public definitions.
     * Its ID, type and schema definitions must remain stable until unregistered.
     *
     * @param device device to register
     * @throws IllegalArgumentException if invalid or its UUID is already registered,
     *         even when the same instance is passed twice
     * @throws NullPointerException if a required public property is null
     */
    public synchronized void register(DashboardDevice device) {
        Objects.requireNonNull(device, "device");
        UUID id = Objects.requireNonNull(device.id(), "device.id");
        if (devices.containsKey(id)) throw new IllegalArgumentException("Duplicate device UUID: " + id);
        if (!device.isValid()) throw new IllegalArgumentException("Cannot register an invalid device: " + id);
        Objects.requireNonNull(device.deviceType(), "device.deviceType");
        Objects.requireNonNull(device.displayName(), "device.displayName");
        Objects.requireNonNull(device.status(), "device.status");
        Objects.requireNonNull(device.position(), "device.position");
        Objects.requireNonNull(device.dimension(), "device.dimension");
        Objects.requireNonNull(device.schema(), "device.schema");
        DeviceSchema.from(device);
        if (!id.equals(device.id())) throw new IllegalArgumentException("Device identity changed during registration");
        devices.put(id, device);
    }

    /**
     * Removes a device by its registered identity, whether or not it remains valid.
     *
     * @param id registered UUID
     * @return removed device, or empty if absent
     */
    public synchronized Optional<DashboardDevice> unregister(UUID id) {
        return Optional.ofNullable(devices.remove(Objects.requireNonNull(id, "id")));
    }

    /**
     * Looks up a device and removes it if invalid or its identity has changed.
     *
     * @param id registered UUID
     * @return valid device, or empty
     */
    public synchronized Optional<DashboardDevice> get(UUID id) {
        Objects.requireNonNull(id, "id");
        DashboardDevice device = devices.get(id);
        if (device != null && !valid(id, device)) {
            devices.remove(id);
            device = null;
        }
        return Optional.ofNullable(device);
    }

    /**
     * Returns an immutable membership snapshot of valid devices in registration order.
     * The devices themselves remain live objects. Invalid entries are removed.
     *
     * @return currently valid devices
     */
    public synchronized List<DashboardDevice> getAll() {
        devices.entrySet().removeIf(entry -> !valid(entry.getKey(), entry.getValue()));
        return List.copyOf(devices.values());
    }

    /**
     * Returns valid devices with the requested type in registration order.
     *
     * @param type namespaced device type
     * @return immutable membership snapshot
     */
    public synchronized List<DashboardDevice> findByType(ResourceLocation type) {
        Objects.requireNonNull(type, "type");
        List<DashboardDevice> matches = new ArrayList<>();
        for (DashboardDevice device : getAll()) {
            if (type.equals(device.deviceType())) matches.add(device);
        }
        return List.copyOf(matches);
    }

    /** Releases all registry references, for example when the owning server stops. */
    public synchronized void clear() { devices.clear(); }

    private static boolean valid(UUID registeredId, DashboardDevice device) {
        return device.isValid() && registeredId.equals(device.id());
    }
}
