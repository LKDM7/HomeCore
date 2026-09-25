package fr.lkdm.homecore.workbench.client;

import fr.lkdm.homecore.workbench.ElectronicsMenu;
import fr.lkdm.homecore.workbench.recipe.ElectronicsRecipe;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Compact native container UI. Inventory and assembly changes are server requests. */
public final class ElectronicsScreen extends AbstractContainerScreen<ElectronicsMenu> {
    private static final int BACK = 0xff303234, HEADER = 0xff45474a, PANEL = 0xff252729, LINE = 0xff626568;
    private static final int TEXT = 0xffe7e5e0, MUTED = 0xffafb1ad, BRASS = 0xffd2b181;
    private static final int GOOD = 0xffa1bd92, WARNING = 0xffd3b16f, BAD = 0xffd19a8f;
    private final List<Button> quantityButtons = new ArrayList<>();
    private Button assemble, maximum;
    private int shownPhase = -1, acknowledgedMask, heldPart = -1, recipePage, materialOffset;
    private String shownRecipe = "";
    private boolean moved;
    private int pendingQuantity = -1, pendingTicks, errorTicks;
    private double dragX, dragY;

    public ElectronicsScreen(ElectronicsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ElectronicsMenu.WIDTH;
        imageHeight = ElectronicsMenu.HEIGHT;
    }
    private static Component tr(String key, Object... args) { return Component.translatable("screen.homecore.electronics." + key, args); }
    @Override protected void init() { super.init(); rebuild(); }

    private void rebuild() {
        clearWidgets(); quantityButtons.clear();
        shownPhase = menu.phase();
        shownRecipe = menu.selectedRecipe().map(h -> h.id().toString()).orElse("");
        heldPart = -1; pendingQuantity = -1; materialOffset = 0; assemble = null; maximum = null;
        if (shownPhase == 0) {
            var recipes = menu.recipes();
            recipePage = Math.clamp(recipePage, 0, Math.max(0, (recipes.size() - 1) / 6));
            for (int i = recipePage * 6; i < Math.min(recipes.size(), recipePage * 6 + 6); i++) {
                var holder = recipes.get(i);
                var tab = new IconButton(leftPos + 80 + (i % 6) * 28, topPos + 25, holder.value().result(),
                        holder.id().toString().equals(shownRecipe), b -> menu.select(holder.id(), holder.value().result().getCount()));
                tab.setTooltip(Tooltip.create(holder.value().result().getHoverName()));
                addRenderableWidget(tab);
            }
            if (recipes.size() > 6) {
                button(268, 25, 18, "<", b -> { recipePage = Math.floorMod(recipePage - 1, (recipes.size() + 5) / 6); rebuild(); });
                button(290, 25, 18, ">", b -> { recipePage = (recipePage + 1) % ((recipes.size() + 5) / 6); rebuild(); });
            }
            quantityButtons.add(button(80, 78, 18, "-", b -> quantity(displayedQuantity() - outputYield())));
            quantityButtons.add(button(137, 78, 18, "+", b -> quantity(displayedQuantity() + outputYield())));
            var presets = new LinkedHashSet<Integer>();
            for (int amount : new int[]{outputYield(), 8, 16, 32, 64}) presets.add(Math.max(outputYield(), amount / outputYield() * outputYield()));
            int index = 0;
            for (int amount : presets) quantityButtons.add(button(161 + index++ * 29, 78, 27, Integer.toString(amount), b -> quantity(amount)));
            maximum = button(80, 99, 36, tr("max"), b -> quantity(menu.maxCraftable()));
            maximum.setTooltip(Tooltip.create(tr("max_hint")));
            assemble = button(227, 99, 81, tr("assemble"), b -> menu.start());
        } else if (shownPhase == 1) {
            button(10, 137, 56, tr("cancel"), b -> menu.cancel()).active = !menu.busy();
        } else if (shownPhase == 3) button(227, 130, 81, tr("take"), b -> menu.take());
        refreshButtons();
    }
    private int outputYield() { return menu.selectedRecipe().map(h -> h.value().result().getCount()).orElse(1); }
    private int displayedQuantity() { return pendingQuantity < 0 ? menu.quantity() : pendingQuantity; }
    private void quantity(int amount) {
        menu.selectedRecipe().ifPresent(h -> {
            pendingQuantity = Math.clamp(amount, outputYield(), 64 / outputYield() * outputYield());
            pendingTicks = 0; menu.select(h.id(), pendingQuantity);
        });
    }
    private Button button(int x, int y, int width, String text, Button.OnPress action) { return button(x, y, width, Component.literal(text), action); }
    private Button button(int x, int y, int width, Component text, Button.OnPress action) { return addRenderableWidget(new WorkbenchButton(leftPos + x, topPos + y, width, text, action)); }
    private void refreshButtons() {
        if (assemble != null) assemble.active = menu.selectedRecipe().isPresent() && pendingQuantity < 0 && menu.quantity() <= menu.maxCraftable() && !menu.busy();
        if (maximum != null) maximum.active = menu.maxCraftable() >= outputYield() && !menu.busy();
        quantityButtons.forEach(button -> button.active = !menu.busy() && menu.selectedRecipe().isPresent());
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (errorTicks > 0) errorTicks--;
        if (pendingQuantity >= 0 && (pendingQuantity == menu.quantity() || ++pendingTicks > 20)) pendingQuantity = -1;
        String selected = menu.selectedRecipe().map(h -> h.id().toString()).orElse("");
        if (shownPhase != menu.phase() && (menu.phase() == 2 || menu.phase() == 3)) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, menu.phase() == 3 ? 1.5f : 1.15f));
        if (shownPhase != menu.phase() || !shownRecipe.equals(selected)) rebuild();
        if (menu.phase() == 1 && (menu.placedMask() & ~acknowledgedMask) != 0) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.25f));
        acknowledgedMask = menu.placedMask(); refreshButtons();
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        fill(graphics, 0, 0, imageWidth, imageHeight, 0xff141617);
        fill(graphics, 1, 1, imageWidth - 1, imageHeight - 1, 0xff6b6e70);
        fill(graphics, 2, 2, imageWidth - 2, imageHeight - 2, BACK);
        fill(graphics, 2, 2, imageWidth - 2, 21, HEADER);
        fill(graphics, 2, 21, imageWidth - 2, 22, LINE);
        screw(graphics, 4, 4); screw(graphics, imageWidth - 8, 4);
        screw(graphics, 4, imageHeight - 8); screw(graphics, imageWidth - 8, imageHeight - 8);
        panel(graphics, 8, 24, 69, 155); panel(graphics, 76, 24, 312, 155);
        panel(graphics, 76, 160, 244, 240);
        fill(graphics, 304, 10, 312, 18, 0xff1d1f20);
        fill(graphics, 306, 12, 310, 16, menu.busy() ? WARNING : menu.phase() == 3 ? GOOD : menu.phase() == 0 ? MUTED : BRASS);
        text(graphics, tr("title"), 10, 10, TEXT);
        if (menu.busy()) text(graphics, clip(tr("busy"), 72), 228, 10, WARNING);
        text(graphics, clip(tr("storage_short"), 54), 12, 29, MUTED);
        for (int i = 0; i < 9; i++) slot(graphics, 12 + 18 * (i % 3), 43 + 18 * (i / 3));
        text(graphics, tr("output"), 12, 103, MUTED); slot(graphics, 30, 117);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(graphics, 79 + col * 18, 163 + row * 18);
        for (int col = 0; col < 9; col++) slot(graphics, 79 + col * 18, 221);
        text(graphics, clip(playerInventoryTitle, 65), 10, 165, MUTED);
        text(graphics, clip(tr("shift_hint"), 65), 10, 180, MUTED);
        menu.selectedRecipe().ifPresentOrElse(holder -> {
            var recipe = holder.value();
            if (menu.phase() == 0) drawSelection(graphics, recipe);
            else if (menu.phase() == 1) drawAssembly(graphics, recipe, mouseX, mouseY);
            else drawProduction(graphics, recipe);
        }, () -> text(graphics, clip(tr("no_recipes"), 220), 82, 54, MUTED));
    }
    private void drawSelection(GuiGraphics graphics, ElectronicsRecipe recipe) {
        text(graphics, clip(recipe.result().getHoverName(), 224), 81, 52, TEXT);
        text(graphics, tr("final_quantity"), 81, 65, MUTED);
        graphics.drawCenteredString(font, Component.literal("\u00d7" + displayedQuantity()), leftPos + 117, topPos + 83, TEXT);
        text(graphics, clip(tr("available", menu.maxCraftable()), 102), 121, 104, displayedQuantity() > menu.maxCraftable() ? BAD : MUTED);
        if (displayedQuantity() > menu.maxCraftable()) drawWrapped(graphics, tr("insufficient"), 10, 195, 64, BAD);
        for (int col = 0; col < 5 && col + materialOffset < recipe.materials().size(); col++) {
            var material = recipe.materials().get(col + materialOffset);
            var choices = material.ingredient().getItems(); int x = 84 + col * 45;
            if (choices.length > 0) graphics.renderItem(choices[0], leftPos + x + 9, topPos + 120);
            int available = 0;
            for (int i = 0; i < 9; i++) if (material.ingredient().test(menu.getSlot(i).getItem())) available += menu.getSlot(i).getItem().getCount();
            int needed = material.count() * displayedQuantity() / outputYield();
            graphics.drawCenteredString(font, available + "/" + needed, leftPos + x + 17, topPos + 141, available >= needed ? GOOD : BAD);
        }
        if (recipe.materials().size() > 5) {
            fill(graphics, 309, 120, 311, 150, BACK);
            int top = 120 + materialOffset * 18 / (recipe.materials().size() - 5); fill(graphics, 309, top, 311, top + 12, BRASS);
        }
    }
    private int targetX(int normalized) { return 100 + normalized * 190 / 100; }
    private int targetY(int normalized) { return 56 + normalized * 60 / 100; }
    private int trayX(int part) { return 81 + part * 28; }
    private void drawAssembly(GuiGraphics graphics, ElectronicsRecipe recipe, int mouseX, int mouseY) {
        text(graphics, tr("prototype_short"), 82, 29, BRASS);
        String count = Integer.bitCount(menu.placedMask()) + " / " + recipe.assemblyLayout().size(); text(graphics, Component.literal(count), 306 - font.width(count), 29, TEXT);
        panel(graphics, 82, 43, 307, 128);
        for (int i = 0; i < recipe.assemblyLayout().size(); i++) {
            var part = recipe.assemblyLayout().get(i); int x = targetX(part.x()), y = targetY(part.y());
            boolean placed = (menu.placedMask() & 1 << i) != 0;
            fill(graphics, Math.min(x, 195), y, Math.max(x, 195) + 1, y + 1, placed ? BRASS : LINE);
            fill(graphics, 195, Math.min(y, 86), 196, Math.max(y, 86) + 1, placed ? BRASS : LINE);
            boolean hover = heldPart == i && near(mouseX - leftPos, mouseY - topPos, x, y);
            fill(graphics, x - 11, y - 11, x + 11, y + 11, placed || hover ? BRASS : LINE); fill(graphics, x - 10, y - 10, x + 10, y + 10, PANEL);
            graphics.renderItem(partIcon(recipe, i), leftPos + x - 8, topPos + y - 8);
            if (!placed) {
                graphics.pose().pushPose(); graphics.pose().translate(0, 0, 180);
                fill(graphics, x - 8, y - 8, x + 8, y + 8, 0x90252729); graphics.pose().popPose();
                int trayX = trayX(i); fill(graphics, trayX, 131, trayX + 24, 153, heldPart == i ? BRASS : LINE);
                fill(graphics, trayX + 1, 132, trayX + 23, 152, BACK); graphics.renderItem(partIcon(recipe, i), leftPos + trayX + 4, topPos + 134);
            }
        }
        drawWrapped(graphics, tr(errorTicks > 0 ? "try_again" : "drag_hint"), 10, 193, 64, errorTicks > 0 ? BAD : MUTED);
    }
    private ItemStack partIcon(ElectronicsRecipe recipe, int part) {
        var items = recipe.materials().get(recipe.assemblyLayout().get(part).ingredientIndex()).ingredient().getItems(); return items.length == 0 ? ItemStack.EMPTY : items[0];
    }
    private void drawProduction(GuiGraphics graphics, ElectronicsRecipe recipe) {
        text(graphics, clip(tr(menu.phase() == 3 ? "complete" : "validated"), 220), 83, 32, GOOD);
        text(graphics, clip(recipe.result().getHoverName(), 217), 83, 49, TEXT);
        graphics.pose().pushPose(); graphics.pose().translate(leftPos + 93, topPos + 74, 0); graphics.pose().scale(1.5f, 1.5f, 1.5f);
        graphics.renderItem(recipe.result(), 0, 0); graphics.pose().popPose();
        int progress = menu.phase() == 3 ? menu.quantity() : menu.quantity() * menu.progress() / Math.max(1, menu.duration());
        text(graphics, tr("production"), 130, 73, MUTED); text(graphics, Component.literal(progress + " / " + menu.quantity()), 130, 89, TEXT);
        panel(graphics, 84, 110, 305, 119);
        fill(graphics, 84, 110, 84 + 221 * progress / Math.max(1, menu.quantity()), 119, menu.phase() == 3 ? GOOD : menu.progress() % 12 < 4 ? 0xffe5c99d : BRASS);
        if (menu.phase() == 3) text(graphics, tr("ready"), 84, 135, MUTED);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (heldPart >= 0 && menu.phase() == 1) menu.selectedRecipe().ifPresent(h -> {
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, 300); graphics.renderItem(partIcon(h.value(), heldPart), mouseX - 8, mouseY - 8); graphics.pose().popPose();
        });
        renderTooltip(graphics, mouseX, mouseY);
        menu.selectedRecipe().ifPresent(holder -> {
            var recipe = holder.value();
            if (menu.phase() == 1 && heldPart < 0) {
                for (int i = 0; i < recipe.assemblyLayout().size(); i++) {
                    var part = recipe.assemblyLayout().get(i);
                    boolean overTray = mouseX >= leftPos + trayX(i) && mouseX < leftPos + trayX(i) + 24 && mouseY >= topPos + 131 && mouseY < topPos + 153 && (menu.placedMask() & 1 << i) == 0;
                    if (near(mouseX - leftPos, mouseY - topPos, targetX(part.x()), targetY(part.y())) || overTray) graphics.renderTooltip(font, Component.translatable(part.label()), mouseX, mouseY);
                }
            } else if (menu.phase() == 0 && mouseX >= leftPos + 80 && mouseX < leftPos + 309 && mouseY >= topPos + 119 && mouseY < topPos + 153) {
                int index = Math.max(0, (mouseX - leftPos - 84) / 45) + materialOffset;
                if (index < recipe.materials().size()) {
                    var choices = recipe.materials().get(index).ingredient().getItems();
                    if (choices.length > 0) graphics.renderTooltip(font, List.of(choices[0].getHoverName(), tr("counts_hint")), java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        });
    }
    private boolean near(double x, double y, int targetX, int targetY) { return Math.abs(x - targetX) <= 12 && Math.abs(y - targetY) <= 12; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.phase() == 1 && !menu.busy() && menu.selectedRecipe().isPresent()) {
            var recipe = menu.selectedRecipe().get().value(); int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
            if (heldPart >= 0 && x >= 82 && x <= 307 && y >= 43 && y <= 128) { drop(recipe, x, y); return true; }
            for (int i = 0; i < recipe.assemblyLayout().size(); i++) if (x >= trayX(i) && x < trayX(i) + 24 && y >= 131 && y < 153 && (menu.placedMask() & 1 << i) == 0) {
                heldPart = i; moved = false; dragX = mouseX; dragY = mouseY; return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (heldPart >= 0 && button == 0) { moved |= Math.abs(x - dragX) + Math.abs(y - dragY) > 3; return true; }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (heldPart >= 0 && button == 0) { if (moved) menu.selectedRecipe().ifPresent(h -> drop(h.value(), (int) x - leftPos, (int) y - topPos)); return true; }
        return super.mouseReleased(x, y, button);
    }
    private void drop(ElectronicsRecipe recipe, int x, int y) {
        int target = -1;
        for (int i = 0; i < recipe.assemblyLayout().size(); i++) { var part = recipe.assemblyLayout().get(i); if (near(x, y, targetX(part.x()), targetY(part.y()))) target = i; }
        if (target == heldPart) menu.place(heldPart, target);
        else { errorTicks = 35; minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f)); }
        heldPart = -1; moved = false;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.phase() == 0 && menu.selectedRecipe().isPresent() && mouseX >= leftPos + 80 && mouseX < leftPos + 311 && mouseY >= topPos + 119 && mouseY < topPos + 154) {
            materialOffset = Math.clamp(materialOffset + (scrollY > 0 ? -1 : 1), 0, Math.max(0, menu.selectedRecipe().get().value().materials().size() - 5)); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    private net.minecraft.util.FormattedCharSequence clip(Component value, int width) {
        if (font.width(value) <= width) return value.getVisualOrderText();
        return Component.literal(font.substrByWidth(value, Math.max(0, width - font.width("..."))).getString() + "...").getVisualOrderText();
    }
    private void drawWrapped(GuiGraphics graphics, Component value, int x, int y, int width, int color) {
        int row = 0; for (var line : font.split(value, width)) { if (row >= 4) break; text(graphics, line, x, y + row++ * 10, color); }
    }
    private void panel(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        fill(graphics, x1, y1, x2, y2, PANEL);
        fill(graphics, x1, y1, x2, y1 + 1, 0xff17191a);
        fill(graphics, x1, y1, x1 + 1, y2, 0xff17191a);
        fill(graphics, x1, y2 - 1, x2, y2, 0xff535659);
    }
    private void screw(GuiGraphics graphics, int x, int y) {
        fill(graphics, x, y, x + 4, y + 4, 0xff242628);
        fill(graphics, x, y, x + 3, y + 1, 0xff727578);
        fill(graphics, x + 1, y + 2, x + 3, y + 3, 0xff858887);
    }
    private void slot(GuiGraphics graphics, int x, int y) {
        panel(graphics, x - 1, y - 1, x + 17, y + 17);
        fill(graphics, x + 16, y, x + 17, y + 16, 0xff535659);
    }
    private void buttonFace(GuiGraphics graphics, int x, int y, int width, int height, boolean enabled, boolean highlighted, boolean outlined) {
        graphics.fill(x, y, x + width, y + height, 0xff181a1b);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, highlighted ? 0xff5a5d60 : enabled ? 0xff474a4d : 0xff36383a);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, enabled ? 0xff74787a : 0xff484b4d);
        graphics.fill(x + 1, y + 2, x + 2, y + height - 1, 0xff626669);
        if (outlined) graphics.renderOutline(x, y, width, height, BRASS);
    }
    private void fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) { graphics.fill(leftPos + x1, topPos + y1, leftPos + x2, topPos + y2, color); }
    private void text(GuiGraphics graphics, net.minecraft.util.FormattedCharSequence value, int x, int y, int color) { graphics.drawString(font, value, leftPos + x, topPos + y, color, false); }
    private void text(GuiGraphics graphics, Component value, int x, int y, int color) { graphics.drawString(font, value, leftPos + x, topPos + y, color, false); }
    private class WorkbenchButton extends Button {
        WorkbenchButton(int x, int y, int width, Component label, OnPress action) { super(x, y, width, 18, label, action, DEFAULT_NARRATION); }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            buttonFace(graphics, getX(), getY(), width, height, active, active && isHoveredOrFocused(), active && isFocused());
            var label = clip(getMessage(), width - 6);
            graphics.drawString(font, label, getX() + (width - font.width(label)) / 2, getY() + 5, active ? TEXT : 0xff91948f, false);
        }
    }
    private final class IconButton extends Button {
        private final ItemStack icon; private final boolean selected;
        IconButton(int x, int y, ItemStack icon, boolean selected, OnPress action) { super(x, y, 24, 23, icon.getHoverName(), action, DEFAULT_NARRATION); this.icon = icon; this.selected = selected; }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            buttonFace(graphics, getX(), getY(), width, height, active, isHoveredOrFocused(), selected || isFocused());
            if (selected) graphics.fill(getX() + 2, getY() + height - 3, getX() + width - 2, getY() + height - 1, BRASS);
            graphics.renderItem(icon, getX() + 4, getY() + 3);
        }
    }
}
