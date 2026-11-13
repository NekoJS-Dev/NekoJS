package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.PerfTimerValue;

/**
 * Performance measurement tools a script calls directly. It provides a high-resolution timestamp,
 * function timing, batch benchmarks and labelled timers.
 *
 * <p>Scripts use it as {@code Performance.now()} / {@code Performance.time(() => ...)} /
 * {@code Performance.bench(() => ..., 1000)} / {@code Performance.start('label')}. Callback
 * arguments are passed through as {@link Object} (a Graal {@code Value}) and run by the implementation.
 */
public interface PerformanceFacade {

    /**
     * Current timestamp of the monotonic clock in milliseconds ({@code double}, sub-millisecond
     * precision). Matches Web {@code performance.now()}.
     */
    double now();

    /** Runs {@code fn} once and returns the elapsed milliseconds. */
    double time(Object fn);

    /**
     * Runs {@code fn} {@code runs} times and returns the statistics (runs/total/mean/min/max).
     *
     * <p>Negative {@code runs} is clamped to {@code 0}.
     */
    PerfStat bench(Object fn, int runs);

    /** Starts a labelled timer; the label is optional. Returns an immutable {@link PerfTimerValue} handle. */
    PerfTimerValue start(String label);
}
