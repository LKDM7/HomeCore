package fr.lkdm.homecore.api.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** HomeLink control with vanilla activation, keyboard navigation and narration. */
public class HomeLinkButton extends Button {
    private boolean navigation;
    private boolean selected;
    private BooleanSupplier selectedState;
    private boolean automaticTooltip;

    /** Creates a client button; subclasses may add consumer-specific icons.
     * @param builder vanilla button configuration
     */
    protected HomeLinkButton(Button.Builder builder) {
        super(builder);
        automaticTooltip = builder instanceof Builder homeLinkBuilder && !homeLinkBuilder.customTooltip;
    }

    @Override public void setMessage(Component message) {
        super.setMessage(message);
        if (automaticTooltip) super.setTooltip(Tooltip.create(message));
    }

    @Override public void setTooltip(Tooltip tooltip) {
        super.setTooltip(tooltip);
        automaticTooltip = false;
    }

    /** Sets the pressed-in state; an enabled navigation mode retains its marker.
     * @param value selected state
     * @return this button
     */
    public HomeLinkButton selected(boolean value) { selectedState = null; selected = value; return this; }

    /** Binds a lightweight client toggle state, evaluated when rendering.
     * @param state selected-state supplier; null stops evaluating the supplier
     * @return this button
     */
    public HomeLinkButton selectedWhen(BooleanSupplier state) { selectedState = state; return this; }

    /** Enables navigation styling and sets the active-tab state.
     * @param value selected state
     * @return this button
     */
    public HomeLinkButton navigation(boolean value) { navigation = true; return selected(value); }

    /** Creates a builder with the recommended 18-pixel height and a full-label tooltip.
     * @param message localized label, also retained for narration
     * @param onPress vanilla activation callback
     * @return HomeLink builder
     */
    public static Builder builder(Component message, OnPress onPress) { return new Builder(message, onPress); }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (selectedState != null) selected = selectedState.getAsBoolean();
        boolean highlighted = active && isHoveredOrFocused();
        int left = getX(), top = getY();
        int background = selected ? 0xFF292B2D : highlighted ? 0xFF5A5D60 : active ? 0xFF474A4D : 0xFF36383A;
        graphics.fill(left, top, left + width, top + height, 0xFF181A1B);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, background);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 2, selected ? 0xFF202224 : active ? 0xFF74787A : 0xFF484B4D);
        graphics.fill(left + 1, top + 2, left + 2, top + height - 1, selected ? 0xFF202224 : 0xFF626669);
        if (selected && navigation) graphics.fill(left + 5, top + height / 2 - 1, left + 7, top + height / 2 + 1, HomeLinkTheme.ACCENT);
        if (isFocused() && active) graphics.renderOutline(left, top, width, height, HomeLinkTheme.ACCENT);
        renderLabel(graphics, selected ? HomeLinkTheme.ACCENT : active ? HomeLinkTheme.TEXT : 0xFF91948F,
                navigation ? 16 : 8);
    }

    /** Draws the centered label. Consumer widgets may add an icon without duplicating the chrome.
     * @param graphics drawing context
     * @param color resolved label color for the current visual state
     * @param horizontalPadding total space reserved around the label
     */
    protected void renderLabel(GuiGraphics graphics, int color, int horizontalPadding) {
        var font = Minecraft.getInstance().font;
        String text = HomeLinkUi.clip(font, getMessage().getString(), Math.max(0, width - horizontalPadding));
        graphics.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + (height - 8) / 2, color, false);
    }

    /** Fluent vanilla-compatible builder returning a HomeLink button. */
    public static class Builder extends Button.Builder {
        private boolean customTooltip;
        /** Creates a builder with a label tooltip and standard height.
         * @param message localized label
         * @param onPress activation callback
         */
        public Builder(Component message, OnPress onPress) {
            super(message, onPress);
            super.size(150, HomeLinkTheme.CONTROL_HEIGHT);
            super.tooltip(Tooltip.create(message));
        }
        @Override public Builder pos(int x, int y) { super.pos(x, y); return this; }
        @Override public Builder width(int width) { super.width(width); return this; }
        @Override public Builder size(int width, int height) { super.size(width, height); return this; }
        @Override public Builder bounds(int x, int y, int width, int height) { super.bounds(x, y, width, height); return this; }
        @Override public Builder tooltip(Tooltip tooltip) { customTooltip = true; super.tooltip(tooltip); return this; }
        @Override public Builder createNarration(CreateNarration narration) { super.createNarration(narration); return this; }
        @Override public HomeLinkButton build() { return new HomeLinkButton(this); }
    }
}
