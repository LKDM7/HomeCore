package fr.lkdm.homecore;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Entry point shared by the client and dedicated server. */
@Mod(HomeCore.MOD_ID)
public final class HomeCore {
    public static final String MOD_ID = "homecore";
    private static final Logger LOGGER = LogUtils.getLogger();

    public HomeCore(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(fr.lkdm.homecore.network.PayloadRegistration::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::serverStopped);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::serverTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::playerLeft);
        LOGGER.info("HomeCore initialized (API {})", DashboardAPI.API_VERSION);
    }

    private void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        fr.lkdm.homecore.internal.ServerRuntime.stop(event.getServer());
    }

    private void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        fr.lkdm.homecore.internal.ServerRuntime.tick(event.getServer());
    }

    private void playerLeft(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            fr.lkdm.homecore.internal.ServerRuntime.playerLeft(player.server, player.getUUID());
        }
    }

}
