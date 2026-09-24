package com.tkisor.nekojs.api.ui;

/**
 * Platform font measurement source for UI layout and painting.
 *
 * <p>Implementations live on the client side (wrapping the platform font, for example
 * Minecraft's {@code Font}); common layout code only consumes the measurements reported
 * here and never guesses glyph widths. All values are logical GUI pixels at the
 * platform font's base size.
 */
public interface FontAdapter {
    /**
     * Width of a single-line string at the base size (no wrapping, no truncation).
     *
     * @param text text to measure; {@code null} or empty measures 0
     * @return width in logical pixels
     */
    int stringWidth(String text);

    /**
     * Height of one line box at the base size.
     *
     * @return line height in logical pixels
     */
    int lineHeight();

    /**
     * Baseline offset from the top of a line box.
     *
     * @return ascent in logical pixels
     */
    int ascent();
}
