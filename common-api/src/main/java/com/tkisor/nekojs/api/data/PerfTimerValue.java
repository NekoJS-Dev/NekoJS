package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.List;
import java.util.Objects;

/**
 * 标签计时器：由 {@code Performance.start(label)} 创建，支持多段 {@code mark}。
 *
 * <p>不可变值类型——{@code mark(label)} 返回附加一个标记点的新实例（链式）。
 * {@code elapsedMillis()} / {@code report()} 以当前 {@link System#nanoTime()} 实时计算，
 * 无需显式 {@code end()}；{@code end()} 返回最终报告并语义上关闭计时器（仍可继续 report）。
 *
 * <p>基于 {@link System#nanoTime()} 单调时钟，亚毫秒精度。
 */
@ContractReceiver("PerfTimer")
public final class PerfTimerValue {
    private final String label;
    private final long startNano;
    private final long endNano;
    private final List<Mark> marks;
    private final boolean ended;

    public PerfTimerValue(String label, long startNano, long endNano, List<Mark> marks, boolean ended) {
        this.label = label;
        this.startNano = startNano;
        this.endNano = endNano;
        this.marks = List.copyOf(marks);
        this.ended = ended;
    }

    public static PerfTimerValue start(String label) {
        return new PerfTimerValue(
                label == null || label.isBlank() ? null : label,
                System.nanoTime(),
                0L,
                List.of(),
                false);
    }

    /** 记录一个中间标记点，返回新实例（链式）。 */
    public PerfTimerValue mark(String markLabel) {
        if (ended) {
            return this;
        }
        Mark m = new Mark(markLabel, System.nanoTime());
        var next = new java.util.ArrayList<Mark>(this.marks.size() + 1);
        next.addAll(this.marks);
        next.add(m);
        return new PerfTimerValue(this.label, this.startNano, this.endNano, next, false);
    }

    /** 标记为结束，冻结当前时刻为基准，返回新实例。仍可继续调用 report。 */
    public PerfTimerValue end() {
        return ended ? this : new PerfTimerValue(this.label, this.startNano, System.nanoTime(), this.marks, true);
    }

    /** 从开始到当前的耗时毫秒（若已 end 则到 end 调用时刻，否则到当前时刻）。 */
    public double elapsedMillis() {
        long until = ended ? endNano : System.nanoTime();
        return (until - startNano) / 1_000_000.0;
    }

    /** 结构化报告。 */
    public PerfTimerReport report() {
        double total = elapsedMillis();
        var reportMarks = new java.util.ArrayList<MarkReport>(marks.size());
        long prevNano = startNano;
        for (Mark m : marks) {
            double sincePrev = (m.nano() - prevNano) / 1_000_000.0;
            double at = (m.nano() - startNano) / 1_000_000.0;
            reportMarks.add(new MarkReport(m.label(), at, sincePrev));
            prevNano = m.nano();
        }
        return new PerfTimerReport(label, total, reportMarks);
    }

    public String label() { return label; }
    public List<Mark> marks() { return marks; }
    public boolean ended() { return ended; }

    /** 原始标记点（label + nano 绝对时间戳）。 */
    public record Mark(String label, long nano) {
        public Mark {
            Objects.requireNonNull(label, "label");
        }
    }

    /** {@link #report()} 的结构化结果。 */
    public record PerfTimerReport(String label, double total, List<MarkReport> marks) {
        public PerfTimerReport {
            marks = List.copyOf(marks);
        }
    }

    /** 单个标记点的报告项：{@code at}=自开始毫秒，{@code sincePrev}=距上一标记点毫秒。 */
    public record MarkReport(String label, double at, double sincePrev) {
        public MarkReport {
            Objects.requireNonNull(label, "label");
        }
    }
}
