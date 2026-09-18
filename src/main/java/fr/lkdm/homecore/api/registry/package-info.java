/**
 * Explicit device and provider registration without continuous world scanning.
 * Register providers during integration setup and release device registrations
 * when backing objects unload or the owning server stops. Synchronized registry
 * operations do not make Minecraft worlds thread-safe: invoke device callbacks
 * on the owning server thread and keep device identities stable while registered.
 */
package fr.lkdm.homecore.api.registry;
