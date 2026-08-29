package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.PerfTimerValue;
import com.tkisor.nekojs.api.facade.PerfStat;
import com.tkisor.nekojs.api.facade.PerformanceFacade;
import graal.graalvm.polyglot.Value;

import java.util.Objects;

/**
 * {@link PerformanceFacade} 默认实现。基于 {@link System#nanoTime()} 单调时钟，
 * 亚毫秒精度；回调通过 Graal {@link Value} 执行。
 */
public final class DefaultPerformanceFacade implements PerformanceFacade {

    @Override
    public double now() {
        return System.nanoTime() / 1_000_000.0;
    }

    @Override
    public double time(Object fn) {
        Value callback = asExecutable(fn);
        long start = System.nanoTime();
        callback.executeVoid();
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    @Override
    public PerfStat bench(Object fn, int runs) {
        Value callback = asExecutable(fn);
        int actualRuns = Math.max(runs, 0);
        if (actualRuns == 0) {
            return PerfStat.of(0, 0.0, 0.0, 0.0);
        }
        // warmup：执行一次不计入统计，避免冷启动偏置
        callback.executeVoid();
        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        double total = 0.0;
        for (int i = 0; i < actualRuns; i++) {
            long start = System.nanoTime();
            callback.executeVoid();
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            total += ms;
            if (ms < min) min = ms;
            if (ms > max) max = ms;
        }
        return PerfStat.of(actualRuns, total, min, max);
    }

    @Override
    public PerfTimerValue start(String label) {
        return PerfTimerValue.start(label);
    }

    private static Value asExecutable(Object fn) {
        Objects.requireNonNull(fn, "fn");
        if (!(fn instanceof Value value) || !value.canExecute()) {
            throw new IllegalArgumentException("expected an executable function");
        }
        return value;
    }
}
