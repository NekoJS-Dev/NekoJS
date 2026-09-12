package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.network.ErrorSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorDashboardModelTest {
    @Test
    void updatePreservesSelectionByStableIdAndReturnsUpdatedDto() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        model.update(List.of(dto("same-id", "server_scripts/a.js", 12, "first")));

        model.select("same-id");
        ErrorSummaryDTO first = model.selectedError();
        assertEquals("first", first.message());

        model.update(List.of(dto("other", "server_scripts/b.js", 1, "other"), dto("same-id", "server_scripts/a.js", 13, "second")));
        ErrorSummaryDTO second = model.selectedError();

        assertEquals("same-id", second.id());
        assertEquals("second", second.message());
        assertNotSame(first, second);
    }

    @Test
    void emptyOrNullSnapshotsClearSelection() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        model.update(List.of(dto("id", "path", -1, "message")));
        model.select("id");
        assertNotNull(model.selectedError());

        model.update(List.of());
        assertTrue(model.visibleErrors().isEmpty());
        assertNull(model.selectedError());
        assertEquals(0, model.totalErrors());
        assertEquals(0L, model.totalOccurrences());

        model.update(List.of(dto("id", "path", -1, "message")));
        model.select("id");
        model.update(null);
        assertNull(model.selectedError());
        assertEquals(0, model.totalErrors());
    }

    @Test
    void searchMatchesAllDiagnosticFieldsUsingRootLocale() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        model.update(List.of(
                dto("ID-1", "server_scripts/TURKISH.js", -1, "message one"),
                dto("ID-2", "other.js", -1, "needle MESSAGE"),
                dto("ID-3", "other.js", -1, "plain")
        ));

        model.setSearch("id-1");
        assertEquals(List.of("ID-1"), ids(model));

        model.setSearch("turkish");
        assertEquals(List.of("ID-1"), ids(model));

        model.setSearch("message");
        assertEquals(List.of("ID-1", "ID-2"), ids(model));

        model.setSearch("ID-3");
        assertEquals(List.of("ID-3"), ids(model));

        model.setSearch("no-match");
        assertTrue(model.visibleErrors().isEmpty());
        assertNull(model.selectedError());
        assertEquals(3, model.totalErrors(), "totals describe the full snapshot, not the filtered view");
        assertEquals("", new ErrorDashboardModel().search());
    }

    @Test
    void changingSearchCannotLeaveAnInvisibleSelection() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        model.update(List.of(
                dto("alpha", "server_scripts/alpha.js", -1, "alpha"),
                dto("beta", "server_scripts/beta.js", -1, "beta")
        ));
        model.select("alpha");

        model.setSearch("beta");
        assertNull(model.selectedError(), "old selection is filtered out and must not survive");

        // Invisible/unknown IDs do not invent or restore a selection.
        model.select("beta");
        model.select("alpha");
        model.select("does-not-exist");
        assertEquals("beta", model.selectedError().id());
    }

    @Test
    void updateCannotPreserveASelectionThatNoLongerMatchesCurrentSearch() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        model.update(List.of(dto("alpha", "path.js", -1, "needle")));
        model.select("alpha");
        model.setSearch("needle");

        model.update(List.of(dto("alpha", "path.js", -1, "changed")));

        assertTrue(model.visibleErrors().isEmpty());
        assertNull(model.selectedError());
    }

    @Test
    void selectNullClearsAndVisibleListIsADefensiveSnapshot() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        model.update(List.of(dto("id", "path", -1, "message")));
        model.select("id");

        model.select(null);
        assertNull(model.selectedError());

        List<ErrorSummaryDTO> visible = model.visibleErrors();
        assertEquals(1, visible.size());
        assertThrows(UnsupportedOperationException.class, () -> visible.add(dto("another", "p", -1, "m")));
    }

    @Test
    void malformedRowsAreSkippedWithoutRewritingValidFullDetails() {
        String originalDetails = "raw\r\ndetails\tstay exact: 100%";
        ErrorSummaryDTO valid = new ErrorSummaryDTO("valid", "path", -1, 1, "message", originalDetails);
        ErrorDashboardModel model = new ErrorDashboardModel();
        List<ErrorSummaryDTO> malformed = new ArrayList<>();
        malformed.add(valid);
        malformed.add(null);
        malformed.addAll(java.util.Arrays.asList(
                new ErrorSummaryDTO(null, "p", -1, 1, "m", "d"),
                new ErrorSummaryDTO(" ", "p", -1, 1, "m", "d"),
                new ErrorSummaryDTO("null-path", null, -1, 1, "m", "d"),
                new ErrorSummaryDTO("null-message", "p", -1, 1, null, "d"),
                new ErrorSummaryDTO("null-details", "p", -1, 1, "m", null)
        ));
        model.update(malformed);
        assertEquals(List.of(valid), model.visibleErrors());
        model.select(valid.id());
        assertSame(valid, model.selectedError());
        assertEquals(originalDetails, model.selectedError().fullDetails());
        assertEquals(1, model.totalErrors());
        assertEquals(1L, model.totalOccurrences());
    }

    @Test
    void occurrenceTotalAccumulatesAsLong() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        model.update(List.of(
                dto("a", "a", -1, Integer.MAX_VALUE),
                dto("b", "b", -1, Integer.MAX_VALUE),
                dto("c", "c", -1, Integer.MAX_VALUE),
                dto("negative", "n", -1, -7)
        ));

        assertEquals(3L * Integer.MAX_VALUE, model.totalOccurrences());
        assertEquals(4, model.totalErrors());
    }

    private static List<String> ids(ErrorDashboardModel model) {
        return model.visibleErrors().stream().map(ErrorSummaryDTO::id).toList();
    }

    private static ErrorSummaryDTO dto(String id, String path, int line, int count) {
        return dto(id, path, line, count, "message " + id, "details " + id);
    }

    private static ErrorSummaryDTO dto(String id, String path, int line, String message) {
        return dto(id, path, line, 1, message, "details " + id);
    }

    private static ErrorSummaryDTO dto(String id, String path, int line, int count, String message) {
        return dto(id, path, line, count, message, "details " + id);
    }

    private static ErrorSummaryDTO dto(String id, String path, int line, int count, String message, String details) {
        return new ErrorSummaryDTO(id, path, line, count, message, details);
    }
}






