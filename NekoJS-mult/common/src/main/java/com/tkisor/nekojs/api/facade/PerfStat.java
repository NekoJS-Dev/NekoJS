package com.tkisor.nekojs.api.facade;

/**
 * {@link PerformanceFacade#bench} 返回的统计快照。所有时间为毫秒。
 *
 * @param runs   实际执行次数
 * @param total  总耗时毫秒
 * @param mean   平均单次耗时毫秒
 * @param min    最快单次毫秒
 * @param max    最慢单次毫秒
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
