package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyArray;
import graal.graalvm.polyglot.proxy.ProxyObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BlockPosAdapter smoke test：仅用三版本 API 稳定的 {@link BlockPos}/{@link Vec3}
 * 与 GraalVM polyglot {@link Value}，不启动游戏。
 */
class BlockPosAdapterTest {

    private final BlockPosAdapter adapter = new BlockPosAdapter();

    @Test
    void declaresBlockPosTargetAndShapes() {
        assertEquals(BlockPos.class, adapter.getTargetClass());
        assertFalse(adapter.inputShapes().isEmpty());
    }

    @Test
    void passesThroughHostBlockPos() {
        Value value = Value.asValue(new BlockPos(10, 20, 30));

        assertTrue(adapter.test(value));
        assertEquals(new BlockPos(10, 20, 30), adapter.apply(value));
    }

    @Test
    void convertsHostVec3ByFlooringCoordinates() {
        Value value = Value.asValue(new Vec3(1.5, 64.0, -2.25));

        assertTrue(adapter.test(value));
        assertEquals(new BlockPos(1, 64, -3), adapter.apply(value));
    }

    @Test
    void convertsObjectShape() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyObject.fromMap(Map.<String, Object>of("x", 1, "y", 64, "z", -2)));

            assertTrue(adapter.test(value));
            assertEquals(new BlockPos(1, 64, -2), adapter.apply(value));
        }
    }

    @Test
    void convertsArrayShape() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyArray.fromArray(1, 64, -2));

            assertTrue(adapter.test(value));
            assertEquals(new BlockPos(1, 64, -2), adapter.apply(value));
        }
    }

    @Test
    void nullInputFallsBackToZero() {
        assertTrue(adapter.test(null));
        assertEquals(BlockPos.ZERO, adapter.apply(null));
    }

    @Test
    void rejectsUnrecognizedHostObject() {
        Value value = Value.asValue(new Object());

        assertFalse(adapter.test(value));
        assertThrows(ValueConversionException.class, () -> adapter.apply(value));
    }
}
