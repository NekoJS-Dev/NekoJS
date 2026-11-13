package com.tkisor.nekojs.api.facade;

/**
 * Snapshot of the statistics {@link PerformanceFacade#bench} returns. All times are in milliseconds.
 *
 * @param runs  number of executions actually performed
 * @param total total elapsed milliseconds
 * @param mean  mean milliseconds per execution
 * @param min   fastest single run in milliseconds
 * @param max   slowest single run in milliseconds
 */
public record PerfStat(int runs, double total, double mean, double min, double max) {
    /**
     * Creates a snapshot, deriving {@code mean} as {@code total / runs}.
     *
     * @param runs  number of executions actually performed
     * @param total total elapsed milliseconds
     * @param min   fastest single run in milliseconds
     * @param max   slowest single run in milliseconds
     * @return the snapshot; {@code mean} is {@code 0.0} when {@code runs <= 0}
     */
    public static PerfStat of(int runs, double total, double min, double max) {
        double mean = runs > 0 ? total / runs : 0.0;
        return new PerfStat(runs, total, mean, min, max);
    }
}
