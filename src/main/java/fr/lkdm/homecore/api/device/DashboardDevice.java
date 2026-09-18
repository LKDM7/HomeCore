package fr.lkdm.homecore.api.device;

import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A logical device; no block entity or world location is required.
 * Identity must remain stable for the lifetime of the device. Read and mutate
 * Minecraft-backed devices on their owning server thread. Returned definitions
 * must remain stable while registered; metric values may change.
 */
public interface DashboardDevice {
    /** Stable persistent identity, independent of chunk load cycles.
     * @return persistent UUID
     */
    UUID id();
    /** Namespaced device kind owned by the integrating mod.
     * @return device kind
     */
    ResourceLocation deviceType();
    /** User-facing label.
     * @return display label
     */
    Component displayName();
    /** Current availability and optional explanation.
     * @return availability snapshot
     */
    DeviceStatus status();
    /** Typed metrics exposed by this device.
     * @return stable metric definitions
     */
    default List<DeviceMetric<?>> metrics() { return List.of(); }
    /** Commands exposed by this device. Direct execution is for trusted server code only.
     * @return stable action definitions
     */
    default List<DeviceAction<?>> actions() { return List.of(); }
    /** Event identifiers this device can publish.
     * @return declared event identifiers
     */
    default Set<ResourceLocation> eventTypes() { return Set.of(); }
    /** Identifiers of optional behaviors offered by this device.
     * @return capability identifiers
     */
    default Set<ResourceLocation> capabilities() { return Set.of(); }
    /** Queries a typed optional behavior without exposing implementation packages.
     * @param capability registered descriptor
     * @param <T> capability contract
     * @return implementation when supported
     */
    default <T> Optional<T> capability(fr.lkdm.homecore.api.capability.DeviceCapability<T> capability) {
        return Optional.empty();
    }
    /** Optional block position; return immutable positions.
     * @return immutable position or empty for an unlocated device
     */
    default Optional<BlockPos> position() { return Optional.empty(); }
    /** Optional world dimension.
     * @return dimension or empty
     */
    default Optional<ResourceKey<Level>> dimension() { return Optional.empty(); }
    /** False when a backing object has been removed or permanently invalidated.
     * @return whether the device remains usable by its integration
     */
    default boolean isValid() { return true; }
    /** Describes the controls and telemetry without accessing implementation details.
     * @return validated schema
     */
    default DeviceSchema schema() { return DeviceSchema.from(this); }
}
