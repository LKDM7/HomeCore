package fr.lkdm.homecore.api.registry;

import fr.lkdm.homecore.api.device.DashboardDevice;

/** Adapts an external object to a logical device without transferring its ownership.
 * @param <T> adapted source type
 */
@FunctionalInterface
public interface DeviceProvider<T> {
    /** Creates a device with stable identity for this source.
     * @param source source owned by the integrating mod
     * @return non-null device
     */
    DashboardDevice create(T source);
}
