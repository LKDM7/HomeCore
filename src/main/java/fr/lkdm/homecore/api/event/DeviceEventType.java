package fr.lkdm.homecore.api.event;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Human-readable definition of an event identifier.
 * @param id namespaced type identifier
 * @param displayName user-facing label
 */
public record DeviceEventType(ResourceLocation id, Component displayName) {
    /** Creates a definition with a defensive copy of its label. */
    public DeviceEventType {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").copy();
    }

    /** Returns a defensive copy of the display label.
     * @return event label
     */
    @Override
    public Component displayName() { return displayName.copy(); }
}
