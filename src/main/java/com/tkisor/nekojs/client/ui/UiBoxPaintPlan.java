//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import java.util.ArrayList;
import java.util.List;

final class UiBoxPaintPlan {
    private final List<Span> backgrounds;
    private final List<Span> borders;

    private UiBoxPaintPlan(List<Span> backgrounds, List<Span> borders) {
        this.backgrounds = List.copyOf(backgrounds);
        this.borders = List.copyOf(borders);
    }

    static UiBoxPaintPlan prepare(int x, int y, int width, int height, int radius, int borderWidth,
                                  int clipX, int clipY, int clipWidth, int clipHeight) {
        List<Span> backgrounds = new ArrayList<>();
        List<Span> leftBorders = new ArrayList<>();
        List<Span> rightBorders = new ArrayList<>();
        if (width <= 0 || height <= 0 || clipWidth <= 0 || clipHeight <= 0) {
            return new UiBoxPaintPlan(backgrounds, leftBorders);
        }
        int corners = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        int border = Math.max(0, Math.min(borderWidth, Math.min(width, height)));
        long left = Math.max((long) x, clipX);
        long right = Math.min((long) x + width, (long) clipX + clipWidth);
        long top = Math.max((long) y, clipY);
        long bottom = Math.min((long) y + height, (long) clipY + clipHeight);
        if (left >= right || top >= bottom) return new UiBoxPaintPlan(backgrounds, leftBorders);
        long innerWidth = (long) width - 2L * border;
        long innerHeight = (long) height - 2L * border;
        for (long row = top; row < bottom; row++) {
            int localRow = (int) (row - y);
            int inset = inset(corners, localRow, height);
            long outerLeft = Math.max(left, (long) x + inset);
            long outerRight = Math.min(right, (long) x + width - inset);
            long innerLeft = outerRight;
            long innerRight = outerRight;
            if (innerWidth > 0 && innerHeight > 0 && localRow >= border && localRow < height - border) {
                int innerInset = inset(Math.max(0, corners - border), localRow - border, (int) innerHeight);
                innerLeft = Math.max(outerLeft, (long) x + border + innerInset);
                innerRight = Math.min(outerRight, (long) x + width - border - innerInset);
                innerLeft = Math.min(innerLeft, outerRight);
                innerRight = Math.max(innerLeft, innerRight);
            }
            append(backgrounds, innerLeft, row, innerRight, row + 1);
            append(leftBorders, outerLeft, row, innerLeft, row + 1);
            append(rightBorders, innerRight, row, outerRight, row + 1);
        }
        leftBorders.addAll(rightBorders);
        return new UiBoxPaintPlan(backgrounds, leftBorders);
    }

    private static int inset(int radius, int row, int height) {
        int edge = Math.min(row, height - 1 - row);
        if (radius == 0 || edge >= radius) return 0;
        double distance = radius - edge - 0.5;
        return Math.max(0, (int) Math.ceil(radius - Math.sqrt((double) radius * radius - distance * distance) - 0.5));
    }

    private static void append(List<Span> spans, long left, long top, long right, long bottom) {
        if (left >= right) return;
        if (!spans.isEmpty()) {
            Span previous = spans.getLast();
            if (previous.left() == left && previous.right() == right && previous.bottom() == top) {
                spans.set(spans.size() - 1, new Span((int) left, previous.top(), (int) right, (int) bottom));
                return;
            }
        }
        spans.add(new Span((int) left, (int) top, (int) right, (int) bottom));
    }

    void paint(Fill fill, int background, int border, double opacity) {
        int backgroundColor = withOpacity(background, opacity);
        int borderColor = withOpacity(composite(border, background), opacity);
        for (Span span : backgrounds) fill.accept(span.left(), span.top(), span.right(), span.bottom(), backgroundColor);
        for (Span span : borders) fill.accept(span.left(), span.top(), span.right(), span.bottom(), borderColor);
    }

    private static int withOpacity(int color, double opacity) {
        int alpha = (int) Math.round((color >>> 24) * opacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int composite(int foreground, int background) {
        double foregroundAlpha = (foreground >>> 24) / 255.0;
        double backgroundContribution = (background >>> 24) / 255.0 * (1 - foregroundAlpha);
        double alpha = foregroundAlpha + backgroundContribution;
        if (alpha == 0) return 0;
        int result = (int) Math.round(alpha * 255) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int channel = (int) Math.round((((foreground >>> shift) & 255) * foregroundAlpha
                    + ((background >>> shift) & 255) * backgroundContribution) / alpha);
            result |= channel << shift;
        }
        return result;
    }

    private record Span(int left, int top, int right, int bottom) { }

    @FunctionalInterface
    interface Fill {
        void accept(int left, int top, int right, int bottom, int color);
    }
}
//?}
