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
 * 把脚本输入适配为 {@link BlockPos}：接受 {@code BlockPos}、{@code Vec3}、{@code {x,y,z}} 对象、
 * {@code [x,y,z]} 数组。坐标取整（{@link BlockPos#containing}）。
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
        return (value.hasMembers() && value.getMemberKeys().containsAll(XYZ))
                || (value.hasArrayElements() && value.getArraySize() >= 3);
    }

    @Override
    protected BlockPos fromOther(Value value) {
        if (value.hasArrayElements() && value.getArraySize() >= 3) {
            return new BlockPos(
                    value.getArrayElement(0).asInt(),
                    value.getArrayElement(1).asInt(),
                    value.getArrayElement(2).asInt());
        }
        return new BlockPos(
                value.getMember("x").asInt(),
                value.getMember("y").asInt(),
                value.getMember("z").asInt());
    }
}
