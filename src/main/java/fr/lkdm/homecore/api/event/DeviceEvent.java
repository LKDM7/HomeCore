package fr.lkdm.homecore.api.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable event with structured, named string fields.
 * Field names and meanings belong to the event type's contract. Values are strings,
 * not executable data or serialized Java objects. A maximum of 64 fields is allowed.
 * @param type namespaced event identifier
 * @param source originating device identity
 * @param timestamp event creation time
 * @param severity importance of the event
 * @param data immutable field mapping; keys are 1..128 and values 0..4096 UTF-16 units
 */
public record DeviceEvent(ResourceLocation type, UUID source, Instant timestamp,
                          Severity severity, Map<String, String> data) {
    /** Severity independent of the device's current operational status. */
    public enum Severity {
        /** Informational change. */
        INFO,
        /** Condition requiring attention. */
        WARNING,
        /** Urgent condition. */
        CRITICAL
    }

    /** Validates and defensively copies the event fields. */
    public DeviceEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(severity, "severity");
        data = Map.copyOf(data);
        if (data.size() > 64) throw new IllegalArgumentException("At most 64 event fields are allowed");
        for (var entry : data.entrySet()) {
            if (entry.getKey().isEmpty() || entry.getKey().length() > 128 || entry.getValue().length() > 4096) {
                throw new IllegalArgumentException("Event field exceeds its size limit");
            }
        }
    }
}
