package fr.lkdm.homecore.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.registry.HomeCoreWorkbench;
import fr.lkdm.homecore.workbench.ElectronicsBlock;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Exercises Minecraft's actual baked models and atlas sprites for every workbench state. */
@EventBusSubscriber(modid = HomeCoreValidation.MOD_ID, value = Dist.CLIENT)
public final class WorkbenchModelSmoke {
    private static boolean checked;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (checked || !Boolean.getBoolean("homecore.workbenchSmoke")
                || !Boolean.getBoolean("homecore.networkSmoke")) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int stage = 0; stage < 4; stage++) check(client, facing, stage);
        }
        checked = true;
        LogUtils.getLogger().info("HOMECORE_WORKBENCH_MODELS_OK");
    }

    private static void check(Minecraft client, Direction facing, int stage) {
        var state = HomeCoreWorkbench.BLOCK.get().defaultBlockState()
                .setValue(ElectronicsBlock.FACING, facing).setValue(ElectronicsBlock.STAGE, stage);
        var model = client.getBlockRenderer().getBlockModel(state);
        var quads = new ArrayList<BakedQuad>();
        quads.addAll(model.getQuads(state, null, RandomSource.create(0), ModelData.EMPTY, null));
        for (Direction side : Direction.values()) {
            quads.addAll(model.getQuads(state, side, RandomSource.create(0), ModelData.EMPTY, null));
        }
        var sprites = quads.stream().map(quad -> quad.getSprite().contents().name()).toList();
        var expectedFront = ResourceLocation.fromNamespaceAndPath("homecore",
                "block/electronics_workbench_front_" + stage);
        if (quads.size() < 60 || !sprites.contains(expectedFront)
                || sprites.stream().anyMatch(sprite -> sprite.getPath().contains("missingno"))) {
            throw new IllegalStateException("Workbench model failed: " + facing + "/" + stage
                    + " (quads=" + quads.size() + ", sprites=" + sprites + ")");
        }
    }
}
