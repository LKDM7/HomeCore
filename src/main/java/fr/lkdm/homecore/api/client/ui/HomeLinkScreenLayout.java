package fr.lkdm.homecore.api.client.ui;

/**
 * Immutable centered surface in scaled GUI pixels. Inventory screens may keep
 * their slot geometry and use the rendering kit without this layout.
 * @param x left edge
 * @param y top edge
 * @param width nonnegative surface width
 * @param height nonnegative surface height
 */
public record HomeLinkScreenLayout(int x, int y, int width, int height) {
    /** Checks surface dimensions.
     * @param x left edge
     * @param y top edge
     * @param width surface width
     * @param height surface height
     */
    public HomeLinkScreenLayout {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative surface size");
    }

    /** Fits a preferred surface without exceeding the viewport.
     * An eight-pixel margin is reserved on each side when the viewport permits;
     * very small viewports use their available space. Negative inputs are rejected.
     * @param screenWidth scaled viewport width
     * @param screenHeight scaled viewport height
     * @param preferredWidth requested maximum width
     * @param preferredHeight requested maximum height
     * @return centered surface
     */
    public static HomeLinkScreenLayout fit(int screenWidth, int screenHeight, int preferredWidth, int preferredHeight) {
        if (screenWidth < 0 || screenHeight < 0 || preferredWidth < 0 || preferredHeight < 0)
            throw new IllegalArgumentException("Negative viewport or preferred size");
        int width = Math.min(preferredWidth, available(screenWidth));
        int height = Math.min(preferredHeight, available(screenHeight));
        return new HomeLinkScreenLayout((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    private static int available(int extent) {
        return extent >= 2 * HomeLinkTheme.OUTER_MARGIN ? extent - 2 * HomeLinkTheme.OUTER_MARGIN : extent;
    }

    private int horizontalPadding() { return Math.min(HomeLinkTheme.CONTENT_PADDING, width / 2); }
    private int verticalPadding() { return Math.min(HomeLinkTheme.CONTENT_PADDING, bodyHeight() / 2); }
    private int bodyHeight() { return height - headerHeight() - footerHeight(); }

    /** Returns the header top edge.
     * @return header y
     */
    public int headerY() { return y; }
    /** Returns the header height, bounded by the surface.
     * @return header height
     */
    public int headerHeight() { return Math.min(HomeLinkTheme.HEADER_HEIGHT, height); }
    /** Returns the footer top edge.
     * @return footer y
     */
    public int footerY() { return y + height - footerHeight(); }
    /** Returns the footer height, bounded by the space below the header.
     * @return footer height
     */
    public int footerHeight() { return Math.min(HomeLinkTheme.FOOTER_HEIGHT, height - headerHeight()); }
    /** Returns the padded content left edge.
     * @return content x
     */
    public int contentX() { return x + horizontalPadding(); }
    /** Returns the padded content top edge; reserve navigation space in the consumer.
     * @return content y
     */
    public int contentY() { return y + headerHeight() + verticalPadding(); }
    /** Returns nonnegative padded content width.
     * @return content width
     */
    public int contentWidth() { return width - 2 * horizontalPadding(); }
    /** Returns nonnegative padded content height.
     * @return content height
     */
    public int contentHeight() { return bodyHeight() - 2 * verticalPadding(); }
}
