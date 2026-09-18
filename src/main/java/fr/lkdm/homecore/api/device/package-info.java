/**
 * Logical device identities, availability and self-describing schemas.
 * Devices need not be block entities or have a position. Identity and definitions
 * remain stable while registered; current metric values and availability may vary.
 * Read or mutate a Minecraft-backed device only on its owning server thread.
 * Integrations must unregister devices when their backing objects are removed.
 */
package fr.lkdm.homecore.api.device;
