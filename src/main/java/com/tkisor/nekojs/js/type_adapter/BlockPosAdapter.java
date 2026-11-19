package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * Adapts script input to {@link BlockPos}: accepts {@code BlockPos}, {@code Vec3}, an
 * {@code {x,y,z}} object or an {@code [x,y,z]} array. Coordinates are rounded down to the
 * block that contains them ({@link BlockPos#containing}).
 *
 * <pre>
 * someApi(BlockPos.of(...))            // self
 * someApi({ x: 1, y: 64, z: -2 })      // object
 * someApi([1, 64, -2])                 // array
 * someApi(new Vec3(1.5, 64, -2))       // Vec3 host
 * </pre>
 */
public class BlockPosAdapter extends AbstractJSTypeAdapter<BlockPos> {

    private static final Set<String> XYZ = Set.of("x", "y", "z");

    @Override
    public Class<BlockPos> getTargetClass() {
        return BlockPos.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                host(Vec3.class),
                object(Slot.req("x", number()), Slot.req("y", number()), Slot.req("z", number())),
                arrayOf(number()));
    }

    @Override
    protected BlockPos defaultValue() {
        return BlockPos.ZERO;
    }

    @Override
    protected BlockPos fromHostObject(Object host) {
        if (host instanceof BlockPos pos) return pos;
        if (host instanceof Vec3 vec) return BlockPos.containing(vec.x, vec.y, vec.z);
        return null;
    }

    @Override
    protected boolean acceptOther(Value value) {
        if (value.hasMembers() && value.getMemberKeys().containsAll(XYZ)) {
            return value.getMember("x").isNumber()
                    && value.getMember("y").isNumber()
                    && value.getMember("z").isNumber();
        }
        if (value.hasArrayElements() && value.getArraySize() >= 3) {
            return value.getArrayElement(0).isNumber()
                    && value.getArrayElement(1).isNumber()
                    && value.getArrayElement(2).isNumber();
        }
        return false;
    }

    @Override
    protected BlockPos fromOther(Value value) {
        if (value.hasArrayElements() && value.getArraySize() >= 3) {
            return BlockPos.containing(
                    value.getArrayElement(0).asDouble(),
                    value.getArrayElement(1).asDouble(),
                    value.getArrayElement(2).asDouble());
        }
        return BlockPos.containing(
                value.getMember("x").asDouble(),
                value.getMember("y").asDouble(),
                value.getMember("z").asDouble());
    }
}
