package com.tkisor.nekojs.client.gui.dashboard;

import com.tkisor.nekojs.core.error.ErrorDashboardModel;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import static com.tkisor.nekojs.client.gui.dashboard.DashboardLayout.Rect;

/** Shared presentation only. Search, identity and selection belong to ErrorDashboardModel. */
public final class DashboardView {
    public interface Canvas {
        void fill(Rect rect, int color);
        void text(String text, int x, int y, int color);
        void clip(Rect rect);
        void unclip();
    }
    public interface Labels { String get(String key, Object... args); }
    public enum Action { NONE, TOGGLE, SEARCH, CLEAR, LIST, DETAILS, COPY_PATH, COPY_DETAILS, OPEN_SOURCE, RAW, STACK }
    private enum Kind { PATH, META, ID, TEXT, MUTED, ERROR, RAW, RAW_BODY, STACK, STACK_BODY, EMPTY, EMPTY_NOTE }
    private record Row(String text, Kind kind) {}
    private record Card(ErrorSummaryDTO error, List<String> path, List<String> message) {}
    private static final class DetailState {
        final DashboardScroll scroll = new DashboardScroll();
        boolean rawOpen = true, stackOpen;
    }

    public static final int BG = 0xFF101D31, PANEL = 0xFF172942, LINE = 0xFF304C70,
            TEXT = 0xFFE7F1FF, MUTED = 0xFFA9BED8, BLUE = 0xFF79B9FF, RED = 0xFFFF8F98;
    private static final int PANEL_BORDER = 0xFF466A99, BUTTON = 0xFF203C60,
            BUTTON_HOVER = 0xFF2B5684, BUTTON_BORDER = 0xFF4C76A6, DISABLED = 0xFF6E87A5,
            CARD = 0xFF1D3554, CARD_HOVER = 0xFF27486E, CARD_ACTIVE = 0xFF27517D,
            CARD_ACTIVE_BORDER = 0xFF7AB9FF, CALLOUT = 0xFF352D43, CALLOUT_TEXT = 0xFFFFC2C4,
            FOLD = 0xFF1D3552, PRE = 0xFF12243A, PRE_TEXT = 0xFFC2D8F2,
            SEARCH_BG = 0xFF0D1B2D, MARK_BG = 0xFF1B3454, MARK_BORDER = 0xFF4D78A8,
            META_BG = 0xFF244462, META_BORDER = 0xFF47709D, META_TEXT = 0xFFC2D7ED,
            CARD_MESSAGE = 0xFFC8D9ED;

    private final ErrorDashboardModel model;
    private final Labels labels;
    private final DashboardScroll listScroll = new DashboardScroll();
    private final DashboardScroll dialogScroll = new DashboardScroll();
    private final Map<String, DetailState> states = new LinkedHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private List<Card> cards = List.of();
    private ErrorSummaryDTO displayed;
    private String stats = "", matches = "", filename = "";
    private ErrorSummaryDTO cachedError;
    private int cachedWidth;
    private boolean cachedExcerpt;
    private List<Row> cachedPrefix = List.of(), cachedMessage = List.of(), cachedRaw = List.of(), cachedStack = List.of();
    private DashboardLayout layout;
    private ToIntFunction<String> measure = String::length;
    private int width, height, lineStep = 10, totalErrors;
    private long totalOccurrences;
    private boolean collapsed, sourceAvailable;
    private String sourceReason = "", feedback = "", dialogTitle, dialogBody;
    private Action focus = Action.LIST, dragging = Action.NONE;
    private double dragPointerStart;
    private int dragScrollStart;

    public DashboardView(ErrorDashboardModel model, Labels labels) { this.model = model; this.labels = labels; }
    public String t(String key, Object... args) { return labels.get(key, args); }
    public DashboardLayout layout() { return layout; }
    public boolean collapsed() { return collapsed; }
    public Action focus() { return focus; }
    public void focus(Action action) { focus = action; }
    public boolean modal() { return dialogTitle != null; }
    public String sourceReason() { return sourceReason; }
    public void status(String text) { feedback = text; }
    public void dialog(String title, String body) { dialogTitle = title; dialogBody = body; dialogScroll.to(0); cacheDialog(); }
    public void dismissDialog() { dialogTitle = null; dialogBody = null; }

    public void configure(int width, int height, int lineStep, ToIntFunction<String> measure) {
        this.width = width; this.height = height; this.lineStep = Math.max(10, lineStep); this.measure = measure;
        layout = DashboardLayout.calculate(width, height, collapsed, this.lineStep);
        cachedError = null; // The font provider may also have changed during resize/resource reload.
        rebuild(); cacheDialog();
    }

    public void refresh() {
        if (model.selectedError() == null && !model.visibleErrors().isEmpty()) {
            model.select(model.visibleErrors().get(0).id());
            listScroll.to(0); // A fallback selection must not be hidden below the previous projection.
        }
        feedback = "";
        if (layout != null) rebuild();
    }

    public void location(boolean available, String reason) {
        sourceAvailable = available; sourceReason = reason;
        if (layout != null) rebuildRows();
    }

    public void toggleSidebar() {
        collapsed = !collapsed;
        if (collapsed && (focus == Action.SEARCH || focus == Action.CLEAR || focus == Action.LIST)) focus = Action.TOGGLE;
        configure(width, height, lineStep, measure);
    }

    public void focusSearch() {
        if (collapsed) toggleSidebar();
        focus = Action.SEARCH;
    }

    private DetailState detailState() {
        var error = model.selectedError();
        String id = error == null ? "" : error.id();
        if (!states.containsKey(id) && states.size() >= 256) states.remove(states.keySet().iterator().next());
        return states.computeIfAbsent(id, ignored -> new DetailState());
    }

    private void rebuild() {
        totalErrors = model.totalErrors();
        totalOccurrences = model.totalOccurrences();
        stats = t("totals", totalErrors, totalOccurrences);
        matches = t("matches", model.visibleErrors().size(), totalErrors);
        List<Card> next = new ArrayList<>();
        int w = Math.max(1, layout.list().width() - 20);
        for (var e : model.visibleErrors()) next.add(new Card(e,
                DashboardText.preview(e.path(), w, 2, measure), DashboardText.preview(e.message(), w, 2, measure)));
        cards = List.copyOf(next); rebuildRows();
        if ((focus == Action.RAW || focus == Action.STACK) && !enabled(focus)) focus = Action.DETAILS;
    }

    private void add(String text, Kind kind) {
        for (String line : DashboardText.wrap(text, Math.max(1, layout.details().width() - 14), measure)) {
            rows.add(new Row(line, kind));
        }
    }

    private List<Row> wrapped(String text, Kind kind, int width) {
        List<Row> result = new ArrayList<>();
        for (String line : DashboardText.wrap(text, width, measure)) result.add(new Row(line, kind));
        return List.copyOf(result);
    }

    private void rebuildRows() {
        rows.clear();
        var e = model.selectedError(); displayed = e;
        filename = e == null ? t("details") : e.path().isBlank() ? t("unknown_path")
                : e.path().substring(Math.max(e.path().lastIndexOf('/'), e.path().lastIndexOf('\\')) + 1);
        if (e == null) {
            rows.add(new Row(t(totalErrors == 0 ? "empty" : "no_match"), Kind.EMPTY));
            rows.add(new Row(t(totalErrors == 0 ? "empty_note" : "no_match_note"), Kind.EMPTY_NOTE));
            return;
        }
        int width = Math.max(1, layout.details().width() - 14);
        if (cachedError != e || cachedWidth != width) {
            cachedError = e; cachedWidth = width;
            List<Row> prefix = new ArrayList<>();
            prefix.addAll(wrapped(e.path().isBlank() ? t("unknown_path") : e.path(), Kind.PATH, width));
            prefix.add(new Row(lineLabel(e) + "   ·   " + t("count", e.count()), Kind.META));
            prefix.add(new Row("ID: " + e.id(), Kind.META));
            prefix.add(new Row("", Kind.TEXT)); cachedPrefix = List.copyOf(prefix);
            cachedMessage = wrapped(e.message(), Kind.ERROR, width);
            DashboardText.Parts parts = DashboardText.splitStack(e.fullDetails());
            cachedRaw = wrapped(parts.body(), Kind.RAW_BODY, width);
            cachedStack = parts.stack().isEmpty() ? List.of() : wrapped(parts.stack(), Kind.STACK_BODY, width);
            cachedExcerpt = e.fullDetails().contains(">> 异常代码片段");
        }
        rows.addAll(cachedPrefix); rows.addAll(cachedMessage);
        if (!sourceAvailable && !sourceReason.isBlank()) add(sourceReason, Kind.MUTED);
        else if (sourceAvailable && sourceReason.equals(t("file_only"))) add(sourceReason, Kind.MUTED);
        add(t(cachedExcerpt ? "source_note" : "no_excerpt"), Kind.MUTED);
        rows.add(new Row("", Kind.TEXT));
        DetailState state = detailState();
        rows.add(new Row((state.rawOpen ? "-" : "+") + "  " + t("raw"), Kind.RAW));
        if (state.rawOpen) rows.addAll(cachedRaw);
        if (!cachedStack.isEmpty()) {
            rows.add(new Row((state.stackOpen ? "-" : "+") + "  " + t("stack"), Kind.STACK));
            if (state.stackOpen) rows.addAll(cachedStack);
        }
        add(t("fold_note"), Kind.MUTED);
    }

    public String lineLabel(ErrorSummaryDTO e) { return e.line() > 0 ? t("line", e.line()) : t("unknown_line"); }
    public boolean enabled(Action a) {
        var e = displayed;
        return switch (a) {
            case COPY_PATH -> e != null && !e.path().isBlank();
            case COPY_DETAILS -> e != null;
            case OPEN_SOURCE -> e != null && sourceAvailable;
            case CLEAR -> !model.search().isEmpty();
            case RAW -> e != null;
            case STACK -> e != null && !cachedStack.isEmpty();
            default -> true;
        };
    }

    public Rect rect(Action action) {
        return switch (action) {
            case TOGGLE -> layout.toggle(); case SEARCH -> layout.search(); case CLEAR -> layout.clear();
            case LIST -> layout.list(); case DETAILS, RAW, STACK -> layout.details();
            case COPY_PATH -> layout.copyPath(); case COPY_DETAILS -> layout.copyDetails();
            case OPEN_SOURCE -> layout.openSource(); default -> layout.panel();
        };
    }

    private String fit(String text, int width) { return DashboardText.fit(text, width, measure); }
    private void border(Canvas g, Rect r, int color) {
        if (r.width() == 0 || r.height() == 0) return;
        g.fill(new Rect(r.x(), r.y(), r.width(), 1), color);
        g.fill(new Rect(r.x(), r.bottom() - 1, r.width(), 1), color);
        g.fill(new Rect(r.x(), r.y(), 1, r.height()), color);
        g.fill(new Rect(r.right() - 1, r.y(), 1, r.height()), color);
    }
    private int textY(int y) { return y + Math.max(1, (lineStep - 8) / 2); }
    private int centerTextY(int y, int height) {
        int fontHeight = Math.max(8, lineStep - 1);
        return y + Math.max(0, (height - fontHeight) / 2);
    }
    private void centered(Canvas g, String text, int centerX, int y, int color) {
        g.text(text, centerX - measure.applyAsInt(text) / 2, textY(y), color);
    }
    private void centeredIn(Canvas g, String text, int centerX, int y, int height, int color) {
        g.text(text, centerX - measure.applyAsInt(text) / 2, centerTextY(y, height), color);
    }

    private String actionLabel(Action action) {
        int buttonWidth = rect(action).width();
        return switch (action) {
            case TOGGLE -> collapsed ? ">" : "<";
            case CLEAR -> layout.clear().width() >= 44 ? t("clear") : layout.clear().width() >= 36 ? t("clear_short") : "×";
            case COPY_PATH -> t(buttonWidth < 50 ? "copy_path_tiny" : buttonWidth < 92 ? "copy_path_short" : "copy_path");
            case COPY_DETAILS -> t(buttonWidth < 50 ? "copy_full_tiny" : buttonWidth < 92 ? "copy_full_short" : "copy_full");
            case OPEN_SOURCE -> t(buttonWidth < 92 ? "vscode_short" : "vscode");
            default -> "";
        };
    }

    private void button(Canvas g, Action action, int mx, int my) {
        Rect r = rect(action); boolean on = enabled(action), hover = on && r.contains(mx, my);
        g.fill(r, hover ? BUTTON_HOVER : BUTTON); border(g, r, focus == action ? BLUE : BUTTON_BORDER);
        String label = fit(actionLabel(action), r.width() - 6);
        int color = on ? TEXT : DISABLED;
        int x = r.x() + Math.max(3, (r.width() - measure.applyAsInt(label)) / 2);
        g.text(label, x, centerTextY(r.y(), r.height()), color);
    }

    private int listHeight() {
        if (cards.isEmpty()) return 0;
        int stride = layout.cardHeight() + 4;
        // If the viewport is shorter than a card, stop at the last card's start rather than
        // scrolling its text completely above the viewport. This keeps the final item
        // inspectable on very small GUI-scaled windows.
        return (cards.size() - 1) * stride + Math.min(layout.cardHeight(), Math.max(1, layout.list().height()));
    }
    private int detailHeight() { return rows.size() * lineStep + 4; }

    private Rect scrollbarThumb(Rect viewport, int contentHeight, int offset) {
        int size = (int) Math.min(viewport.height(), Math.max(8, (long) viewport.height() * viewport.height() / Math.max(1, contentHeight)));
        int y = viewport.y() + (int) ((long) offset * (viewport.height() - size) / Math.max(1, contentHeight - viewport.height()));
        return new Rect(viewport.right() - 3, y, 3, size);
    }

    private void scrollbar(Canvas g, Rect viewport, int contentHeight, int offset) {
        if (contentHeight <= viewport.height() || viewport.height() <= 0) return;
        g.fill(new Rect(viewport.right() - 3, viewport.y(), 3, viewport.height()), LINE);
        g.fill(scrollbarThumb(viewport, contentHeight, offset), BLUE);
    }

    private void searchIcon(Canvas g, Rect r) {
        int x = r.x() + 5, y = r.y() + Math.max(3, (r.height() - 7) / 2), c = BLUE;
        g.fill(new Rect(x, y, 5, 1), c); g.fill(new Rect(x, y + 4, 5, 1), c);
        g.fill(new Rect(x, y, 1, 5), c); g.fill(new Rect(x + 4, y, 1, 5), c);
        g.fill(new Rect(x + 5, y + 5, 2, 2), c);
    }

    private void paintHeader(Canvas g) {
        Rect header = layout.header(); g.fill(header, BG);
        g.fill(new Rect(header.x(), header.bottom() - 1, header.width(), 1), LINE);
        boolean compact = layout.compact();
        int markSize = Math.max(14, Math.min(22, header.height() - 8));
        Rect mark = new Rect(header.x() + 7, header.y() + (header.height() - markSize) / 2, markSize, markSize);
        if (!compact) {
            g.fill(mark, MARK_BG); border(g, mark, MARK_BORDER);
            centeredIn(g, "!", mark.x() + mark.width() / 2, mark.y(), mark.height(), BLUE);
        }
        int textLeft = compact ? header.x() + 8 : mark.right() + 7;
        int statsWidth = Math.min(header.width() * 45 / 100, measure.applyAsInt(stats));
        int textWidth = Math.max(0, header.right() - statsWidth - textLeft - 8);
        g.text(fit(t("title"), textWidth), textLeft, centerTextY(header.y(), header.height()), TEXT);
        String count = Integer.toString(totalErrors), caption = t("totals_after", totalOccurrences);
        int countWidth = measure.applyAsInt(count), captionWidth = measure.applyAsInt(caption), totalWidth = countWidth + 4 + captionWidth;
        int statsY = textY(header.y() + Math.max(3, (header.height() - lineStep) / 2));
        if (totalWidth <= header.width() * 52 / 100) {
            g.text(count, header.right() - totalWidth - 7, statsY, RED);
            g.text(caption, header.right() - captionWidth - 7, statsY, MUTED);
        } else {
            g.text(fit(stats, Math.max(0, header.width() / 2)), header.right() - Math.min(header.width() / 2, measure.applyAsInt(stats)) - 7, statsY, MUTED);
        }
    }

    private void paintCard(Canvas g, Rect list, int index, int y, int mx, int my) {
        Card c = cards.get(index);
        Rect card = new Rect(list.x(), y, Math.max(0, list.width() - 5), layout.cardHeight());
        if (card.width() <= 0 || card.height() <= 0) return;
        boolean active = displayed != null && displayed.id().equals(c.error().id());
        g.fill(card, active ? CARD_ACTIVE : card.contains(mx, my) ? CARD_HOVER : CARD);
        border(g, card, active ? CARD_ACTIVE_BORDER : LINE);
        if (active) g.fill(new Rect(card.x(), card.y(), 2, card.height()), BLUE);

        // Keep the card geometry invariant. The previous tall/compact branches
        // moved the path, message and line independently, which made cards with
        // different text lengths look like they had different proportions.
        int pad = Math.max(6, lineStep - 2);
        String count = t("count", c.error().count());
        int countWidth = Math.min(Math.max(18, card.width() / 3), measure.applyAsInt(count));
        int pathWidth = Math.max(1, card.width() - countWidth - pad * 2 - 15);
        g.fill(new Rect(card.x() + pad, card.y() + pad + 3, 3, 3), RED);
        Rect countChip = new Rect(Math.max(card.x() + pad, card.right() - countWidth - pad - 6),
                card.y() + pad - 2, countWidth + 6, lineStep + 3);
        g.fill(countChip, 0xFF493044);
        g.text(fit(count, countWidth), countChip.x() + 3, textY(countChip.y()), RED);

        int pathY = card.y() + pad;
        for (String line : c.path()) {
            g.text(fit(line, pathWidth), card.x() + pad + 7, textY(pathY), TEXT);
            pathY += lineStep;
        }
        int messageY = card.y() + pad + lineStep * 2 + 4;
        for (String line : c.message()) {
            g.text(fit(line, Math.max(1, card.width() - pad * 2)), card.x() + pad, textY(messageY), CARD_MESSAGE);
            messageY += lineStep;
        }
        int bottomY = card.bottom() - pad - lineStep;
        g.text(fit(lineLabel(c.error()), Math.max(1, card.width() - pad * 2 - countWidth - 8)),
                card.x() + pad, textY(bottomY), MUTED);
        g.text(fit(count, countWidth), card.right() - countWidth - pad - 2, textY(bottomY), RED);    }

    private void paintSidebar(Canvas g, int mx, int my) {
        Rect side = layout.sidebar(); g.fill(side, PANEL); border(g, side, LINE);
        int paneHeight = Math.max(0, layout.toggle().bottom() - side.y() + 2);
        g.fill(new Rect(side.x(), side.y(), side.width(), 1), LINE);
        if (paneHeight > 1) g.fill(new Rect(side.x(), Math.min(side.bottom() - 1, side.y() + paneHeight), side.width(), 1), LINE);
        button(g, Action.TOGGLE, mx, my);
        if (collapsed) return;
        String title = t("list_title") + "  " + matches;
        g.text(fit(title, Math.max(0, layout.toggle().x() - side.x() - 8)), side.x() + 6, centerTextY(side.y(), paneHeight), MUTED);
        Rect search = layout.search(); g.fill(search, SEARCH_BG); border(g, search, focus == Action.SEARCH ? BLUE : BUTTON_BORDER);
        searchIcon(g, search);
        button(g, Action.CLEAR, mx, my);
        Rect list = layout.list(); int content = listHeight(), offset = listScroll.offset(content, list.height());
        g.clip(list);
        int first = Math.max(0, offset / (layout.cardHeight() + 4));
        int last = Math.min(cards.size(), first + list.height() / (layout.cardHeight() + 4) + 2);
        for (int i = first; i < last; i++) paintCard(g, list, i, list.y() + i * (layout.cardHeight() + 4) - offset, mx, my);
        scrollbar(g, list, content, offset); g.unclip();
    }

    private void paintDetailHeader(Canvas g, int mx, int my) {
        Rect header = layout.detailHeader(); g.fill(header, BG);
        g.fill(new Rect(header.x(), header.bottom() - 1, header.width(), 1), LINE);
        boolean stacked = layout.compact();
        int textWidth = stacked ? Math.max(0, header.width() - 12)
                : Math.max(0, layout.copyPath().x() - header.x() - 10);
        g.text(fit(t("detail_eyebrow"), textWidth), header.x() + 6, textY(header.y() + 2), RED);
        g.text(fit(filename, textWidth), header.x() + 6, textY(header.y() + 2 + lineStep), TEXT);
        button(g, Action.COPY_PATH, mx, my); button(g, Action.COPY_DETAILS, mx, my); button(g, Action.OPEN_SOURCE, mx, my);
    }

    private void paintRow(Canvas g, Rect area, Row row, int y, int index) {
        Rect content = new Rect(area.x(), y, Math.max(0, area.width() - 6), lineStep);
        switch (row.kind()) {
            case ERROR -> {
                g.fill(content, CALLOUT); g.fill(new Rect(content.x(), content.y(), 2, content.height()), RED);
                g.text(row.text(), content.x() + 7, textY(y), CALLOUT_TEXT);
            }
            case META -> {
                int width = Math.min(content.width(), measure.applyAsInt(row.text()) + 10);
                Rect chip = new Rect(content.x(), y - 1, width, lineStep + 2);
                g.fill(chip, META_BG); border(g, chip, META_BORDER);
                g.text(fit(row.text(), Math.max(0, width - 6)), chip.x() + 3, textY(y), META_TEXT);
            }
            case RAW -> paintFoldHeader(g, content, row.text(), focus == Action.RAW);
            case STACK -> paintFoldHeader(g, content, row.text(), focus == Action.STACK);
            case RAW_BODY, STACK_BODY -> {
                boolean first = index == 0 || rows.get(index - 1).kind() != row.kind();
                boolean last = index + 1 >= rows.size() || rows.get(index + 1).kind() != row.kind();
                g.fill(content, PRE);
                g.fill(new Rect(content.x(), content.y(), 1, content.height()), LINE);
                g.fill(new Rect(content.right() - 1, content.y(), 1, content.height()), LINE);
                if (first) g.fill(new Rect(content.x(), content.y(), content.width(), 1), LINE);
                if (last) g.fill(new Rect(content.x(), content.bottom() - 1, content.width(), 1), LINE);
                g.text(row.text(), content.x() + 5, textY(y), PRE_TEXT);
            }
            case PATH, ID, MUTED -> g.text(row.text(), content.x() + 2, textY(y), MUTED);
            case EMPTY, EMPTY_NOTE -> { }
            default -> g.text(row.text(), content.x() + 2, textY(y), TEXT);
        }
    }

    private void paintFoldHeader(Canvas g, Rect content, String text, boolean focused) {
        g.fill(content, FOLD); border(g, content, focused ? BLUE : LINE);
        boolean open = text.startsWith("-");
        int tx = content.x() + 5, ty = content.y() + Math.max(2, (lineStep - 5) / 2);
        if (open) {
            g.fill(new Rect(tx, ty, 5, 1), TEXT);
            g.fill(new Rect(tx + 1, ty + 1, 3, 1), TEXT);
            g.fill(new Rect(tx + 2, ty + 2, 1, 1), TEXT);
        } else {
            g.fill(new Rect(tx, ty, 1, 5), TEXT);
            g.fill(new Rect(tx + 1, ty + 1, 1, 3), TEXT);
            g.fill(new Rect(tx + 2, ty + 2, 1, 1), TEXT);
        }
        String label = text.length() > 1 && (text.charAt(0) == '-' || text.charAt(0) == '+')
                ? text.substring(1).trim() : text;
        g.text(fit(label, Math.max(1, content.width() - 16)), content.x() + 14, textY(content.y()), TEXT);
    }

    private void paintEmpty(Canvas g, Rect area) {
        int centerX = area.x() + area.width() / 2;
        int startY = area.y() + Math.max(12, (area.height() - 54) / 3);
        String icon = totalErrors == 0 ? "!" : "?";
        Rect mark = new Rect(centerX - 9, startY, 18, 18);
        g.fill(mark, MARK_BG); border(g, mark, MARK_BORDER);
        centeredIn(g, icon, centerX, mark.y(), mark.height(), BLUE);
        String title = rows.isEmpty() ? "" : rows.get(0).text();
        String note = rows.size() < 2 ? "" : rows.get(1).text();
        centered(g, title, centerX, mark.bottom() + 7, TEXT);
        centered(g, note, centerX, mark.bottom() + 7 + lineStep + 3, MUTED);
    }

    private void paintDetails(Canvas g) {
        Rect details = layout.details();
        if (displayed == null) { paintEmpty(g, details); return; }
        int offset = detailState().scroll.offset(detailHeight(), details.height());
        g.clip(details);
        int first = Math.max(0, offset / lineStep), last = Math.min(rows.size(), first + details.height() / lineStep + 2);
        for (int i = first; i < last; i++) paintRow(g, details, rows.get(i), details.y() + i * lineStep - offset, i);
        scrollbar(g, details, detailHeight(), offset); g.unclip();
    }

    public void paint(Canvas g, int mx, int my) {
        g.fill(new Rect(0, 0, width, height), 0xDF101722);
        g.fill(layout.panel(), BG); border(g, layout.panel(), PANEL_BORDER);
        paintHeader(g);
        paintSidebar(g, mx, my);
        paintDetailHeader(g, mx, my);
        paintDetails(g);
        Rect footer = layout.footer(); g.fill(footer, PANEL); g.fill(new Rect(footer.x(), footer.y(), footer.width(), 1), LINE);
        String hint = t(width < 400 ? "keys_short" : "keys"), message = statusMessage();
        if (layout.compact()) {
            g.text(fit(message, footer.width() - 12), footer.x() + 6, textY(footer.y() + 3), BLUE);
            g.text(fit(hint, footer.width() - 12), footer.x() + 6, textY(footer.y() + 3 + lineStep), MUTED);
        } else {
            int hintWidth = measure.applyAsInt(hint);
            g.text(fit(message, footer.width() - hintWidth - 16), footer.x() + 6, textY(footer.y() + 2), BLUE);
            g.text(hint, footer.right() - hintWidth - 6, textY(footer.y() + 2), MUTED);
        }
        if (modal()) paintDialog(g);
    }

    private Rect dialogRect() { return new Rect(12, Math.max(8, (height - Math.min(150, height - 24)) / 2), Math.max(1, width - 24), Math.max(1, Math.min(150, height - 24))); }
    private Rect dialogClose() { Rect r = dialogRect(); return new Rect(r.right() - 62, r.bottom() - 23, 56, 18); }
    private Rect dialogContent() { Rect r = dialogRect(); return new Rect(r.x() + 6, r.y() + 22, r.width() - 12, Math.max(1, r.height() - 50)); }
    private List<String> dialogLines = List.of();
    private void cacheDialog() { if (modal()) dialogLines = DashboardText.wrap(dialogBody, dialogContent().width() - 6, measure); }

    private void paintDialog(Canvas g) {
        g.fill(new Rect(0, 0, width, height), 0xC0101722);
        Rect box = dialogRect(); g.fill(box, PANEL); border(g, box, PANEL_BORDER);
        g.text(fit(dialogTitle, box.width() - 12), box.x() + 6, textY(box.y() + 4), TEXT);
        Rect area = dialogContent(); int content = dialogLines.size() * lineStep;
        int offset = dialogScroll.offset(content, area.height());
        g.clip(area);
        int first = offset / lineStep, last = Math.min(dialogLines.size(), first + area.height() / lineStep + 2);
        for (int i = first; i < last; i++) g.text(dialogLines.get(i), area.x(), textY(area.y() + i * lineStep - offset), TEXT);
        scrollbar(g, area, content, offset); g.unclip();
        Rect close = dialogClose(); g.fill(close, BUTTON); border(g, close, BLUE);
        String label = fit(t("dismiss"), close.width() - 8);
        g.text(label, close.x() + Math.max(4, (close.width() - measure.applyAsInt(label)) / 2), centerTextY(close.y(), close.height()), TEXT);
    }

    public Action click(double x, double y) {
        if (modal()) { if (dialogClose().contains(x, y)) dismissDialog(); return Action.NONE; }
        if (!layout.panel().contains(x, y)) return Action.NONE;
        for (Action a : List.of(Action.TOGGLE, Action.COPY_PATH, Action.COPY_DETAILS, Action.OPEN_SOURCE)) {
            if (rect(a).contains(x, y)) {
                if (!enabled(a)) { if (a == Action.OPEN_SOURCE) status(sourceReason); return Action.NONE; }
                focus = a; return a;
            }
        }
        if (!collapsed && layout.search().contains(x, y)) { focus = Action.SEARCH; return focus; }
        if (!collapsed && layout.clear().contains(x, y)) {
            if (!enabled(Action.CLEAR)) return Action.NONE;
            focus = Action.CLEAR; return focus;
        }
        if (!collapsed && layout.list().contains(x, y)) {
            focus = Action.LIST;
            if (beginScrollbarDrag(Action.LIST, x, y)) return Action.NONE;
            int pos = (int) y - layout.list().y() + listScroll.offset(listHeight(), layout.list().height());
            int i = pos / (layout.cardHeight() + 4);
            if (i >= 0 && i < cards.size() && pos % (layout.cardHeight() + 4) < layout.cardHeight()) {
                model.select(cards.get(i).error().id()); refresh();
            }
            return focus;
        }
        if (layout.details().contains(x, y)) {
            focus = Action.DETAILS;
            if (beginScrollbarDrag(Action.DETAILS, x, y)) return Action.NONE;
            int i = ((int) y - layout.details().y() + detailState().scroll.offset(detailHeight(), layout.details().height())) / lineStep;
            if (i >= 0 && i < rows.size()) {
                if (rows.get(i).kind() == Kind.RAW) return focus = Action.RAW;
                if (rows.get(i).kind() == Kind.STACK) return focus = Action.STACK;
            }
        }
        return Action.NONE;
    }

    public void toggleFold(Action action) {
        if (model.selectedError() == null) return;
        if (action == Action.RAW) detailState().rawOpen = !detailState().rawOpen;
        if (action == Action.STACK) detailState().stackOpen = !detailState().stackOpen;
        rebuildRows();
    }

    public void scroll(double x, double y, double delta) {
        if (modal()) { dialogScroll.move(delta, dialogLines.size() * lineStep, dialogContent().height()); return; }
        if (!collapsed && layout.list().contains(x, y)) listScroll.move(delta, listHeight(), layout.list().height());
        else if (layout.details().contains(x, y)) detailState().scroll.move(delta, detailHeight(), layout.details().height());
    }

    private boolean beginScrollbarDrag(Action action, double x, double y) {
        Rect r = rect(action); int content = action == Action.LIST ? listHeight() : detailHeight();
        if (x < r.right() - 4 || content <= r.height() || r.height() <= 0) return false;
        DashboardScroll scroll = action == Action.LIST ? listScroll : detailState().scroll;
        int offset = scroll.offset(content, r.height());
        Rect thumb = scrollbarThumb(r, content, offset);
        if (y < thumb.y() || y >= thumb.bottom()) {
            double fraction = (y - r.y() - thumb.height() / 2.0) / Math.max(1, r.height() - thumb.height());
            scroll.to(Math.max(0, Math.min(1, fraction)) * (content - r.height()));
        }
        dragging = action; dragPointerStart = y; dragScrollStart = scroll.offset(content, r.height());
        return true;
    }

    public boolean drag(double x, double y) {
        if (dragging == Action.NONE || modal()) return false;
        Rect r = rect(dragging); int content = dragging == Action.LIST ? listHeight() : detailHeight();
        DashboardScroll scroll = dragging == Action.LIST ? listScroll : detailState().scroll;
        int travel = r.height() - scrollbarThumb(r, content, scroll.offset(content, r.height())).height();
        double offset = dragScrollStart + (y - dragPointerStart) * Math.max(0, content - r.height()) / Math.max(1, travel);
        scroll.to(Math.max(0, Math.min(Math.max(0, content - r.height()), offset))); return true;
    }

    public void release() { dragging = Action.NONE; }

    public void moveSelection(int step) {
        if (cards.isEmpty()) return;
        var selected = model.selectedError(); int current = -1;
        for (int i = 0; i < cards.size(); i++) if (selected != null && cards.get(i).error().id().equals(selected.id())) { current = i; break; }
        int next = Math.max(0, Math.min(cards.size() - 1, current + step));
        model.select(cards.get(next).error().id()); refresh();
        int top = next * (layout.cardHeight() + 4), offset = listScroll.offset(listHeight(), layout.list().height());
        if (top < offset) listScroll.to(top);
        else if (top + layout.cardHeight() > offset + layout.list().height()) listScroll.to(top + layout.cardHeight() - layout.list().height());
    }

    public void scrollFocused(int direction, boolean page) {
        if (modal()) { dialogScroll.move(direction * (page ? dialogContent().height() - lineStep : lineStep * 3), dialogLines.size() * lineStep, dialogContent().height()); return; }
        boolean list = focus == Action.LIST && !collapsed;
        Rect r = list ? layout.list() : layout.details();
        (list ? listScroll : detailState().scroll).move(direction * (page ? Math.max(lineStep, r.height() - lineStep) : lineStep * 3), list ? listHeight() : detailHeight(), r.height());
    }

    public void tab(boolean backwards) {
        List<Action> order = new ArrayList<>(); order.add(Action.TOGGLE);
        if (!collapsed) order.addAll(List.of(Action.SEARCH, Action.CLEAR, Action.LIST));
        order.addAll(List.of(Action.COPY_PATH, Action.COPY_DETAILS, Action.OPEN_SOURCE, Action.DETAILS));
        if (rows.stream().anyMatch(r -> r.kind() == Kind.RAW)) order.add(Action.RAW);
        if (rows.stream().anyMatch(r -> r.kind() == Kind.STACK)) order.add(Action.STACK);
        order.removeIf(action -> !enabled(action));
        int current = order.indexOf(focus);
        int next = current < 0 ? (backwards ? order.size() - 1 : 0)
                : Math.floorMod(current + (backwards ? -1 : 1), order.size());
        focus = order.get(next);
        Kind target = focus == Action.RAW ? Kind.RAW : focus == Action.STACK ? Kind.STACK : null;
        if (target != null) for (int i = 0; i < rows.size(); i++) if (rows.get(i).kind() == target) {
            detailState().scroll.to(i * lineStep); break;
        }
    }

    private String statusMessage() {
        return feedback.isEmpty() ? t(totalErrors == 0 ? "readonly_empty" : "readonly") : feedback;
    }

    public String tooltip(double x, double y) {
        if (modal()) return "";
        if (layout.footer().contains(x, y)) return statusMessage();
        if (layout.toggle().contains(x, y)) return t(collapsed ? "expand" : "collapse");
        if (!collapsed && layout.clear().contains(x, y)) return t("clear");
        if (layout.copyPath().contains(x, y)) return t("copy_path");
        if (layout.copyDetails().contains(x, y)) return t("copy_full");
        if (layout.openSource().contains(x, y)) return sourceReason.isBlank() ? t("vscode") : sourceReason;
        return "";
    }

    public String narration() {
        var e = model.selectedError();
        return modal() ? dialogTitle + ". " + dialogBody : t("title") + ". " + actionLabel(focus) + ". "
                + (focus == Action.OPEN_SOURCE ? sourceReason : "") + (e == null ? t("empty") : e.path() + ". " + e.message());
    }
}



