//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

/** Overlay geometry shared by scrollbar painting and pointer routing. */
final class JsxScrollBar {
    private JsxScrollBar() { }

    static Geometry geometry(JsxHostTree.Node node) {
        if (!"scroll".equals(node.type) || node.scrollRange <= 0) return null;
        boolean horizontal = "row".equals(node.props.get("direction"));
        int inset = Math.max(1, (int) Math.round(node.coordinateScale));
        int extent = horizontal ? node.width : node.height;
        int thickness = Math.min((horizontal ? node.height : node.width) - 2 * inset,
                Math.max(1, (int) Math.round(3 * node.coordinateScale)));
        int length = extent - 2 * inset;
        if (thickness <= 0 || length <= 1) return null;
        int minimumThumb = Math.max(4, (int) Math.round(12 * node.coordinateScale));
        // The retained range is in authored units; painting uses committed logical pixels.
        int thumbLength = Math.min(length - 1, Math.max(minimumThumb,
                (int) Math.round(length * (double) extent / (extent + node.scrollRange * node.coordinateScale))));
        double offset = node.props.get("scrollOffset") instanceof Number number ? number.doubleValue() : 0;
        double progress = Math.max(0, Math.min(1, offset / node.scrollRange));
        int x = horizontal ? node.x + inset : node.x + node.width - inset - thickness;
        int y = horizontal ? node.y + node.height - inset - thickness : node.y + inset;
        int thumbStart = (horizontal ? x : y) + (int) Math.round(progress * (length - thumbLength));
        return new Geometry(x, y, horizontal ? length : thickness, horizontal ? thickness : length,
                horizontal, thumbStart, thumbLength);
    }

    record Geometry(int x, int y, int width, int height, boolean horizontal, int thumbStart, int thumbLength) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        double position(double mouseX, double mouseY) { return horizontal ? mouseX : mouseY; }
        int thumbX() { return horizontal ? thumbStart : x; }
        int thumbY() { return horizontal ? y : thumbStart; }
        int thumbWidth() { return horizontal ? thumbLength : width; }
        int thumbHeight() { return horizontal ? height : thumbLength; }

        boolean onThumb(double position) {
            return position >= thumbStart && position < thumbStart + thumbLength;
        }

        double offsetAt(double position, double grabFraction, double range) {
            double progress = (position - (horizontal ? x : y) - grabFraction * thumbLength)
                    / ((horizontal ? width : height) - thumbLength);
            return Math.max(0, Math.min(1, progress)) * range;
        }
    }

    record Capture(long identity, boolean horizontal, boolean thumb, double grabFraction) { }
}
//?}
