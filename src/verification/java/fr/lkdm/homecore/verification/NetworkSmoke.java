package fr.lkdm.homecore.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.client.ClientDeviceCache;
import fr.lkdm.homecore.api.client.HomeCoreClient;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicitly enabled connected-client transport verification, excluded from release jars. */
@EventBusSubscriber(modid = HomeCoreValidation.MOD_ID, value = Dist.CLIENT)
public final class NetworkSmoke {
    private static final boolean DEBUG_SMOKE = Boolean.getBoolean("homecore.debugSmoke");
    private static final ResourceLocation PROGRESS = DEBUG_SMOKE ? ResourceLocation.fromNamespaceAndPath("homecore", "progress") : id("progress");
    private static final ResourceLocation ACTION = DEBUG_SMOKE ? ResourceLocation.fromNamespaceAndPath("homecore", "set_progress") : id("set_progress");
    private static volatile UUID network;
    private static volatile UUID deviceId = UUID.fromString("92c12521-24d1-4432-a4eb-0826c15f2187");
    private static volatile Throwable serverFailure;
    private static int stage;
    private static long started;
    private static long snapshots;
    private static UUID request;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homecore.networkSmoke") || stage == 5) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                stage = 1;
                started = System.nanoTime();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                var settings = new LevelSettings("HomeCore Transport Verification", GameType.CREATIVE,
                        false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("homecore-smoke-" + System.currentTimeMillis(),
                        settings, new WorldOptions(0L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (System.nanoTime() - started > 240_000_000_000L) throw new IllegalStateException("Network verification timed out at stage " + stage);
            if (serverFailure != null) throw new IllegalStateException("Fixture creation failed", serverFailure);
            if (stage == 1 && client.player != null && client.getSingleplayerServer() != null) {
                stage = 2;
                UUID player = client.player.getUUID();
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        if (DEBUG_SMOKE) {
                            var serverPlayer = server.getPlayerList().getPlayer(player);
                            if (serverPlayer == null) throw new IllegalStateException("Connected player missing");
                            int result = server.getCommands().getDispatcher().execute("homecore_debug", serverPlayer.createCommandSourceStack());
                            if (result != 1) throw new IllegalStateException("Debug command failed");
                            var home = DashboardAPI.networks(server).getNetworksForPlayer(player).stream()
                                    .filter(candidate -> candidate.owner().equals(player) && candidate.name().equals("HomeCore Debug"))
                                    .findFirst().orElseThrow();
                            if (home.devices().size() != 1) throw new IllegalStateException("Expected one debug device");
                            deviceId = home.devices().iterator().next();
                            network = home.id();
                            return;
                        }
                        var fixture = new Fixture();
                        DashboardAPI.devices(server).register(fixture);
                        var home = DashboardAPI.networks(server).createNetwork("Transport Verification", player);
                        DashboardAPI.networks(server).addDevice(home.id(), deviceId);
                        network = home.id();
                    } catch (Throwable failure) { serverFailure = failure; }
                });
            } else if (stage == 2 && network != null) {
                HomeCoreClient.requestDevices(Optional.of(network), 0);
                stage = 3;
            } else if (stage == 3 && ClientDeviceCache.INSTANCE.devices().containsKey(new ClientDeviceCache.DeviceKey(network, deviceId))) {
                var snapshot = ClientDeviceCache.INSTANCE.devices().get(new ClientDeviceCache.DeviceKey(network, deviceId));
                if (snapshot.data().getList("metrics", 10).isEmpty()) throw new IllegalStateException("Initial metrics missing");
                snapshots = ClientDeviceCache.INSTANCE.snapshotCount();
                request = HomeCoreClient.executeAction(network, deviceId, ACTION, 85.0);
                stage = 4;
            } else if (stage == 4) {
                boolean result = ClientDeviceCache.INSTANCE.recentMessages().stream()
                        .filter(HomeCorePayloads.ActionResultResponse.class::isInstance)
                        .map(HomeCorePayloads.ActionResultResponse.class::cast)
                        .anyMatch(response -> response.requestId().equals(request) && response.result().isSuccess());
                var delta = ClientDeviceCache.INSTANCE.metricUpdates().get(
                        new ClientDeviceCache.MetricKey(new ClientDeviceCache.DeviceKey(network, deviceId), PROGRESS));
                if (result && delta != null) {
                    Object expected = DEBUG_SMOKE ? new fr.lkdm.homecore.api.metric.Percentage(85) : Double.valueOf(85);
                    if (!expected.equals(delta.value().value())) throw new IllegalStateException("Unexpected metric delta " + delta.value());
                    if (DEBUG_SMOKE) {
                        boolean eventReceived = ClientDeviceCache.INSTANCE.recentMessages().stream()
                                .filter(HomeCorePayloads.DeviceEventNotification.class::isInstance)
                                .map(HomeCorePayloads.DeviceEventNotification.class::cast)
                                .anyMatch(notification -> notification.event().type().equals(ResourceLocation.fromNamespaceAndPath("homecore", "test_event")));
                        if (!eventReceived) return;
                        LogUtils.getLogger().info("HOMECORE_DEBUG_SMOKE_OK command=true percentage=85 event=homecore:test_event");
                    }
                    if (ClientDeviceCache.INSTANCE.snapshotCount() != snapshots) throw new IllegalStateException("Action resent a full snapshot");
                    LogUtils.getLogger().info("HOMECORE_NETWORK_SMOKE_OK snapshots={} deltas={} request={}", snapshots, ClientDeviceCache.INSTANCE.updateCount(), request);
                    stage = 5;
                    if (!Boolean.getBoolean("homecore.workbenchSmoke")) shutdown(client);
                }
            }
        } catch (Throwable failure) {
            stage = 5;
            LogUtils.getLogger().error("HOMECORE_NETWORK_SMOKE_FAILED", failure);
            shutdown(client);
        }
    }

    private static void shutdown(Minecraft client) {
        // Match the vanilla pause menu: close the connection before waiting for the integrated server.
        if (client.level != null) client.level.disconnect();
        client.disconnect();
        LogUtils.getLogger().info("HOMECORE_NETWORK_SMOKE_SHUTDOWN_OK");
        client.stop();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HomeCoreValidation.MOD_ID, path); }

    private static final class Fixture implements DashboardDevice {
        private final DeviceMetric<Double> progress = DeviceMetric.builder(PROGRESS, Component.literal("Progress"), MetricTypes.DOUBLE, 73.0)
                .updatePolicy(UpdatePolicy.ON_CHANGE).build();
        private final DeviceAction<Double> set = DeviceAction.slider(ACTION, Component.literal("Set progress"), 0, 100)
                .handler((context, value) -> { progress.setValue(value); return ActionResult.success(); }).build();
        @Override public UUID id() { return deviceId; }
        @Override public ResourceLocation deviceType() { return NetworkSmoke.id("temporary_fixture"); }
        @Override public Component displayName() { return Component.literal("Transport Verification Fixture"); }
        @Override public DeviceStatus status() { return DeviceStatus.of(DeviceStatus.State.ONLINE); }
        @Override public List<DeviceMetric<?>> metrics() { return List.of(progress); }
        @Override public List<DeviceAction<?>> actions() { return List.of(set); }
    }
}
