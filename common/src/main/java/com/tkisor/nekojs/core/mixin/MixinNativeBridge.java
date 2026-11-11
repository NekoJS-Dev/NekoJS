package com.tkisor.nekojs.core.mixin;

/** Early-load ABI: JDK-only, nullable, and never creates the script runtime. */
public final class MixinNativeBridge {
    public static final int ABI = 1;
    private static volatile Lease current;
    private MixinNativeBridge() {}
    public interface Sink {
        boolean enabled(String plan, String id);
        Decision invoke(String plan, String id, Object self, Object[] args, Object original);
    }
    public record Decision(boolean changed, Object value) {
        public static final Decision PASS = new Decision(false, null);
    }
    public static final class Lease implements AutoCloseable {
        private final Sink sink;
        private Lease(Sink sink) { this.sink = sink; }
        @Override public void close() {
            synchronized (MixinNativeBridge.class) { if (current == this) current = null; }
        }
    }
    public static synchronized Lease attach(Sink sink) {
        if (sink == null || current != null) throw new IllegalStateException("[NEKO-4091] Native bridge already owned or sink missing");
        return current = new Lease(sink);
    }
    public static boolean enabled(String plan, String id) {
        Lease lease = current;
        try { return lease != null && lease.sink.enabled(plan, id); }
        catch (RuntimeException | LinkageError failure) { return false; }
    }
    public static Decision invoke(String plan, String id, Object self, Object[] args, Object original) {
        Lease lease = current;
        try {
            Decision decision = lease == null ? Decision.PASS : lease.sink.invoke(plan, id, self, args, original);
            return decision == null ? Decision.PASS : decision;
        } catch (RuntimeException | LinkageError failure) { return Decision.PASS; }
    }
}
