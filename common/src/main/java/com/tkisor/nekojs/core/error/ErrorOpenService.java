package com.tkisor.nekojs.core.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.tkisor.nekojs.network.ErrorSummaryDTO;

/**
 * 把“点击时重新校验位置”和“异步分派”绑在一起，避免 UI 用选中时缓存的旧路径绕过检查。
 */
public final class ErrorOpenService {
    private final LocalErrorSource source;
    private final ErrorLocationOpener opener;

    public ErrorOpenService(LocalErrorSource source, ErrorLocationOpener opener) {
        this.source = Objects.requireNonNull(source, "source");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /**
     * 在调用方给的 Executor 上重新解析并分派；渲染线程应传入后台 executor。
     * 返回 future 正常完成，实现内部的异常会转成 FAILED/UNAVAILABLE 结果。
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

    Result openNow(ErrorSummaryDTO error, boolean integratedSingleplayerServer) {
        LocalErrorSource.Result location;
        try {
            location = source.resolve(error, integratedSingleplayerServer);
        } catch (RuntimeException e) {
            return Result.unavailable(LocalErrorSource.Status.IO_ERROR);
        }
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

