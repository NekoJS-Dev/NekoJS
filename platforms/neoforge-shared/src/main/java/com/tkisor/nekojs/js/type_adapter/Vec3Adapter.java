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
 * 把脚本输入适配为 {@link Vec3}：接受 {@code Vec3}、{@code BlockPos}、{@code {x,y,z}} 对象、
 * {@code [x,y,z]} 数组，坐标保留小数。
 *
 * <pre>
 * someApi({ x: 1.5, y: 64.0, z: -2.25 })
 * someApi([1.5, 64, -2.25])
 * </pre>
 */
public class Vec3Adapter extends AbstractJSTypeAdapter<Vec3> {

    private static final Set<String> XYZ = Set.of("x", "y", "z");

    @Override
    public Class<Vec3> getTargetClass() {
        return Vec3.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                host(BlockPos.class),
                object(Slot.req("x", number()), Slot.req("y", number()), Slot.req("z", number())),
                arrayOf(number()));
    }

    @Override
    protected Vec3 defaultValue() {
        return Vec3.ZERO;
    }

    @Override
    protected Vec3 fromHostObject(Object host) {
        if (host instanceof Vec3 vec) return vec;
        if (host instanceof BlockPos pos) return new Vec3(pos.getX(), pos.getY(), pos.getZ());
        return null;
    }

    @Override
    protected boolean acceptOther(Value value) {
        return (value.hasMembers() && value.getMemberKeys().containsAll(XYZ))
                || (value.hasArrayElements() && value.getArraySize() >= 3);
    }

    @Override
    protected Vec3 fromOther(Value value) {
        if (value.hasArrayElements() && value.getArraySize() >= 3) {
            return new Vec3(
                    value.getArrayElement(0).asDouble(),
                    value.getArrayElement(1).asDouble(),
                    value.getArrayElement(2).asDouble());
        }
        return new Vec3(
                value.getMember("x").asDouble(),
                value.getMember("y").asDouble(),
                value.getMember("z").asDouble());
    }
}
