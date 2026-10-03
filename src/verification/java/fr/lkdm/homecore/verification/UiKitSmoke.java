package fr.lkdm.homecore.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.client.ui.HomeLinkButton;
import fr.lkdm.homecore.api.client.ui.HomeLinkScreenLayout;
import fr.lkdm.homecore.api.client.ui.HomeLinkStatusTone;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** World-free rendering and vanilla input verification, excluded from release artifacts. */
@EventBusSubscriber(modid = HomeCoreValidation.MOD_ID, value = Dist.CLIENT)
public final class UiKitSmoke {
    private static final List<String> CAPTURES = new ArrayList<>();
    private static boolean done;
    private static int stage, ticks, renderedFrames;
    private static long started;

    private UiKitSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homecore.uiSmoke") || done) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            if (System.nanoTime() - started > 120_000_000_000L)
                throw new IllegalStateException("UI smoke timed out at stage " + stage);
            if (stage == 0) {
                if (client.getOverlay() != null) return;
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                resize(client, 2);
                client.setScreen(new DemoScreen());
                stage = 1;
                ticks = 0;
            } else if (stage == 1 || stage == 2) {
                ticks++;
            } else if (stage == 3 && ++ticks >= 12) {
                for (String name : CAPTURES) {
                    Path file = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
                    if (!Files.isRegularFile(file) || Files.size(file) == 0)
                        throw new IllegalStateException("Missing framebuffer capture " + name);
                }
                LogUtils.getLogger().info("HOMECORE_UI_SMOKE_OK language={} sizes=640x360,427x240 states=8 input=true keyboard=true screenshots={}",
                        client.getLanguageManager().getSelected(), CAPTURES.size());
                done = true;
                client.stop();
            }
        } catch (Throwable failure) {
            done = true;
            LogUtils.getLogger().error("HOMECORE_UI_SMOKE_FAILED", failure);
            client.stop();
        }
    }

    /** Capture only completed, unobscured frames on the render thread. Run on an isolated desktop. */
    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("homecore.uiSmoke") || done || (stage != 1 && stage != 2)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null || !(client.screen instanceof DemoScreen demo)) {
            renderedFrames = 0;
            return;
        }
        if (++renderedFrames < 8 || ticks < 12) return;
        try {
            demo.verify();
            capture(client, demo, stage == 1 ? "large" : "small");
            if (stage == 1) resize(client, 3);
            stage++;
            ticks = 0;
            renderedFrames = 0;
        } catch (Throwable failure) {
            done = true;
            LogUtils.getLogger().error("HOMECORE_UI_SMOKE_FAILED", failure);
            client.stop();
        }
    }

    private static DemoScreen requireDemo(Minecraft client) {
        if (!(client.screen instanceof DemoScreen demo)) throw new IllegalStateException("UI demo screen was replaced");
        return demo;
    }

    private static void resize(Minecraft client, int scale) {
        client.options.guiScale().set(scale);
        client.resizeDisplay();
    }

    private static void capture(Minecraft client, DemoScreen demo, String size) throws java.io.IOException {
        String name = "homecore-ui-" + client.getLanguageManager().getSelected() + "-" + size + ".png";
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            assertPixel(image, client, demo.layout.x() + 8, demo.layout.y() + 31, HomeLinkTheme.BACKGROUND);
            assertPixel(image, client, demo.layout.contentX() + 2, demo.layout.contentY() + 3, HomeLinkTheme.SURFACE);
            AbstractWidget normal = demo.controls.getFirst();
            assertPixel(image, client, normal.getX() + 3, normal.getY() + 3, 0xFF474A4D);
            Path file = client.gameDirectory.toPath().resolve("screenshots").resolve(name);
            Files.createDirectories(file.getParent());
            image.writeToFile(file);
        }
        CAPTURES.add(name);
        LogUtils.getLogger().info("HOMECORE_UI_CAPTURE_OK file={} framebufferColors=true", name);
    }

    private static void assertPixel(com.mojang.blaze3d.platform.NativeImage image, Minecraft client,
            int guiX, int guiY, int expectedArgb) {
        double scale = client.getWindow().getGuiScale();
        int pixelX = (int) Math.floor((guiX + 0.5) * scale);
        int pixelY = (int) Math.floor((guiY + 0.5) * scale);
        int rgba = image.getPixelRGBA(pixelX, pixelY);
        int expectedRgba = (expectedArgb & 0xFF00FF00) | ((expectedArgb >>> 16) & 0xFF) | ((expectedArgb & 0xFF) << 16);
        if ((rgba & 0xFFFFFF) != (expectedRgba & 0xFFFFFF))
            throw new IllegalStateException("UI frame not rendered at " + pixelX + "," + pixelY
                    + ": expected " + Integer.toHexString(expectedRgba) + ", received " + Integer.toHexString(rgba));
    }

    /** A consumer-style screen using the public kit directly, without a base-screen abstraction. */
    private static final class DemoScreen extends Screen {
        private final List<AbstractWidget> controls = new ArrayList<>();
        private HomeLinkScreenLayout layout;
        private HomeLinkButton hovered, focused, disabled;
        private EditBox input;
        private int presses;

        private DemoScreen() { super(text("title")); }

        @Override protected void init() {
            controls.clear();
            layout = HomeLinkScreenLayout.fit(width, height, HomeLinkTheme.DEFAULT_MAX_WIDTH, HomeLinkTheme.DEFAULT_MAX_HEIGHT);
            int columnWidth = (layout.contentWidth() - 22) / 2;
            HomeLinkButton normal = button("normal", 0, columnWidth);
            hovered = button("hover", 1, columnWidth);
            hovered.setTooltip(null);
            disabled = button("disabled", 2, columnWidth);
            disabled.active = false;
            button("selected", 3, columnWidth).selected(true);
            button("tab_active", 4, columnWidth).navigation(true);
            button("tab_other", 5, columnWidth).navigation(false);
            focused = button("focus", 6, columnWidth);
            button("long", 7, columnWidth);
            setFocused(focused);
            focused.setFocused(true);
            input = HomeLinkUi.input(new EditBox(font, layout.contentX() + 8, layout.contentY() + 92,
                    layout.contentWidth() - 16, HomeLinkTheme.CONTROL_HEIGHT, text("input")));
            input.setValue(text("input").getString());
            controls.add(addRenderableWidget(input));
            normal.setTooltip(null);
        }

        private HomeLinkButton button(String key, int index, int columnWidth) {
            HomeLinkButton button = HomeLinkButton.builder(text(key), ignored -> presses++)
                    .bounds(layout.contentX() + 8 + (index % 2) * (columnWidth + 6),
                            layout.contentY() + 8 + (index / 2) * 21, columnWidth, HomeLinkTheme.CONTROL_HEIGHT).build();
            controls.add(addRenderableWidget(button));
            return button;
        }

        private void verify() {
            if (client().level != null) throw new IllegalStateException("UI smoke unexpectedly opened a world");
            if (title.getString().equals("ui.homecore_validation.title")
                    || text("long").getString().equals("ui.homecore_validation.long"))
                throw new IllegalStateException("Localized UI verification resources were not loaded");
            for (AbstractWidget widget : controls) {
                if (widget.getX() < 0 || widget.getY() < 0 || widget.getWidth() <= 0 || widget.getHeight() <= 0
                        || widget.getX() + widget.getWidth() > width || widget.getY() + widget.getHeight() > height)
                    throw new IllegalStateException("Control outside viewport: " + widget.getMessage().getString());
            }
            if (font.width(HomeLinkUi.clip(font, text("long").getString(), 28)) > 28)
                throw new IllegalStateException("Ellipsis exceeded available width");
            int before = presses;
            if (!focused.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0) || presses != before + 1)
                throw new IllegalStateException("Vanilla keyboard activation was lost");
            disabled.setFocused(true);
            disabled.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            disabled.setFocused(false);
            if (presses != before + 1) throw new IllegalStateException("Disabled control activated");
            keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
            if (getFocused() == focused || getFocused() == disabled || getFocused() == null)
                throw new IllegalStateException("Vanilla tab traversal was lost");
            setFocused(focused);
            focused.setFocused(true);
            if (!focused.isFocused() || !input.getValue().equals(text("input").getString()))
                throw new IllegalStateException("Focus or text field state was lost");
            LogUtils.getLogger().info("HOMECORE_UI_VIEWPORT_OK width={} height={} frame={}x{}", width, height, layout.width(), layout.height());
        }

        private Minecraft client() { return Minecraft.getInstance(); }

        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF141617);
            HomeLinkUi.frame(graphics, layout.x(), layout.y(), layout.width(), layout.height());
            HomeLinkUi.mark(graphics, layout.x() + 14, layout.y() + 7, HomeLinkTheme.ACCENT);
            graphics.drawString(font, title, layout.x() + 36, layout.y() + 11, HomeLinkTheme.TEXT, false);
            HomeLinkUi.panel(graphics, layout.contentX(), layout.contentY(), layout.contentWidth(), layout.contentHeight());
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Synthetic hover exercises rendering without moving the user's desktop pointer.
            super.render(graphics, hovered.getX() + 4, hovered.getY() + 4, partialTick);
            int x = layout.contentX() + 8, y = layout.contentY() + 117;
            HomeLinkStatusTone[] tones = HomeLinkStatusTone.values();
            int step = (layout.contentWidth() - 16) / tones.length;
            for (int i = 0; i < tones.length; i++) {
                HomeLinkUi.statusDot(graphics, x + i * step, y, tones[i]);
                graphics.drawString(font, HomeLinkUi.clip(font, text(tones[i].name().toLowerCase(java.util.Locale.ROOT)).getString(), step - 15),
                        x + i * step + 11, y, HomeLinkTheme.TEXT, false);
            }
            HomeLinkUi.progressBar(graphics, x, y + 16, layout.contentWidth() - 16, 5, 0.62, HomeLinkTheme.ACCENT);
            graphics.drawString(font, text("footer"), layout.x() + 12, layout.footerY() + 10, HomeLinkTheme.MUTED, false);
        }

        @Override public boolean isPauseScreen() { return false; }
    }

    private static Component text(String key) { return Component.translatable("ui.homecore_validation." + key); }
}
