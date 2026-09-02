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
 * Vec3Adapter smoke test：坐标保留小数，与 BlockPosAdapter 的取整语义区分验证。
 */
class Vec3AdapterTest {

    private final Vec3Adapter adapter = new Vec3Adapter();

    @Test
    void declaresVec3TargetAndShapes() {
        assertEquals(Vec3.class, adapter.getTargetClass());
        assertFalse(adapter.inputShapes().isEmpty());
    }

    @Test
    void passesThroughHostVec3() {
        Value value = Value.asValue(new Vec3(1.5, 64.0, -2.25));

        assertTrue(adapter.test(value));
        assertEquals(new Vec3(1.5, 64.0, -2.25), adapter.apply(value));
    }

    @Test
    void convertsHostBlockPos() {
        Value value = Value.asValue(new BlockPos(10, 20, 30));

        assertTrue(adapter.test(value));
        assertEquals(new Vec3(10.0, 20.0, 30.0), adapter.apply(value));
    }

    @Test
    void convertsObjectShapePreservingDecimals() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyObject.fromMap(Map.<String, Object>of("x", 1.5, "y", 64.0, "z", -2.25)));

            assertTrue(adapter.test(value));
            assertEquals(new Vec3(1.5, 64.0, -2.25), adapter.apply(value));
        }
    }

    @Test
    void convertsArrayShapePreservingDecimals() {
        try (Context context = Context.newBuilder().build()) {
            Value value = context.asValue(ProxyArray.fromArray(1.5, 64.0, -2.25));

            assertTrue(adapter.test(value));
            assertEquals(new Vec3(1.5, 64.0, -2.25), adapter.apply(value));
        }
    }

    @Test
    void nullInputFallsBackToZero() {
        assertTrue(adapter.test(null));
        assertEquals(Vec3.ZERO, adapter.apply(null));
    }

    @Test
    void rejectsUnrecognizedHostObject() {
        Value value = Value.asValue(new Object());

        assertFalse(adapter.test(value));
        assertThrows(ValueConversionException.class, () -> adapter.apply(value));
    }
}
