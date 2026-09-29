package fr.lkdm.homecore.verification;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Verifies that the HomeLink component item models are baked as three-dimensional voxel models. */
@EventBusSubscriber(modid = HomeCoreValidation.MOD_ID, value = Dist.CLIENT)
public final class HomeLinkModelSmoke {
    private static boolean checked;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (checked || !Boolean.getBoolean("homecore.networkSmoke")) return;
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof TitleScreen)) return;
        check(client, "homelink_circuit_board", 12);
        check(client, "homelink_microprocessor", 60);
        check(client, "homelink_communication_module", 60);
        check(client, "homelink_control_module", 50);
        check(client, "homelink_connector", 60, "block/device_steel");
        checked = true;
        LogUtils.getLogger().info("HOMECORE_HOMELINK_MODELS_OK");
    }

    private static void check(Minecraft client, String name, int minimumQuads) {
        check(client, name, minimumQuads, "item/" + name);
    }

    private static void check(Minecraft client, String name, int minimumQuads, String texture) {
        var location = ResourceLocation.fromNamespaceAndPath("homecore", name);
        var model = client.getModelManager().getModel(new ModelResourceLocation(location, "inventory"));
        var quads = model.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, null);
        var sprites = quads.stream().map(quad -> quad.getSprite().contents().name()).toList();
        if (!model.isGui3d() || quads.size() < minimumQuads
                || sprites.stream().anyMatch(sprite -> sprite.getPath().equals("missingno"))
                || !sprites.contains(ResourceLocation.fromNamespaceAndPath("homecore", texture))
                || !sprites.contains(ResourceLocation.fromNamespaceAndPath(
                        "homecore", "item/homelink_model_palette"))) {
            throw new IllegalStateException("HomeLink voxel item model did not bake: " + name
                    + " (quads=" + quads.size() + ", sprites=" + sprites + ")");
        }
    }
}
