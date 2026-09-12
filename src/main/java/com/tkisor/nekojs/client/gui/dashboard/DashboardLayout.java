package com.tkisor.nekojs.client.gui.dashboard;

/** GUI-scaled pixels, independent of Minecraft and of either rendering API. */
public record DashboardLayout(Rect panel, Rect header, Rect sidebar, Rect toggle,
        Rect search, Rect clear, Rect list, Rect detailHeader, Rect copyPath,
        Rect copyDetails, Rect openSource, Rect details, Rect footer, int cardHeight,
        boolean compact) {
    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Negative rectangle");
        }
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < right() && py < bottom();
        }
        public Rect inset(int n) {
            int dx = Math.min(n, width / 2), dy = Math.min(n, height / 2);
            return new Rect(x + dx, y + dy, width - 2 * dx, height - 2 * dy);
        }
    }

    /**
     * Builds a full-viewport layout. The HTML prototype uses a fixed app frame,
     * but an in-game Screen already owns the viewport, so leaving a margin here
     * would expose the world behind the dashboard and make the proportions lie.
     */
    public static DashboardLayout calculate(int width, int height, boolean collapsed, int lineHeight) {
        int w = Math.max(0, width), h = Math.max(0, height);
        int line = Math.max(9, lineHeight);
        Rect panel = new Rect(0, 0, w, h);

        // At GUI scale 5 a 512px logical viewport is still a small screen. Keep
        // the prototype's compact 210px sidebar profile until the detail pane can
        // comfortably hold three action buttons and readable metadata.
        boolean compact = w < 600;
        boolean shortHeight = h < 600;
        int headerHeight = Math.min(h, compact ? Math.max(32, line * 3 + 2)
                : shortHeight ? Math.max(30, line * 2 + 8) : Math.max(36, line * 3 + 8));
        int footerHeight = Math.min(Math.max(0, h - headerHeight), compact ? Math.max(28, line * 3)
                : shortHeight ? Math.max(20, line + 10) : Math.max(22, line * 2 + 4));
        Rect header = new Rect(0, 0, w, headerHeight);
        Rect footer = new Rect(0, h - footerHeight, w, footerHeight);
        int bodyHeight = Math.max(0, footer.y() - header.bottom());

        int desiredSide = collapsed ? 40 : compact ? 210
                : w < 950 ? 260 : Math.max(260, Math.min(330, w * 30 / 100));
        int minimumRight = compact ? 150 : 300;
        int sideWidth = Math.min(desiredSide, Math.max(0, w - Math.min(minimumRight, w)));
        Rect side = new Rect(0, header.bottom(), sideWidth, bodyHeight);

        int paneHeight = Math.min(bodyHeight, compact ? line * 3 + 6 : line * 2 + 8);
        int toggleSize = Math.max(0, Math.min(compact ? 28 : 24, Math.max(0, paneHeight - 2)));
        Rect toggle = new Rect(Math.max(side.x(), side.right() - toggleSize - 8), side.y() + 3,
                Math.min(toggleSize, sideWidth), Math.min(toggleSize, paneHeight - 2));

        int searchY = Math.min(side.bottom(), side.y() + paneHeight + 5);
        int searchHeight = Math.min(compact ? 24 : 22, Math.max(0, side.bottom() - searchY));
        int clearWidth = collapsed ? 0 : compact ? 58 : Math.min(72, Math.max(50, sideWidth / 4));
        int searchWidth = collapsed ? 0 : Math.max(0, sideWidth - 16 - clearWidth - 6);
        Rect search = new Rect(side.x() + 8, searchY, searchWidth, searchHeight);
        Rect clear = new Rect(search.right() + 6, searchY, clearWidth, searchHeight);
        int listY = Math.min(side.bottom(), searchY + searchHeight + 7);
        Rect list = new Rect(side.x() + 8, listY, Math.max(0, sideWidth - 16), Math.max(0, side.bottom() - listY - 8));

        int rightX = side.right();
        int rightWidth = Math.max(0, w - rightX);
        int detailHeaderHeight;
        if (compact) {
            // Eyebrow + filename, then two buttons on the first row and VS Code
            // on the second row, matching the responsive web prototype.
            int desired = line * 2 + 10 + 24 * 2 + 6;
            detailHeaderHeight = Math.min(desired, Math.max(0, bodyHeight - 84));
        } else {
            detailHeaderHeight = Math.min(Math.max(42, line * 3 + 12), Math.max(0, bodyHeight - 84));
        }
        Rect detailHeader = new Rect(rightX, side.y(), rightWidth, detailHeaderHeight);

        int gap = 5;
        Rect copyPath, copyDetails, open;
        if (compact) {
            int actionWidth = Math.max(0, rightWidth - 16);
            // Match the prototype's intrinsic button sizes instead of stretching
            // the two copy buttons across the entire detail pane.
            int copyWidth = Math.min(96, Math.max(0, (actionWidth - gap) / 2));
            int openWidth = Math.min(140, actionWidth);
            int actionY = detailHeader.y() + line * 2 + 10;
            int actionHeight = Math.min(24, Math.max(0, (detailHeader.bottom() - actionY - gap) / 2));
            copyPath = new Rect(detailHeader.x() + 8, actionY, copyWidth, actionHeight);
            copyDetails = new Rect(copyPath.right() + gap, actionY, copyWidth, actionHeight);
            open = new Rect(detailHeader.x() + 8, copyPath.bottom() + gap, openWidth, actionHeight);
        } else {
            int buttonWidth = Math.max(68, Math.min(112, rightWidth / 5));
            int usedActions = buttonWidth * 3 + gap * 2;
            int actionY = detailHeader.y() + Math.max(4, (detailHeader.height() - 22) / 2);
            int actionX = Math.max(rightX + 8, detailHeader.right() - 8 - usedActions);
            copyPath = new Rect(actionX, actionY, buttonWidth, Math.min(22, detailHeader.height()));
            copyDetails = new Rect(copyPath.right() + gap, actionY, buttonWidth, Math.min(22, detailHeader.height()));
            open = new Rect(copyDetails.right() + gap, actionY, buttonWidth, Math.min(22, detailHeader.height()));
        }

        int detailsY = Math.min(footer.y(), detailHeader.bottom() + 2);
        Rect details = new Rect(rightX + 8, detailsY, Math.max(0, rightWidth - 16),
                Math.max(0, footer.y() - detailsY - 2));

        // Every list item has the same fixed height, just like the prototype's
        // 118px flex-basis. Two path lines, two message lines and one footer line
        // are reserved even when their content is shorter.
        int cardHeight = compact ? Math.max(76, line * 6 + 18)
                : shortHeight ? 84 : 96;
        return new DashboardLayout(panel, header, side, toggle, search, clear, list,
                detailHeader, copyPath, copyDetails, open, details, footer, cardHeight, compact);
    }
}






