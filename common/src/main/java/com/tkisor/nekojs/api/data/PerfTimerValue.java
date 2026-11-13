package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.List;
import java.util.Objects;

/**
 * A labelled timer created by {@code Performance.start(label)}. It records any number of
 * {@code mark} points.
 *
 * <p>This is an immutable value type: {@code mark(label)} returns a new instance with one more
 * mark point, so calls can be chained. {@code elapsedMillis()} and {@code report()} read the
 * current {@link System#nanoTime()} on every call, so no explicit {@code end()} is needed.
 * {@code end()} freezes the end time and semantically closes the timer; {@code report()} still
 * works afterwards.
 *
 * <p>Times come from the monotonic {@link System#nanoTime()} clock and have sub-millisecond
 * precision.
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

    /** Records one intermediate mark point and returns a new instance, so calls can be chained. */
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

    /** Marks the timer as ended, freezes the current instant as the end time and returns a new
     * instance. {@code report()} can still be called afterwards. */
    public PerfTimerValue end() {
        return ended ? this : new PerfTimerValue(this.label, this.startNano, System.nanoTime(), this.marks, true);
    }

    /** Milliseconds from the start to now, or to the {@code end()} call if the timer has ended. */
    public double elapsedMillis() {
        long until = ended ? endNano : System.nanoTime();
        return (until - startNano) / 1_000_000.0;
    }

    /** Builds the structured report. */
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

    /** A raw mark point: its label plus the absolute {@code nano} timestamp. */
    public record Mark(String label, long nano) {
        public Mark {
            Objects.requireNonNull(label, "label");
        }
    }

    /** The structured result returned by {@link #report()}. */
    public record PerfTimerReport(String label, double total, List<MarkReport> marks) {
        public PerfTimerReport {
            marks = List.copyOf(marks);
        }
    }

    /** One mark in the report: {@code at} is milliseconds since the start, {@code sincePrev} is
     * milliseconds since the previous mark. */
    public record MarkReport(String label, double at, double sincePrev) {
        public MarkReport {
            Objects.requireNonNull(label, "label");
        }
    }
}
