package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.PerfTimerValue;

/**
 * 用户主动调用的性能检测工具。提供高精度时间戳、函数计时、批量基准与标签计时器。
 *
 * <p>脚本侧形如 {@code Performance.now()} / {@code Performance.time(() => ...)} /
 * {@code Performance.bench(() => ..., 1000)} / {@code Performance.start('label')}。
 * 回调参数以 {@link Object} 透传（Graal {@code Value}），由实现侧执行。
 */
public interface PerformanceFacade {

    /** 单调时钟当前时间戳（毫秒，{@code double}，亚毫秒精度）。对标 Web {@code performance.now()}。 */
    double now();

    /** 执行一次 {@code fn}，返回耗时毫秒。 */
    double time(Object fn);

    /** 执行 {@code fn} {@code runs} 次，返回统计（runs/total/mean/min/max）。 */
    PerfStat bench(Object fn, int runs);

    /** 开始一个标签计时器，可选 label。返回不可变 {@link PerfTimerValue} 句柄。 */
    PerfTimerValue start(String label);
}
