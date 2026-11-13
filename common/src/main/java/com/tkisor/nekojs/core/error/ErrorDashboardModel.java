package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.network.ErrorSummaryDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure data snapshot model for the error panel.
 *
 * <p>The model covers three concepts only: the current snapshot, the search, and selection by
 * stable id; list scrolling, expansion and rendering state belong to the UI. Malformed DTOs in
 * the input are skipped, and the string fields of valid DTOs (fullDetails in particular) are kept
 * as they are, never rewritten for display.</p>
 */
public final class ErrorDashboardModel {
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public ErrorDashboardModel() {}

    /** Replaces the full error snapshot; selection follows the same id to the new DTO and is cleared
     * when that error is filtered out or gone. */
    public void update(List<ErrorSummaryDTO> errors) {
        List<ErrorSummaryDTO> safeErrors = safeErrors(errors);
        this.snapshot = this.snapshot.withErrors(safeErrors);
    }

    /**
     * Sets the search term. null and blank both mean no filtering; matching uses
     * {@link Locale#ROOT} case rules.
     */
    public void setSearch(String search) {
        this.snapshot = this.snapshot.withSearch(search == null ? "" : search);
    }

    public String search() {
        return snapshot.search();
    }

    /**
     * Selects a currently visible error by its stable id. null clears the selection; an
     * unknown or filtered-out id leaves the current selection unchanged.
     */
    public void select(String id) {
        this.snapshot = this.snapshot.select(id);
    }

    public List<ErrorSummaryDTO> visibleErrors() {
        return snapshot.visibleErrors();
    }

    /** Returns the selected DTO, or {@code null} when no visible error is selected. */
    public ErrorSummaryDTO selectedError() {
        return snapshot.selectedError();
    }

    /** Number of errors in the full snapshot; search filtering does not affect it. */
    public int totalErrors() {
        return snapshot.errors().size();
    }

    /** Total occurrences in the full snapshot. Summed as long so many int counts cannot overflow. */
    public long totalOccurrences() {
        long total = 0L;
        for (ErrorSummaryDTO error : snapshot.errors()) {
            total += Math.max(0, error.count());
        }
        return total;
    }

    private static List<ErrorSummaryDTO> safeErrors(List<ErrorSummaryDTO> errors) {
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        List<ErrorSummaryDTO> result = new ArrayList<>(errors.size());
        for (ErrorSummaryDTO error : errors) {
            // id is the panel's stable selection key. The other strings may be empty and still render, but
            // a null would create an implicit requirement for the renderer.
            if (error != null && error.id() != null && !error.id().isBlank()
                    && error.path() != null && error.message() != null && error.fullDetails() != null) {
                result.add(error);
            }
        }
        return List.copyOf(result);
    }

    private static boolean matches(ErrorSummaryDTO error, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        return contains(error.id(), normalizedQuery)
                || contains(error.path(), normalizedQuery)
                || contains(error.message(), normalizedQuery)
                || contains(error.fullDetails(), normalizedQuery);
    }

    private static boolean contains(String value, String normalizedQuery) {
        return value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    /**
     * Immutable view state. The field is volatile and replaced as a whole, so concurrent readers
     * always see one complete snapshot generation.
     */
    private record Snapshot(
            List<ErrorSummaryDTO> errors,
            List<ErrorSummaryDTO> visibleErrors,
            String search,
            String selectedId
    ) {
        private static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), "", null);

        private Snapshot {
            Objects.requireNonNull(errors, "errors");
            Objects.requireNonNull(visibleErrors, "visibleErrors");
            Objects.requireNonNull(search, "search");
            errors = List.copyOf(errors);
            visibleErrors = List.copyOf(visibleErrors);
            search = search == null ? "" : search;
        }

        private Snapshot withErrors(List<ErrorSummaryDTO> newErrors) {
            List<ErrorSummaryDTO> newVisible = visibleFor(newErrors, search);
            String newSelectedId = containsId(newVisible, selectedId) ? selectedId : null;
            return new Snapshot(newErrors, newVisible, search, newSelectedId);
        }

        private Snapshot withSearch(String newSearch) {
            List<ErrorSummaryDTO> newVisible = visibleFor(errors, newSearch);
            String newSelectedId = containsId(newVisible, selectedId) ? selectedId : null;
            return new Snapshot(errors, newVisible, newSearch, newSelectedId);
        }

        private Snapshot select(String id) {
            if (id == null) {
                return new Snapshot(errors, visibleErrors, search, null);
            }
            return containsId(visibleErrors, id)
                    ? new Snapshot(errors, visibleErrors, search, id)
                    : this;
        }

        private ErrorSummaryDTO selectedError() {
            if (selectedId == null) {
                return null;
            }
            for (ErrorSummaryDTO error : visibleErrors) {
                if (selectedId.equals(error.id())) {
                    return error;
                }
            }
            return null;
        }

        private static List<ErrorSummaryDTO> visibleFor(List<ErrorSummaryDTO> errors, String search) {
            if (search == null || search.isBlank()) {
                return errors;
            }
            String query = search.toLowerCase(Locale.ROOT);
            List<ErrorSummaryDTO> result = new ArrayList<>();
            for (ErrorSummaryDTO error : errors) {
                if (matches(error, query)) {
                    result.add(error);
                }
            }
            return List.copyOf(result);
        }

        private static boolean containsId(List<ErrorSummaryDTO> errors, String id) {
            if (id == null) {
                return false;
            }
            for (ErrorSummaryDTO error : errors) {
                if (id.equals(error.id())) {
                    return true;
                }
            }
            return false;
        }
    }
}

