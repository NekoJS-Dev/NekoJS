package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.network.ErrorSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ticket 27 AC1: the GUI does not cache mutable shared state — {@link ErrorDashboardModel}
 * only holds an immutable copy of the caller's snapshot; later mutations of the caller's list
 * never change the model, and the exposed projection cannot be rewritten from outside.
 */
class Ticket27ErrorDashboardSnapshotTest {

    private static ErrorSummaryDTO dto(String id) {
        return new ErrorSummaryDTO(id, id + ".js", 1, 1, "message", "details");
    }

    @Test
    void modelDoesNotRetainTheCallerSuppliedMutableList() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        List<ErrorSummaryDTO> callerList = new ArrayList<>();
        callerList.add(dto("id0"));
        model.update(callerList);

        callerList.clear();
        callerList.add(dto("other"));

        assertEquals(1, model.totalErrors(), "the snapshot taken at update time is kept");
        assertEquals("id0", model.visibleErrors().getFirst().id());
    }

    @Test
    void exposedProjectionIsImmutableAndIndependentOfLaterUpdates() {
        ErrorDashboardModel model = new ErrorDashboardModel();
        model.update(List.of(dto("id0")));

        List<ErrorSummaryDTO> visible = model.visibleErrors();
        assertThrows(UnsupportedOperationException.class, () -> visible.add(dto("id1")),
                "the projection handed to the presentation layer must not be mutable");

        model.update(List.of());
        assertEquals(1, visible.size(), "an earlier snapshot view stays intact after a new update");
        assertEquals(0, model.totalErrors());
    }
}
