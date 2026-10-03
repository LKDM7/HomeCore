package fr.lkdm.homecore.api.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

/** Stateless rendering primitives in scaled GUI pixels, callable only on the client. */
public final class HomeLinkUi {
    private HomeLinkUi() { }

    /** Draws the standard beveled window with header, footer rule and corner screws.
     * @param graphics drawing context
     * @param x surface left
     * @param y surface top
     * @param width surface width
     * @param height surface height
     */
    public static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        window(graphics, x, y, width, height, HomeLinkTheme.HEADER_HEIGHT);
        if (width > 20 && height >= HomeLinkTheme.FOOTER_HEIGHT)
            separator(graphics, x + 10, y + height - HomeLinkTheme.FOOTER_HEIGHT, width - 20);
    }

    /** Draws inventory-compatible chrome without a footer rule. Slot positions remain caller-owned.
     * @param graphics drawing context
     * @param x surface left
     * @param y surface top
     * @param width surface width
     * @param height surface height
     * @param headerHeight header band height
     */
    public static void window(GuiGraphics graphics, int x, int y, int width, int height, int headerHeight) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xFF141617);
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF6B6E70);
        graphics.fill(x, y, x + width, y + height, HomeLinkTheme.BACKGROUND);
        graphics.fill(x, y, x + width, y + Math.min(height, Math.max(0, headerHeight)), HomeLinkTheme.HEADER);
        graphics.renderOutline(x, y, width, height, HomeLinkTheme.LINE);
        if (width >= 16 && height >= 16) {
            screw(graphics, x + 4, y + 4);
            screw(graphics, x + width - 8, y + 4);
            screw(graphics, x + 4, y + height - 8);
            screw(graphics, x + width - 8, y + height - 8);
        }
    }

    /** Draws a recessed content panel.
     * @param graphics drawing context
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, HomeLinkTheme.SURFACE);
        graphics.fill(x, y, x + width, y + 1, 0xFF17191A);
        graphics.fill(x, y, x + 1, y + height, 0xFF17191A);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF535659);
    }

    /** Draws a recessed item slot; the vanilla 16-pixel item sits at (x, y).
     * @param graphics drawing context
     * @param x item left
     * @param y item top
     */
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF17191A);
        graphics.fill(x, y, x + 17, y + 17, 0xFF535659);
        graphics.fill(x, y, x + 16, y + 16, HomeLinkTheme.SURFACE);
    }

    /** Draws a one-pixel horizontal rule.
     * @param graphics drawing context
     * @param x left
     * @param y top
     * @param width rule width
     */
    public static void separator(GuiGraphics graphics, int x, int y, int width) {
        if (width > 0) graphics.fill(x, y, x + width, y + 1, HomeLinkTheme.LINE);
    }

    /** Draws a four-pixel screw.
     * @param graphics drawing context
     * @param x left
     * @param y top
     */
    public static void screw(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 4, y + 4, 0xFF242628);
        graphics.fill(x, y, x + 3, y + 1, 0xFF727578);
        graphics.fill(x + 1, y + 2, x + 3, y + 3, 0xFF858887);
    }

    /** Styles a vanilla field without changing its value, responder, narration or bounds.
     * @param edit existing field; normally use the recommended control height
     * @return the same field
     */
    public static EditBox input(EditBox edit) {
        edit.setTextColor(HomeLinkTheme.TEXT);
        edit.setTextColorUneditable(HomeLinkTheme.MUTED);
        return edit;
    }

    /** Draws the small HomeLink pixel mark without an external texture.
     * @param graphics drawing context
     * @param x left
     * @param y top
     * @param color opaque ARGB color
     */
    public static void mark(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y + 2, x + 2, y + 14, color);
        graphics.fill(x + 10, y + 2, x + 12, y + 14, color);
        graphics.fill(x + 2, y + 7, x + 10, y + 9, color);
        graphics.fill(x + 4, y, x + 8, y + 2, color);
    }

    /** Draws an eight-pixel status socket. Add adjacent text or a tooltip.
     * @param graphics drawing context
     * @param x left
     * @param y top
     * @param color opaque ARGB status color
     */
    public static void statusDot(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 8, y + 8, 0xFF1D1F20);
        graphics.fill(x + 2, y + 2, x + 6, y + 6, color);
    }

    /** Draws a typed status indicator; add adjacent text or a tooltip.
     * @param graphics drawing context
     * @param x left
     * @param y top
     * @param tone visual tone
     */
    public static void statusDot(GuiGraphics graphics, int x, int y, HomeLinkStatusTone tone) {
        statusDot(graphics, x, y, HomeLinkTheme.statusColor(tone));
    }

    /** Draws a bounded horizontal progress track. The caller supplies the meaning and label.
     * @param graphics drawing context
     * @param x left
     * @param y top
     * @param width track width
     * @param height track height
     * @param fraction fill ratio; clamped to 0..1, NaN is empty
     * @param color opaque ARGB fill color
     */
    public static void progressBar(GuiGraphics graphics, int x, int y, int width, int height, double fraction, int color) {
        if (width <= 0 || height <= 0) return;
        double bounded = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));
        graphics.fill(x, y, x + width, y + height, HomeLinkTheme.LINE);
        int filled = (int) Math.round(width * bounded);
        if (filled > 0) graphics.fill(x, y, x + filled, y + height, color);
    }

    /** Draws the three-pixel inventory gauge used by HomeLink device screens.
     * @param graphics drawing context
     * @param x left
     * @param y top
     * @param width track width
     * @param fraction fill ratio, clamped to 0..1
     * @param color opaque ARGB fill color
     */
    public static void gauge(GuiGraphics graphics, int x, int y, int width, float fraction, int color) {
        progressBar(graphics, x, y, width, 3, fraction, color);
    }

    /** Clips plain text to available width with an ellipsis when it fits.
     * @param font native Minecraft font
     * @param text resolved text
     * @param width available width
     * @return text within the requested width
     */
    public static String clip(Font font, String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        int ellipsisWidth = font.width("…");
        return width < ellipsisWidth ? "" : font.plainSubstrByWidth(text, width - ellipsisWidth) + "…";
    }
}
