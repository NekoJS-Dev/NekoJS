package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Value;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.*;
import java.util.regex.Pattern;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 FluidIngredient 适配器（自包含）。
 * <b>注意：1.12.2 无 {@code FluidIngredient} 类</b>，本适配器返回 {@link FluidStack} 列表作为
 * 匹配结果，实际使用时需自行处理。
 *
 * <p>支持字符串前缀：fluid:id / #tag / @mod / * / /regex/
 * 以及对象形式：{ fluid, tag, mod, regex, wildcard, any }。</p>
 *
 * <p>1.12.2 适配：使用 {@link FluidRegistry} 进行流体查找。</p>
 */
@SuppressWarnings("unchecked")
public final class FluidIngredientAdapter implements JSTypeAdapter<List<FluidStack>> {

    @Override
    public Class<List<FluidStack>> getTargetClass() {
        return (Class) List.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                string(),
                host(FluidStack.class),
                host(Fluid.class),
                host(NekoId.class),
                object(
                        Slot.opt("fluid", string()),
                        Slot.opt("tag", string()),
                        Slot.opt("mod", string()),
                        Slot.opt("regex", string()),
                        Slot.opt("wildcard", bool()),
                        Slot.opt("any", arrayOf(self()))));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("fluid:id | #tag | @mod | * | /regex/ | { fluid?|tag?|mod?|regex?|wildcard?|any? }");
    }

    @Override
    public boolean test(Value value) {
        if (value.isString() || value.hasArrayElements() || value.hasMembers()) return true;
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof FluidStack || obj instanceof Fluid || obj instanceof NekoId;
        }
        return false;
    }

    @Override
    public List<FluidStack> apply(Value value) {
        try {
            return fromValue(value);
        } catch (ValueConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ValueConversionException(List.class, "fluid / fluid id / fluid ingredient object", value,
                e.getMessage(), e);
        }
    }

    public static List<FluidStack> fromValue(Value value) {
        if (value == null || value.isNull()) return Collections.emptyList();
        if (value.isString()) return fromString(value.asString());
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof FluidStack stack)
                return Collections.singletonList(stack.copy());
            if (obj instanceof Fluid fluid)
                return Collections.singletonList(new FluidStack(fluid, 1000));
            if (obj instanceof NekoId id)
                return fromString(id.toString());
        }
        if (value.hasArrayElements()) {
            List<FluidStack> result = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                result.addAll(fromValue(value.getArrayElement(i)));
            }
            return result;
        }
        if (value.hasMembers()) return fromObject(value);
        throw new ValueConversionException(List.class,
            "fluid / fluid id / fluid ingredient object", value, "unsupported fluid ingredient value");
    }

    private static List<FluidStack> fromString(String raw) {
        if (raw == null || raw.trim().isEmpty())
            throw new ValueConversionException(List.class, "non-blank string", raw, "fluid ingredient id cannot be empty");
        String s = raw.trim();
        char c = s.charAt(0);
        if (c == '*') return wildcard();
        if (c == '@') return modFluids(s.substring(1));
        if (c == '/') {
            String body = (s.length() > 2 && s.charAt(s.length() - 1) == '/')
                ? s.substring(1, s.length() - 1) : s.substring(1);
            return regexFluids(body);
        }
        // #tag 或 fluid:id
        if (s.startsWith("#")) {
            // 1.12.2 无流体 tag 系统，尝试作为流体名查找
            String name = s.substring(1);
            Fluid fluid = FluidRegistry.getFluid(name);
            if (fluid == null) {
                throw new ValueConversionException(List.class, "registered fluid id or tag", s,
                    "fluid not found: " + s);
            }
            return Collections.singletonList(new FluidStack(fluid, 1000));
        }
        Fluid fluid = FluidRegistry.getFluid(s);
        if (fluid == null) {
            throw new ValueConversionException(List.class, "registered fluid id", s,
                "fluid not found: " + s);
        }
        return Collections.singletonList(new FluidStack(fluid, 1000));
    }

    private static List<FluidStack> wildcard() {
        List<FluidStack> result = new ArrayList<>();
        for (String name : FluidRegistry.getRegisteredFluids().keySet()) {
            Fluid fluid = FluidRegistry.getFluid(name);
            if (fluid != null) {
                result.add(new FluidStack(fluid, 1000));
            }
        }
        return result;
    }

    private static List<FluidStack> modFluids(String modId) {
        List<FluidStack> result = new ArrayList<>();
        for (Map.Entry<String, Fluid> entry : FluidRegistry.getRegisteredFluids().entrySet()) {
            Fluid fluid = entry.getValue();
            if (fluid.getBlock() != null && fluid.getBlock().getRegistryName() != null) {
                // 1.12.2: 通过流体方块注册名推断 mod
                String blockRegName = fluid.getBlock().getRegistryName().getNamespace();
                if (blockRegName.equals(modId)) {
                    result.add(new FluidStack(fluid, 1000));
                }
            }
        }
        return result;
    }

    private static List<FluidStack> regexFluids(String regex) {
        Pattern pattern = Pattern.compile(regex);
        List<FluidStack> result = new ArrayList<>();
        for (Map.Entry<String, Fluid> entry : FluidRegistry.getRegisteredFluids().entrySet()) {
            if (pattern.matcher(entry.getKey()).find()) {
                result.add(new FluidStack(entry.getValue(), 1000));
            }
        }
        return result;
    }

    private static List<FluidStack> fromObject(Value value) {
        if (value.hasMember("any")) return fromValue(value.getMember("any"));
        if (value.hasMember("wildcard") && value.getMember("wildcard").asBoolean()) return wildcard();
        if (value.hasMember("mod")) return modFluids(value.getMember("mod").asString());
        if (value.hasMember("regex")) return regexFluids(value.getMember("regex").asString());
        if (value.hasMember("fluid")) return fromValue(value.getMember("fluid"));
        if (value.hasMember("tag")) {
            String tag = value.getMember("tag").asString();
            return fromString(tag.startsWith("#") ? tag : "#" + tag);
        }
        throw new ValueConversionException(List.class,
            "recognized field (fluid|tag|mod|regex|wildcard|any)", value,
            "no recognized field in fluid ingredient object");
    }
}
