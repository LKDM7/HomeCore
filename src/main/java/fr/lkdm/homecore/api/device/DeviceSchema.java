package fr.lkdm.homecore.api.device;

import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Validated structural description of a device, including typed metric and action
 * definitions and available event identifiers. Lists are immutable. Metric values
 * remain live; consumers must capture values separately when creating snapshots.
 * Executable action definitions are for trusted server integrations, not transport.
 * @param metrics stable metric definitions with live observable values
 * @param actions stable descriptions and trusted handlers
 * @param events declared event identifiers
 */
public record DeviceSchema(List<DeviceMetric<?>> metrics, List<DeviceAction<?>> actions,
                           Set<ResourceLocation> events) {
    /** Copies collections and rejects null entries or duplicate metric/action identifiers. */
    public DeviceSchema {
        metrics = List.copyOf(metrics);
        actions = List.copyOf(actions);
        events = Set.copyOf(events);
        requireUnique(metrics, DeviceMetric::id);
        requireUnique(actions, DeviceAction::id);
    }

    /** Generates and validates the schema exposed by a device.
     * @param device source device with stable definitions
     * @return validated schema snapshot
     */
    public static DeviceSchema from(DashboardDevice device) {
        Objects.requireNonNull(device);
        return new DeviceSchema(device.metrics(), device.actions(), device.eventTypes());
    }

    private static <T> void requireUnique(List<T> values, Function<T, ResourceLocation> identity) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (T value : values) {
            ResourceLocation id = Objects.requireNonNull(identity.apply(value));
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate schema identifier: " + id);
        }
    }
}
