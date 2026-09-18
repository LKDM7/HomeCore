package fr.lkdm.homecore.api.security;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Permissions understood by HomeCore's server-side authorization layer. */
public enum Permission {
    /** Read networks and device data. */
    VIEW("view"),
    /** Invoke device controls. */
    CONTROL("control"),
    /** Configure automated control. */
    AUTOMATE("automate"),
    /** Change device configuration. */
    CONFIGURE("configure"),
    /** Manage network membership and settings. */
    MANAGE_NETWORK("manage_network");

    private final ResourceLocation id;
    Permission(String path) { id = ResourceLocation.fromNamespaceAndPath("homecore", path); }

    /** Returns the stable identifier used in action metadata.
     * @return permission identifier
     */
    public ResourceLocation id() { return id; }

    /** Resolves a known permission; unknown identifiers must be denied.
     * @param id candidate identifier
     * @return known permission or empty
     */
    public static Optional<Permission> fromId(ResourceLocation id) {
        for (Permission permission : values()) if (permission.id.equals(id)) return Optional.of(permission);
        return Optional.empty();
    }
}
