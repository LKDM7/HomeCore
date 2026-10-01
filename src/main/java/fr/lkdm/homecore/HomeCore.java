package fr.lkdm.homecore;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.registry.HomeCoreCreativeTabs;
import fr.lkdm.homecore.registry.HomeCoreItems;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Entry point shared by the client and dedicated server. */
@Mod(HomeCore.MOD_ID)
public final class HomeCore {
    public static final String MOD_ID = "homecore";
    private static final Logger LOGGER = LogUtils.getLogger();

    public HomeCore(net.neoforged.bus.api.IEventBus modBus) {
        HomeCoreItems.ITEMS.register(modBus);
        fr.lkdm.homecore.registry.HomeCoreWorkbench.register(modBus);
        fr.lkdm.homecore.registry.HomeCoreRecipes.TYPES.register(modBus);
        fr.lkdm.homecore.registry.HomeCoreRecipes.SERIALIZERS.register(modBus);
        HomeCoreCreativeTabs.TABS.register(modBus);
        modBus.addListener(fr.lkdm.homecore.network.PayloadRegistration::register);
        modBus.addListener(fr.lkdm.homecore.workbench.WorkbenchNetworking::register);
        modBus.addListener(HomeCore::setupContracts);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::serverStopped);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::serverTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::playerLeft);
        LOGGER.info("HomeCore initialized (API {})", DashboardAPI.API_VERSION);
    }

    /** Publishes the neutral contracts consumer mods read, once registries are frozen. */
    private static void setupContracts(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DashboardAPI.capabilities().register(fr.lkdm.homecore.api.stock.StockProvider.CAPABILITY);
            fr.lkdm.homecore.api.recipe.RecipeDescriptors.register(
                    fr.lkdm.homecore.registry.HomeCoreRecipes.TYPE.get(),
                    fr.lkdm.homecore.workbench.recipe.ElectronicsDescriptors::describe);
        });
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
