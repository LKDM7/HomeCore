package fr.lkdm.homelink.integration.client;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.client.ui.HomeLinkButton;
import fr.lkdm.homecore.api.client.ui.HomeLinkScreenLayout;
import fr.lkdm.homecore.api.client.ui.HomeLinkStatusTone;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;
import fr.lkdm.homelink.integration.HomeLinkIntegration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** Shared-kit rendering with all production consumers loaded, without creating a world. */
@EventBusSubscriber(modid = HomeLinkIntegration.ID, value = Dist.CLIENT)
public final class IntegrationClientSmoke {
    private static final List<String> MODS = List.of("homecore", "homelink_dashboard", "homelink_storage",
            "homelink_farm", "homelink_quarry", "homelink_energy", "homelink_tasks");
    private static boolean done;
    private static int stage, ticks, frames;
    private static long started;
    private static Path capture;

    private IntegrationClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homelink.integrationSmoke") || done) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > 120_000_000_000L)
                throw new IllegalStateException("Integration client smoke timed out at stage " + stage);
            if (stage == 0) {
                if (client.getOverlay() != null) return;
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                for (String mod : MODS)
                    if (!ModList.get().isLoaded(mod)) throw new IllegalStateException("Production mod not loaded: " + mod);
                client.options.guiScale().set(2);
                client.resizeDisplay();
                client.setScreen(new KitScreen());
                stage = 1;
                ticks = 0;
            } else if (stage == 1) {
                ticks++;
            } else if (stage == 2 && ++ticks >= 8) {
                if (capture == null || !Files.isRegularFile(capture) || Files.size(capture) == 0)
                    throw new IllegalStateException("Integration framebuffer capture is missing");
                LogUtils.getLogger().info("HOMELINK_INTEGRATION_CLIENT_OK mods={} keyboard=true input=true framebuffer=true screenshot={}",
                        MODS, capture.getFileName());
                done = true;
                client.stop();
            }
        } catch (Throwable failure) {
            fail(client, failure);
        }
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("homelink.integrationSmoke") || done || stage != 1) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null || !(client.screen instanceof KitScreen screen)) { frames = 0; return; }
        if (++frames < 8 || ticks < 12) return;
        try {
            screen.verify();
            capture(client, screen);
            stage = 2;
            ticks = 0;
        } catch (Throwable failure) {
            fail(client, failure);
        }
    }

    private static void capture(Minecraft client, KitScreen screen) throws IOException {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int guiX = screen.layout.x() + 8;
            int guiY = screen.layout.y() + HomeLinkTheme.HEADER_HEIGHT + 2;
            double scale = client.getWindow().getGuiScale();
            int pixelX = (int) Math.floor((guiX + 0.5) * scale);
            int pixelY = (int) Math.floor((guiY + 0.5) * scale);
            int actual = image.getPixelRGBA(pixelX, pixelY);
            int argb = HomeLinkTheme.BACKGROUND;
            int expected = (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
            if ((actual & 0xFFFFFF) != (expected & 0xFFFFFF))
                throw new IllegalStateException("Shared frame was not rendered: " + Integer.toHexString(actual));
            capture = client.gameDirectory.toPath().resolve("screenshots/homelink-integration-ui.png");
            Files.createDirectories(capture.getParent());
            image.writeToFile(capture);
        }
    }

    private static void fail(Minecraft client, Throwable failure) {
        done = true;
        LogUtils.getLogger().error("HOMELINK_INTEGRATION_CLIENT_FAILED", failure);
        client.stop();
    }

    private static Component text(String key) { return Component.translatable("ui.homelink_integration." + key); }

    private static final class KitScreen extends Screen {
        private HomeLinkScreenLayout layout;
        private HomeLinkButton action;
        private EditBox input;
        private int presses;

        private KitScreen() { super(text("title")); }

        @Override protected void init() {
            layout = HomeLinkScreenLayout.fit(width, height, 360, 220);
            action = addRenderableWidget(HomeLinkButton.builder(text("action"), button -> presses++)
                    .bounds(layout.contentX() + 8, layout.contentY() + 8, 144, HomeLinkTheme.CONTROL_HEIGHT).build());
            input = addRenderableWidget(HomeLinkUi.input(new EditBox(font,
                    layout.contentX() + 8, layout.contentY() + 38, 200, HomeLinkTheme.CONTROL_HEIGHT, text("input"))));
            setFocused(action);
            action.setFocused(true);
        }

        private void verify() {
            if (Minecraft.getInstance().level != null) throw new IllegalStateException("Smoke opened a world");
            if (title.getString().equals("ui.homelink_integration.title"))
                throw new IllegalStateException("Integration translations not loaded");
            if (!action.isFocused()) throw new IllegalStateException("Keyboard focus missing");
            int before = presses;
            if (!keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0) || presses != before + 1)
                throw new IllegalStateException("Vanilla keyboard activation failed");
            keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            if (getFocused() != input || !input.isFocused())
                throw new IllegalStateException("Native tab traversal did not reach the field");
            if (!charTyped('x', 0) || !input.getValue().equals("x"))
                throw new IllegalStateException("Native text field input failed");
        }

        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF141617);
            HomeLinkUi.frame(graphics, layout.x(), layout.y(), layout.width(), layout.height());
            HomeLinkUi.panel(graphics, layout.contentX(), layout.contentY(), layout.contentWidth(), layout.contentHeight());
            graphics.drawString(font, title, layout.x() + 14, layout.y() + 11, HomeLinkTheme.TEXT, false);
            HomeLinkUi.statusDot(graphics, layout.contentX() + 8, layout.contentY() + 72, HomeLinkStatusTone.ONLINE);
            graphics.drawString(font, text("loaded"), layout.contentX() + 20, layout.contentY() + 72, HomeLinkTheme.TEXT, false);
        }

        @Override public boolean isPauseScreen() { return false; }
    }
}
