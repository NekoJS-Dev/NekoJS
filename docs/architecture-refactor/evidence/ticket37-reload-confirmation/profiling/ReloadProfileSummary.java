import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ReloadProfileSummary {
    private record Sample(Instant time, String nearest, List<String> frames) {}

    private static List<String> frames(RecordedEvent event) {
        if (event.getStackTrace() == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (RecordedFrame frame : event.getStackTrace().getFrames()) {
            if (frame.getMethod() != null) {
                result.add(frame.getMethod().getType().getName() + "." + frame.getMethod().getName()
                        + ":" + frame.getLineNumber());
            }
        }
        return result;
    }

    private static void counts(PrintWriter out, String label, List<Sample> samples) {
        Map<String, Integer> nearest = new LinkedHashMap<>();
        Map<String, Integer> inclusive = new LinkedHashMap<>();
        for (Sample sample : samples) {
            nearest.merge(sample.nearest(), 1, Integer::sum);
            for (String frame : new LinkedHashSet<>(sample.frames())) {
                if (frame.startsWith("com.tkisor.nekojs.")) {
                    inclusive.merge(frame, 1, Integer::sum);
                }
            }
        }
        out.println(label + "_SAMPLES=" + samples.size());
        out.println(label + "_NEAREST_NEKO_FRAMES");
        nearest.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(18).forEach(entry -> out.println(entry.getValue() + " " + entry.getKey()));
        out.println(label + "_INCLUSIVE_NEKO_FRAMES");
        inclusive.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(25).forEach(entry -> out.println(entry.getValue() + " " + entry.getKey()));
    }

    public static void main(String[] arguments) throws Exception {
        List<Sample> reloadSamples = new ArrayList<>();
        Map<String, Integer> serverEventCounts = new LinkedHashMap<>();
        long serverCpuSamples = 0;
        long reloadParkNanos = 0;
        try (RecordingFile recording = new RecordingFile(Path.of(arguments[0]));
             PrintWriter out = new PrintWriter(arguments[1], StandardCharsets.UTF_8)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String eventType = event.getEventType().getName();
                boolean sampled = eventType.equals("jdk.ExecutionSample") || eventType.equals("jdk.NativeMethodSample");
                RecordedThread thread = event.hasField(sampled ? "sampledThread" : "eventThread")
                        ? event.getThread(sampled ? "sampledThread" : "eventThread") : null;
                if (thread == null || !"Server thread".equals(thread.getJavaName())) {
                    continue;
                }
                serverEventCounts.merge(eventType, 1, Integer::sum);
                List<String> stack = frames(event);
                boolean reload = stack.stream().anyMatch(frame -> frame.contains("ScriptManager.")
                        && (frame.contains("reload") || frame.contains("prepareCandidate")));
                if (sampled) {
                    serverCpuSamples++;
                    if (reload) {
                        String nearest = stack.stream().filter(frame -> frame.startsWith("com.tkisor.nekojs."))
                                .findFirst().orElse("none");
                        reloadSamples.add(new Sample(event.getStartTime(), nearest, stack));
                    }
                } else if (reload && eventType.equals("jdk.ThreadPark")) {
                    reloadParkNanos += event.getDuration().toNanos();
                }
            }
            reloadSamples.sort(Comparator.comparing(Sample::time));
            out.println("DIAGNOSTIC_ONLY=true");
            out.println("SAMPLE_COUNTS_NOT_MILLISECONDS=true");
            out.println("SERVER_CPU_NATIVE_SAMPLES=" + serverCpuSamples);
            out.println("SERVER_EVENT_COUNTS=" + serverEventCounts);
            out.println("RELOAD_CHAIN_THREAD_PARK_NANOS=" + reloadParkNanos);
            counts(out, "ALL_RELOAD", reloadSamples);
            List<List<Sample>> clusters = new ArrayList<>();
            Instant previous = null;
            for (Sample sample : reloadSamples) {
                if (previous == null || Duration.between(previous, sample.time()).toMillis() > 1000) {
                    clusters.add(new ArrayList<>());
                }
                clusters.getLast().add(sample);
                previous = sample.time();
            }
            out.println("CLUSTERS_BY_ONE_SECOND_GAP=" + clusters.size());
            for (int index = 0; index < clusters.size(); index++) {
                List<Sample> cluster = clusters.get(index);
                out.println("CLUSTER_" + index + "_UTC=" + cluster.getFirst().time() + ".." + cluster.getLast().time());
                counts(out, "CLUSTER_" + index, cluster);
                for (int example = 0; example < Math.min(3, cluster.size()); example++) {
                    Sample sample = cluster.get(example * (cluster.size() - 1) / Math.max(1, Math.min(3, cluster.size()) - 1));
                    out.println("EXAMPLE_UTC=" + sample.time());
                    sample.frames().stream().limit(30).forEach(frame -> out.println("  " + frame));
                }
            }
            out.println("LIMIT=Clusters require matching retained sampler timestamps; sampling omits unsampled work and is not exclusive elapsed time.");
        }
    }
}
