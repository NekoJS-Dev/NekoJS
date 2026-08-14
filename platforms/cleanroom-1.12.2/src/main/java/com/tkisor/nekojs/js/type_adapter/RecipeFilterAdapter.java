package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 simplified RecipeFilter adapter.
 *
 * <p>Parses filter criteria from JS objects into structured data maps.
 * Since 1.12.2 lacks RecipeHolder/HolderLookup/Registries APIs,
 * the filter objects are returned as structured data for downstream use.
 *
 * <p><b>遗留风险（legacy risk）：</b>本适配器以 {@code Object.class} 注册（经
 * {@code NekoSharedHostAccess} 的 {@code targetTypeMapping(Value.class, Object.class, ...)}），
 * 覆盖面远大于配方过滤——任何宿主方法签名中的 {@code Object} 参数都可能被本适配器接管。
 * 为避免静默丢数据，{@link #apply} 对对象形状做<b>严格校验</b>：任何不属于 KNOWN_KEYS 的键
 * 都会抛 {@link ValueConversionException}（而不是像旧实现那样静默丢弃成空 filter，
 * 例如 {@code {a:1, b:2}} 会被转成空 {@code LinkedHashMap{}}）。
 * 彻底收窄注册范围需要专用持有类型（参考 neoforge 平台的
 * {@code com.tkisor.nekojs.api.recipe.RecipeFilter}）并同步修改
 * {@code RecipeEventJS} 等方法签名，超出本文件可控范围，故以严格校验兜底。
 */
public final class RecipeFilterAdapter implements JSTypeAdapter<Object> {

    /** 合法 filter 键（与 {@link #inputShapes()} 的 object 槽位一致，用于严格校验）。 */
    private static final List<String> KNOWN_KEYS = List.of(
            "not", "and", "or",
            "output", "input", "mod", "group", "id",
            "idStartsWith", "idEndsWith", "idContains", "type");

    @Override
    public Class<Object> getTargetClass() {
        return Object.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                string(),
                arrayOf(self()),
                object(
                        Slot.opt("not", self()),
                        Slot.opt("and", self()),
                        Slot.opt("or", self()),
                        Slot.opt("output", string()),
                        Slot.opt("input", string()),
                        Slot.opt("mod", string()),
                        Slot.opt("group", string()),
                        Slot.opt("id", string()),
                        Slot.opt("idStartsWith", string()),
                        Slot.opt("idEndsWith", string()),
                        Slot.opt("idContains", string()),
                        Slot.opt("type", string())));
    }

    @Override
    public boolean test(Value value) {
        return value != null && (value.isString() || value.hasMembers() || value.hasArrayElements());
    }

    @Override
    public Object apply(Value value) {
        if (value == null || value.isNull()) return null;

        if (value.isString()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", value.asString());
            return result;
        }

        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                Object sub = apply(value.getArrayElement(i));
                if (sub != null) list.add(sub);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("or", list);
            return result;
        }

        Map<String, Object> filter = new LinkedHashMap<>();

        // 严格校验：Object.class 注册使本适配器可能被任意 Object 参数触发，
        // 对象形状必须先整体校验键集合——未知键直接抛错而非静默丢弃，
        // 否则 {a:1, b:2} 会被静默转成空 filter（数据丢失）。
        if (!value.hasMembers()) {
            throw new ValueConversionException(getTargetClass(),
                    "recipe filter (string | array | object)", value,
                    "unsupported recipe filter value shape; only string/array/object filters are accepted");
        }
        for (String key : value.getMemberKeys()) {
            if (!KNOWN_KEYS.contains(key)) {
                throw new ValueConversionException(getTargetClass(),
                        "recipe filter object with keys " + KNOWN_KEYS, value,
                        "unknown key \"" + key + "\": RecipeFilterAdapter only accepts documented filter keys; "
                                + "misspelled or unsupported keys are rejected to prevent silent data loss");
            }
        }

        if (value.hasMember("not")) {
            Object sub = apply(value.getMember("not"));
            if (sub != null) filter.put("not", sub);
        }

        if (value.hasMember("and")) {
            Object sub = apply(value.getMember("and"));
            if (sub != null) filter.put("and", sub);
        }

        if (value.hasMember("or")) {
            Object sub = apply(value.getMember("or"));
            if (sub != null) filter.put("or", sub);
        }

        copyStringMember(value, filter, "output");
        copyStringMember(value, filter, "input");
        copyStringMember(value, filter, "mod");
        copyStringMember(value, filter, "group");
        copyStringMember(value, filter, "id");
        copyStringMember(value, filter, "idStartsWith");
        copyStringMember(value, filter, "idEndsWith");
        copyStringMember(value, filter, "idContains");
        copyStringMember(value, filter, "type");

        // 空对象 = 无约束：返回空 Map 表示匹配全部，避免 apply 返回 null 让调用方 NPE
        return filter;
    }

    private void copyStringMember(Value value, Map<String, Object> filter, String key) {
        if (value.hasMember(key)) {
            filter.put(key, value.getMember(key).asString());
        }
    }
}
