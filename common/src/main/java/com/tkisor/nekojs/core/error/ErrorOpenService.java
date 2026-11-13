package com.tkisor.nekojs.core.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.tkisor.nekojs.network.ErrorSummaryDTO;

/**
 * Binds "re-check the location at click time" to asynchronous dispatch, so the UI cannot open a
 * stale path cached when the error was selected. Both entry points share one resolve-and-dispatch
 * path: the error DTO (wire projection) and the frozen diagnostic record, handed in as a
 * {@link DiagnosticOpenAction}.
 */
public final class ErrorOpenService {
    private final LocalErrorSource source;
    private final ErrorLocationOpener opener;

    public ErrorOpenService(LocalErrorSource source, ErrorLocationOpener opener) {
        this.source = Objects.requireNonNull(source, "source");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /**
     * Re-resolves and dispatches on the caller's Executor; a rendering thread must pass a
     * background executor. The returned future completes normally: internal failures turn into
     * FAILED/UNAVAILABLE results.
     */
    public CompletableFuture<Result> openAsync(
            ErrorSummaryDTO error,
            boolean integratedSingleplayerServer,
            Executor executor
    ) {
        Objects.requireNonNull(executor, "executor");
        try {
            return CompletableFuture.supplyAsync(
                    () -> openNow(error, integratedSingleplayerServer),
                    executor
            );
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Result.failed(e.getClass().getSimpleName()));
        }
    }

    /**
     * Resolves and dispatches from the frozen diagnostic record's {@link DiagnosticOpenAction}
     * instead of re-deriving the location from another source. A {@code null} action (no locatable
     * authored source) reports {@code LOCATION_UNAVAILABLE}/{@code NO_ERROR} without touching the
     * opener.
     */
    public CompletableFuture<Result> openAsync(
            DiagnosticOpenAction action,
            boolean integratedSingleplayerServer,
            Executor executor
    ) {
        Objects.requireNonNull(executor, "executor");
        if (action == null) {
            return CompletableFuture.completedFuture(
                    Result.unavailable(LocalErrorSource.Status.NO_ERROR));
        }
        try {
            return CompletableFuture.supplyAsync(
                    () -> openNow(action, integratedSingleplayerServer),
                    executor
            );
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Result.failed(e.getClass().getSimpleName()));
        }
    }

    Result openNow(ErrorSummaryDTO error, boolean integratedSingleplayerServer) {
        LocalErrorSource.Result location;
        try {
            location = source.resolve(error, integratedSingleplayerServer);
        } catch (RuntimeException e) {
            return Result.unavailable(LocalErrorSource.Status.IO_ERROR);
        }
        return dispatch(location);
    }

    Result openNow(DiagnosticOpenAction action, boolean integratedSingleplayerServer) {
        LocalErrorSource.Result location;
        try {
            location = source.resolve(action, integratedSingleplayerServer);
        } catch (RuntimeException e) {
            return Result.unavailable(LocalErrorSource.Status.IO_ERROR);
        }
        return dispatch(location);
    }

    /** Shared tail of both entry points: report an unavailable location or dispatch the opener. */
    private Result dispatch(LocalErrorSource.Result location) {
        if (!location.available()) {
            return Result.unavailable(location.status());
        }
        try {
            ErrorLocationOpener.OpenResult opened = opener.open(location.target());
            if (opened == null) {
                return Result.failed("opener returned no result");
            }
            boolean lineRequested = opened.lineRequested() && location.target().gotoLine();
            return new Result(
                    opened.accepted() ? Outcome.DISPATCH_ACCEPTED : Outcome.DISPATCH_FAILED,
                    location.status(),
                    lineRequested,
                    opened.failure()
            );
        } catch (RuntimeException | LinkageError e) {
            return Result.failed(e.getClass().getSimpleName());
        }
    }

    public enum Outcome { LOCATION_UNAVAILABLE, DISPATCH_ACCEPTED, DISPATCH_FAILED }

    public record Result(
            Outcome outcome,
            LocalErrorSource.Status locationStatus,
            boolean lineRequested,
            String detail
    ) {
        private static Result unavailable(LocalErrorSource.Status status) {
            return new Result(Outcome.LOCATION_UNAVAILABLE, status, false, null);
        }

        private static Result failed(String detail) {
            return new Result(Outcome.DISPATCH_FAILED, LocalErrorSource.Status.LOCAL_FILE, false, detail);
        }

        public boolean accepted() {
            return outcome == Outcome.DISPATCH_ACCEPTED;
        }
    }
}

