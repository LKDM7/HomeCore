package fr.lkdm.homecore.development;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.example.ExampleMachineDevice;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Optional development-only mod; its source sets are excluded from release artifacts. */
@Mod(HomeCoreDebug.MOD_ID)
public final class HomeCoreDebug {
    /** Separate mod identity, never included in the HomeCore release JAR. */
    public static final String MOD_ID = "homecore_debug";
    private static final String NETWORK_NAME = "HomeCore Debug";
    private final Map<MinecraftServer, Map<UUID, ExampleMachineDevice.MemoryMachine>> machines = new IdentityHashMap<>();

    /** Registers development hooks only in a non-production launch. */
    public HomeCoreDebug() {
        if (FMLEnvironment.production) {
            LogUtils.getLogger().warn("HomeCore development helpers are disabled in production");
            return;
        }
        ExampleMachineDevice.registerCapability();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::serverStopping);
        LogUtils.getLogger().info("HomeCore development device available through /homecore_debug");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("homecore_debug")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    HomeNetwork network = install(player);
                    context.getSource().sendSuccess(() -> Component.literal("Debug Dashboard Device installed in "
                            + network.name() + " (" + network.id() + ")"), false);
                    return 1;
                }));
    }

    private HomeNetwork install(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!server.isSameThread()) throw new IllegalStateException("Debug setup requires the server thread");
        UUID deviceId = UUID.nameUUIDFromBytes(("homecore:debug_device:" + player.getUUID()).getBytes(StandardCharsets.UTF_8));
        var sources = machines.computeIfAbsent(server, ignored -> new HashMap<>());
        ExampleMachineDevice.MemoryMachine source = sources.computeIfAbsent(player.getUUID(), ignored -> new ExampleMachineDevice.MemoryMachine(deviceId));
        var registry = DashboardAPI.devices(server);
        if (registry.get(deviceId).isEmpty()) registry.register(new ExampleMachineDevice(source, DashboardAPI.events(server)));
        var networks = DashboardAPI.networks(server);
        HomeNetwork network = networks.getNetworksForPlayer(player.getUUID()).stream()
                .filter(candidate -> candidate.owner().equals(player.getUUID()) && candidate.name().equals(NETWORK_NAME))
                .findFirst().orElseGet(() -> networks.createNetwork(NETWORK_NAME, player.getUUID()));
        networks.addDevice(network.id(), deviceId);
        return networks.getNetwork(network.id()).orElseThrow();
    }

    private void serverStopping(ServerStoppingEvent event) {
        Map<UUID, ExampleMachineDevice.MemoryMachine> removed = machines.remove(event.getServer());
        if (removed == null) return;
        var registry = DashboardAPI.devices(event.getServer());
        for (var source : removed.values()) {
            source.invalidate();
            registry.unregister(source.persistentDeviceId());
        }
    }
}
