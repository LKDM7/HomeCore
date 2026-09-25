package fr.lkdm.homecore.workbench.client;

import fr.lkdm.homecore.workbench.ElectronicsMenu;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Native container screen; all inventory and assembly mutations remain server requests. */
public final class ElectronicsScreen extends AbstractContainerScreen<ElectronicsMenu> {
    private static final int BACK = 0xff202328, PANEL = 0xff2c3035, LINE = 0xff45494d;
    private static final int TEXT = 0xffece8df, MUTED = 0xffa8aaa9, COPPER = 0xffd3986c;
    private final List<Button> quantityButtons = new ArrayList<>();
    private Button assemble;
    private int shownPhase = -1, acknowledgedMask, heldPart = -1;
    private String shownRecipe = "";
    private boolean moved;
    private int partOffset, materialOffset;
    private int pendingQuantity = -1, pendingTicks;
    private double dragX, dragY;

    public ElectronicsScreen(ElectronicsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ElectronicsMenu.WIDTH;
        imageHeight = ElectronicsMenu.HEIGHT;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.homecore.electronics." + key, args);
    }

    @Override protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        quantityButtons.clear();
        shownPhase = menu.phase();
        shownRecipe = menu.selectedRecipe().map(h -> h.id().toString()).orElse("");
        heldPart = -1;
        pendingQuantity = -1;
        partOffset = 0;
        materialOffset = 0;
        assemble = null;
        if (shownPhase == 0) {
            button(9, 28, 20, "<", b -> cycle(-1));
            button(291, 28, 20, ">", b -> cycle(1));
            quantityButtons.add(button(12, 62, 22, "-", b -> quantity(displayedQuantity() - outputYield())));
            quantityButtons.add(button(110, 62, 22, "+", b -> quantity(displayedQuantity() + outputYield())));
            int[] presets = {outputYield(), 8, 16, 32, 64};
            for (int i = 0; i < presets.length; i++) {
                int amount = Math.max(outputYield(), presets[i] / outputYield() * outputYield());
                quantityButtons.add(button(12 + i * 25, 83, 23, Integer.toString(amount), b -> quantity(amount)));
            }
            assemble = button(221, 113, 90, tr("assemble"), b -> menu.start());
        } else if (shownPhase == 1) {
            button(9, 113, 77, tr("cancel"), b -> menu.cancel()).active = !menu.busy();
        } else if (shownPhase == 3) {
            button(221, 113, 90, tr("take"), b -> menu.take());
        }
        refreshButtons();
    }

    private int outputYield() {
        return menu.selectedRecipe().map(h -> h.value().result().getCount()).orElse(1);
    }

    private void quantity(int amount) {
        menu.selectedRecipe().ifPresent(h -> {
            pendingQuantity = Math.clamp(amount, outputYield(), 64 / outputYield() * outputYield());
            pendingTicks = 0;
            menu.select(h.id(), pendingQuantity);
        });
    }

    private int displayedQuantity() { return pendingQuantity < 0 ? menu.quantity() : pendingQuantity; }

    private void cycle(int delta) {
        List<RecipeHolder<ElectronicsRecipe>> recipes = menu.recipes();
        if (recipes.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < recipes.size(); i++) if (recipes.get(i).id().toString().equals(shownRecipe)) index = i;
        RecipeHolder<ElectronicsRecipe> recipe = recipes.get(Math.floorMod(index + delta, recipes.size()));
        menu.select(recipe.id(), recipe.value().result().getCount());
    }

    private Button button(int x, int y, int width, String text, Button.OnPress action) {
        return button(x, y, width, Component.literal(text), action);
    }

    private Button button(int x, int y, int width, Component text, Button.OnPress action) {
        return addRenderableWidget(new CopperButton(leftPos + x, topPos + y, width, text, action));
    }

    private void refreshButtons() {
        if (assemble != null) assemble.active = menu.selectedRecipe().isPresent()
                && pendingQuantity < 0 && menu.quantity() <= menu.maxCraftable() && !menu.busy();
        quantityButtons.forEach(button -> button.active = !menu.busy() && menu.selectedRecipe().isPresent());
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (pendingQuantity >= 0 && (pendingQuantity == menu.quantity() || ++pendingTicks > 20)) pendingQuantity = -1;
        String selected = menu.selectedRecipe().map(h -> h.id().toString()).orElse("");
        if (shownPhase != menu.phase() && (menu.phase() == 2 || menu.phase() == 3)) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,
                    menu.phase() == 3 ? 1.5f : 1.15f));
        }
        if (shownPhase != menu.phase() || !shownRecipe.equals(selected)) rebuild();
        if (menu.phase() == 1 && (menu.placedMask() & ~acknowledgedMask) != 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.25f));
        }
        acknowledgedMask = menu.placedMask();
        refreshButtons();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        fill(graphics, 0, 0, imageWidth, imageHeight, BACK);
        fill(graphics, 0, 0, imageWidth, 2, COPPER);
        fill(graphics, 8, 25, 312, 134, PANEL);
        for (int i = 0; i < 9; i++) slot(graphics, 9 + 18 * i, 141);
        slot(graphics, 289, 141);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(graphics, 79 + col * 18, 163 + row * 18);
        for (int col = 0; col < 9; col++) slot(graphics, 79 + col * 18, 221);
        text(graphics, tr("title"), 10, 10, TEXT);
        if (menu.busy()) text(graphics, clip(tr("busy"), 82), 228, 10, COPPER);
        text(graphics, tr("storage"), 10, 130, MUTED);
        text(graphics, tr("output"), 260, 130, MUTED);
        text(graphics, clip(playerInventoryTitle, 63), 10, 169, MUTED);
        if (menu.busy() && menu.phase() == 0) text(graphics, tr("busy"), 14, 115, COPPER);
        menu.selectedRecipe().ifPresentOrElse(holder -> {
            ElectronicsRecipe recipe = holder.value();
            if (menu.phase() == 0) drawSelection(graphics, recipe);
            else if (menu.phase() == 1) drawAssembly(graphics, recipe, mouseX, mouseY);
            else drawProduction(graphics, recipe);
        }, () -> text(graphics, tr("no_recipes"), 35, 34, MUTED));
    }

    private void drawSelection(GuiGraphics graphics, ElectronicsRecipe recipe) {
        graphics.renderItem(recipe.result(), leftPos + 34, topPos + 28);
        text(graphics, clip(recipe.result().getHoverName(), 229), 54, 33, TEXT);
        text(graphics, tr("quantity"), 12, 51, MUTED);
        graphics.drawCenteredString(font, Integer.toString(displayedQuantity()), leftPos + 72, topPos + 67, TEXT);
        text(graphics, tr("maximum", menu.maxCraftable()), 12, 105, displayedQuantity() > menu.maxCraftable() ? 0xffed9b88 : MUTED);
        text(graphics, tr("materials"), 150, 51, MUTED);
        int row = 0;
        for (var material : recipe.materials().subList(materialOffset, recipe.materials().size())) {
            int y = 64 + row++ * 10;
            if (y > 106) break;
            ItemStack[] matches = material.ingredient().getItems();
            int available = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (material.ingredient().test(stack)) available += stack.getCount();
            }
            int needed = material.count() * displayedQuantity() / recipe.result().getCount();
            Component name = matches.length == 0 ? tr("material") : matches[0].getHoverName();
            text(graphics, clip(name, 91), 150, y, TEXT);
            graphics.drawString(font, available + " / " + needed, leftPos + 306 - font.width(available + " / " + needed), topPos + y,
                    available >= needed ? COPPER : 0xffed9b88, false);
        }
        if (recipe.materials().size() > 5) {
            fill(graphics, 309, 63, 311, 109, BACK);
            int top = 63 + materialOffset * 26 / (recipe.materials().size() - 5);
            fill(graphics, 309, top, 311, top + 20, COPPER);
        }
    }

    private int targetX(int normalized) { return 129 + normalized * 156 / 100; }
    private int targetY(int normalized) { return 54 + normalized * 48 / 100; }

    private void drawAssembly(GuiGraphics graphics, ElectronicsRecipe recipe, int mouseX, int mouseY) {
        text(graphics, tr("prototype"), 14, 30, COPPER);
        text(graphics, clip(tr("assembly_hint"), 196), 111, 118, MUTED);
        fill(graphics, 115, 46, 305, 113, BACK);
        if (recipe.assemblyLayout().size() > 5) {
            fill(graphics, 110, 44, 112, 109, BACK);
            int top = 44 + partOffset * 45 / (recipe.assemblyLayout().size() - 5);
            fill(graphics, 110, top, 112, top + 20, COPPER);
        }
        int centerX = 209, centerY = 80;
        for (int i = 0; i < recipe.assemblyLayout().size(); i++) {
            var part = recipe.assemblyLayout().get(i);
            int x = targetX(part.x()), y = targetY(part.y());
            boolean placed = (menu.placedMask() & 1 << i) != 0;
            fill(graphics, Math.min(x, centerX), y, Math.max(x, centerX) + 1, y + 1, LINE);
            fill(graphics, centerX, Math.min(y, centerY), centerX + 1, Math.max(y, centerY) + 1, LINE);
            boolean hover = heldPart == i && Math.abs(mouseX - leftPos - x) <= 12 && Math.abs(mouseY - topPos - y) <= 12;
            fill(graphics, x - 10, y - 10, x + 10, y + 10, placed || hover ? COPPER : LINE);
            fill(graphics, x - 9, y - 9, x + 9, y + 9, PANEL);
            graphics.renderItem(partIcon(recipe, i), leftPos + x - 8, topPos + y - 8);
            if (!placed) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 180);
                fill(graphics, x - 8, y - 8, x + 8, y + 8, 0xa02c3035);
                graphics.pose().popPose();
            }
            int trayY = 44 + (i - partOffset) * 13;
            if (!placed && i >= partOffset && i < partOffset + 5) {
                fill(graphics, 13, trayY, 108, trayY + 12, heldPart == i ? LINE : BACK);
                text(graphics, clip(Component.translatable(part.label()), 88), 17, trayY + 2, heldPart == i ? COPPER : TEXT);
            }
        }
    }

    private ItemStack partIcon(ElectronicsRecipe recipe, int part) {
        ItemStack[] items = recipe.materials().get(recipe.assemblyLayout().get(part).ingredientIndex()).ingredient().getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }

    private void drawProduction(GuiGraphics graphics, ElectronicsRecipe recipe) {
        text(graphics, tr(menu.phase() == 3 ? "complete" : "validated"), 17, 36, COPPER);
        text(graphics, tr("production"), 17, 56, TEXT);
        int progress = menu.phase() == 3 ? menu.quantity() : menu.quantity() * menu.progress() / Math.max(1, menu.duration());
        fill(graphics, 17, 74, 303, 83, BACK);
        int pulse = menu.phase() == 2 && menu.progress() % 12 < 4 ? 0xffefbc91 : COPPER;
        fill(graphics, 17, 74, 17 + 286 * progress / Math.max(1, menu.quantity()), 83, pulse);
        text(graphics, Component.literal(progress + " / " + menu.quantity()), 17, 91, TEXT);
        graphics.renderItem(recipe.result(), leftPos + 283,
                topPos + 91 - (menu.phase() == 2 && menu.progress() % 12 < 6 ? 1 : 0));
        text(graphics, clip(recipe.result().getHoverName(), 210), 17, 113, MUTED);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (heldPart >= 0 && menu.phase() == 1) menu.selectedRecipe().ifPresent(h -> {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.renderItem(partIcon(h.value(), heldPart), mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        });
        renderTooltip(graphics, mouseX, mouseY);
        if (heldPart < 0 && menu.phase() == 1) menu.selectedRecipe().ifPresent(holder -> {
            var layout = holder.value().assemblyLayout();
            for (int index = 0; index < layout.size(); index++) {
                var part = layout.get(index);
                boolean overTray = index >= partOffset && index < partOffset + 5
                        && (menu.placedMask() & 1 << index) == 0
                        && mouseX >= leftPos + 13 && mouseX < leftPos + 108
                        && mouseY >= topPos + 44 + (index - partOffset) * 13
                        && mouseY < topPos + 56 + (index - partOffset) * 13;
                if ((Math.abs(mouseX - leftPos - targetX(part.x())) <= 10
                        && Math.abs(mouseY - topPos - targetY(part.y())) <= 10) || overTray) {
                    graphics.renderTooltip(font, Component.translatable(part.label()), mouseX, mouseY);
                }
            }
        });
        if (menu.phase() == 0 && mouseX >= leftPos + 150 && mouseX < leftPos + 308
                && mouseY >= topPos + 64 && mouseY < topPos + 114) menu.selectedRecipe().ifPresent(holder -> {
            int row = (mouseY - topPos - 64) / 10 + materialOffset;
            if (row < holder.value().materials().size()) {
                ItemStack[] matching = holder.value().materials().get(row).ingredient().getItems();
                if (matching.length > 0) graphics.renderTooltip(font, matching[0].getHoverName(), mouseX, mouseY);
            }
        });
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.phase() == 1 && !menu.busy() && menu.selectedRecipe().isPresent()) {
            ElectronicsRecipe recipe = menu.selectedRecipe().get().value();
            int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
            if (heldPart >= 0 && x >= 115 && x <= 305 && y >= 46 && y <= 113) {
                drop(recipe, x, y);
                return true;
            }
            for (int i = 0; i < recipe.assemblyLayout().size(); i++) {
                if (x >= 13 && x < 108 && i >= partOffset && i < partOffset + 5 && y >= 44 + (i - partOffset) * 13 && y < 56 + (i - partOffset) * 13 && (menu.placedMask() & 1 << i) == 0) {
                    heldPart = i;
                    moved = false;
                    dragX = mouseX;
                    dragY = mouseY;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (heldPart >= 0 && button == 0) {
            moved |= Math.abs(x - dragX) + Math.abs(y - dragY) > 3;
            return true;
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (heldPart >= 0 && button == 0) {
            if (moved) menu.selectedRecipe().ifPresent(h -> drop(h.value(), (int) x - leftPos, (int) y - topPos));
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private void drop(ElectronicsRecipe recipe, int x, int y) {
        int target = -1;
        for (int i = 0; i < recipe.assemblyLayout().size(); i++) {
            var part = recipe.assemblyLayout().get(i);
            if (Math.abs(x - targetX(part.x())) <= 12 && Math.abs(y - targetY(part.y())) <= 12) target = i;
        }
        if (target == heldPart) menu.place(heldPart, target);
        else minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f));
        heldPart = -1;
        moved = false;
    }

    private net.minecraft.util.FormattedCharSequence clip(Component value, int width) {
        if (font.width(value) <= width) return value.getVisualOrderText();
        String shortened = font.substrByWidth(value, Math.max(0, width - font.width("..."))).getString();
        return Component.literal(shortened + "...").getVisualOrderText();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.selectedRecipe().isPresent()) {
            var recipe = menu.selectedRecipe().get().value();
            int direction = scrollY > 0 ? -1 : 1;
            if (mouseX >= leftPos + 13 && mouseX < leftPos + 108 && mouseY >= topPos + 44 && mouseY < topPos + 110 && menu.phase() == 1) {
                partOffset = Math.clamp(partOffset + direction, 0, Math.max(0, recipe.assemblyLayout().size() - 5));
                return true;
            }
            if (mouseX >= leftPos + 150 && mouseX < leftPos + 309 && mouseY >= topPos + 63 && mouseY < topPos + 111 && menu.phase() == 0) {
                materialOffset = Math.clamp(materialOffset + direction, 0, Math.max(0, recipe.materials().size() - 5));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void slot(GuiGraphics graphics, int x, int y) {
        fill(graphics, x - 1, y - 1, x + 17, y + 17, LINE);
        fill(graphics, x, y, x + 16, y + 16, 0xff14171b);
    }

    private void fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(leftPos + x1, topPos + y1, leftPos + x2, topPos + y2, color);
    }

    private void text(GuiGraphics graphics, net.minecraft.util.FormattedCharSequence value, int x, int y, int color) {
        graphics.drawString(font, value, leftPos + x, topPos + y, color, false);
    }

    private void text(GuiGraphics graphics, Component value, int x, int y, int color) {
        graphics.drawString(font, value, leftPos + x, topPos + y, color, false);
    }

    private final class CopperButton extends Button {
        CopperButton(int x, int y, int width, Component label, OnPress action) {
            super(x, y, width, 17, label, action, DEFAULT_NARRATION);
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, active && isHoveredOrFocused() ? COPPER : LINE);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, BACK);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 4,
                    !active ? 0xff62676c : isHoveredOrFocused() ? COPPER : TEXT);
        }
    }
}
