package dev.latvian.mods.kubejs.util;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.time.Duration;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.List;

public record TickDuration(long ticks) implements TemporalAmount {
    public static final TickDuration ZERO = new TickDuration(0L);
    public static final Codec<TickDuration> CODEC = Codec.LONG.xmap(TickDuration::of, TickDuration::ticks);
    public static final TypeInfo TYPE_INFO = TypeInfo.of(TickDuration.class);
    public static TickDuration of(long ticks) { return ticks == 0L ? ZERO : new TickDuration(ticks); }
    public static TickDuration wrap(Context context, Object value) {
        value = Wrapper.unwrapped(value);
        if (value == null) return ZERO;
        if (value instanceof TickDuration duration) return duration;
        if (value instanceof Number number) return of(number.longValue());
        if (value instanceof JsonPrimitive json && json.isNumber()) return of(json.getAsLong());
        if (value instanceof Duration duration) return of(duration.toMillis() / 50L);
        if (value instanceof CharSequence text) {
            try {
                return parse(text.toString());
            } catch (CommandSyntaxException | IllegalArgumentException cause) {
                throw new IllegalArgumentException("[NEKO-4284] Invalid tick duration: " + text, cause);
            }
        }
        throw new IllegalArgumentException("[NEKO-4284] Unsupported tick duration value: " + value);
    }

    private static TickDuration parse(String text) throws CommandSyntaxException {
        var reader = new StringReader(text);
        reader.skipWhitespace();
        if (!reader.canRead()) throw new IllegalArgumentException("Expected a time amount and unit");

        double totalNanos = 0D;
        double ticks = Double.NaN;
        while (reader.canRead()) {
            double amount = reader.readDouble();
            if (!Double.isFinite(amount)) throw new IllegalArgumentException("Expected a finite time amount");
            reader.skipWhitespace();
            String unit = readUnit(reader);
            switch (unit) {
                case "t" -> ticks = (Double.isNaN(ticks) ? 0D : ticks) + amount;
                case "ns" -> totalNanos += amount;
                case "ms" -> totalNanos += amount * 1_000_000D;
                case "s" -> totalNanos += amount * 1_000_000_000D;
                case "m" -> totalNanos += amount * 60D * 1_000_000_000D;
                case "h" -> totalNanos += amount * 3600D * 1_000_000_000D;
                case "d" -> totalNanos += amount * 86400D * 1_000_000_000D;
                case "w" -> totalNanos += amount * 604800D * 1_000_000_000D;
                case "M" -> totalNanos += amount * (31556952D / 12D) * 1_000_000_000D;
                case "y" -> totalNanos += amount * 31556952D * 1_000_000_000D;
                default -> throw new IllegalArgumentException("Expected a time unit");
            }
            reader.skipWhitespace();
        }

        // TimeJS truncates mixed tick input once; duration-only input first becomes nanoseconds.
        if (!Double.isNaN(ticks)) return of((long) (ticks + totalNanos / 50_000_000D));
        return of(Duration.ofNanos((long) totalNanos).toMillis() / 50L);
    }

    private static String readUnit(StringReader reader) {
        if (!reader.canRead()) throw new IllegalArgumentException("Expected a time unit");
        if (reader.canRead(2) && (reader.peek() == 'm' || reader.peek() == 'n') && reader.peek(1) == 's') {
            char prefix = reader.read();
            reader.skip();
            return prefix + "s";
        }
        return String.valueOf(reader.read());
    }
    @Override public long get(TemporalUnit unit) { return unit == TickTemporalUnit.INSTANCE ? ticks : 0L; }
    @Override public List<TemporalUnit> getUnits() { return List.of(TickTemporalUnit.INSTANCE); }
    @Override public Temporal addTo(Temporal temporal) { return temporal.plus(ticks, TickTemporalUnit.INSTANCE); }
    @Override public Temporal subtractFrom(Temporal temporal) { return temporal.minus(ticks, TickTemporalUnit.INSTANCE); }
    public int intTicks() { return Math.clamp(ticks, Integer.MIN_VALUE, Integer.MAX_VALUE); }
    @Override public String toString() { return ticks + " ticks"; }
}
