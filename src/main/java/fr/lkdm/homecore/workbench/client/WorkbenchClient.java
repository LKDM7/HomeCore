package fr.lkdm.homecore.workbench.client;

import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Installs the workbench screen only on physical clients. */
@EventBusSubscriber(modid = "homecore", value = Dist.CLIENT)
public final class WorkbenchClient {
    private WorkbenchClient() { }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(HomeCoreWorkbench.MENU.get(), ElectronicsScreen::new);
    }
}
