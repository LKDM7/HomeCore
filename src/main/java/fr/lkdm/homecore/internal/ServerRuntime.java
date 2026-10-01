package fr.lkdm.homecore.internal;

import fr.lkdm.homecore.api.registry.DeviceRegistry;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/** Owns ephemeral state for each server, released on server stop. */
public final class ServerRuntime {
    private static final Map<MinecraftServer, ServerRuntime> INSTANCES = new IdentityHashMap<>();
    private final DeviceRegistry devices = new DeviceRegistry();
    private final fr.lkdm.homecore.api.event.DeviceEventBus events = new fr.lkdm.homecore.api.event.DeviceEventBus();
    private final fr.lkdm.homecore.api.security.RateLimiter actionLimiter = new fr.lkdm.homecore.api.security.RateLimiter();
    private final fr.lkdm.homecore.api.security.PermissionValidator permissions = new fr.lkdm.homecore.api.security.PermissionValidator();
    private final fr.lkdm.homecore.api.production.ProductionLog production = new fr.lkdm.homecore.api.production.ProductionLog();
    private fr.lkdm.homecore.network.ServerSync sync;

    private ServerRuntime() { }

    public static synchronized ServerRuntime get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("HomeCore requires the server thread");
        return INSTANCES.computeIfAbsent(server, ignored -> new ServerRuntime());
    }

    public static synchronized void stop(MinecraftServer server) {
        ServerRuntime runtime = INSTANCES.remove(server);
        if (runtime != null) {
            if (runtime.sync != null) runtime.sync.close();
            runtime.devices.clear();
            runtime.events.close();
            runtime.production.close();
            runtime.actionLimiter.clear();
        }
    }

    public DeviceRegistry devices() { return devices; }
    public fr.lkdm.homecore.api.event.DeviceEventBus events() { return events; }
    public fr.lkdm.homecore.api.production.ProductionLog production() { return production; }
    public fr.lkdm.homecore.api.security.PermissionValidator permissions() { return permissions; }
    public fr.lkdm.homecore.api.security.ActionExecutor actions(MinecraftServer server) {
        return new fr.lkdm.homecore.api.security.ActionExecutor(devices,
                HomeNetworkSavedData.get(server).manager(), permissions, actionLimiter);
    }

    public fr.lkdm.homecore.network.ServerSync sync(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("HomeCore requires the server thread");
        if (sync == null) sync = new fr.lkdm.homecore.network.ServerSync(server, devices,
                HomeNetworkSavedData.get(server).manager(), permissions, actions(server), events);
        return sync;
    }

    public static synchronized void tick(MinecraftServer server) {
        ServerRuntime runtime = INSTANCES.get(server);
        if (runtime != null && runtime.sync != null) runtime.sync.tick();
    }

    public static synchronized void playerLeft(MinecraftServer server, java.util.UUID player) {
        ServerRuntime runtime = INSTANCES.get(server);
        if (runtime != null && runtime.sync != null) runtime.sync.playerLeft(player);
    }
}
