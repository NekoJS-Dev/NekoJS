package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * RecipeFilter adapter：把 JS 过滤条件解析为 {@link RecipeFilter}（顶层数组为
 * {@link RecipeFilter.Or}，扁平对象为 {@link RecipeFilter.And}，字符串为
 * {@link RecipeFilter.ById}）。
 *
 * <p><b>严格校验契约</b>：对象分支仅接受文档中列出的 12 个已知键（not/and/or/
 * output/input/mod/group/id/idStartsWith/idEndsWith/idContains/type）。出现未知键
 * （如把 {@code input} 误拼为 {@code imput}）或值无法进入任何分支（如数字）时，
 * 立即抛 {@link ValueConversionException}，防止条件被静默丢弃后落入
 * {@code andFilters.isEmpty()} 分支退化成“匹配全部”造成误删。空对象 {@code {}}
 * 仍表示无约束（匹配全部），属于有意行为，不抛异常。
 */
public final class RecipeFilterAdapter implements JSTypeAdapter<RecipeFilter> {
    /** 对象分支允许的全部键；出现集合外的键一律抛异常，防止拼写错误被静默忽略 */
    private static final Set<String> KNOWN_KEYS = Set.of(
            "not", "and", "or", "output", "input", "mod", "group",
            "id", "idStartsWith", "idEndsWith", "idContains", "type");

    @Override
    public Class<RecipeFilter> getTargetClass() { return RecipeFilter.class; }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                string(),
                arrayOf(self()),
                object(
                        Slot.opt("not", self()),
                        Slot.opt("and", self()),
                        Slot.opt("or", self()),
                        // output/input 是物品 id 或 #标签：逐个列出而不是用裸 string，
                        // 否则联合里的 id 字面量补全会被 string 吞掉
                        Slot.opt("output", union(registry("Item"), template("#", registryTag("Item")))),
                        Slot.opt("input", union(registry("Item"), template("#", registryTag("Item")))),
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
    public RecipeFilter apply(Value value) {
        if (value == null || value.isNull()) return null;

        if (value.isString()) {
            return new RecipeFilter.ById(value.asString());
        }

        if (value.hasArrayElements()) {
            List<RecipeFilter> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                RecipeFilter sub = apply(value.getArrayElement(i));
                if (sub != null) list.add(sub);
            }
            return new RecipeFilter.Or(list);
        }

        // 非字符串/数组/对象（如数字）无法进入任何分支，静默放行会退化成“匹配全部”
        if (!value.hasMembers()) {
            throw new ValueConversionException(RecipeFilter.class, "recipe filter string / array / object",
                    value, "unsupported recipe filter value");
        }

        // 严格校验：未知键（如把 input 误拼为 imput）直接抛异常，防止条件被静默丢弃后
        // 落入 andFilters.isEmpty() 分支退化成“匹配全部”（空对象 {} 除外，那是有意的匹配全部）
        for (String key : value.getMemberKeys()) {
            if (!KNOWN_KEYS.contains(key)) {
                throw new ValueConversionException(RecipeFilter.class, "recipe filter object with documented keys",
                        value, "RecipeFilterAdapter: unknown key '" + key + "'（未知键），仅接受文档中列出的过滤键，防止条件被静默丢弃 (silent data loss)");
            }
        }

        List<RecipeFilter> andFilters = new ArrayList<>();

        if (value.hasMember("not")) {
            RecipeFilter sub = apply(value.getMember("not"));
            if (sub != null) andFilters.add(new RecipeFilter.Not(sub));
        }

        if (value.hasMember("and")) {
            RecipeFilter sub = apply(value.getMember("and"));
            if (sub != null) andFilters.add(sub);
        }

        if (value.hasMember("or")) {
            RecipeFilter sub = apply(value.getMember("or"));
            if (sub != null) andFilters.add(sub);
        }

        if (value.hasMember("output")) {
            andFilters.add(new RecipeFilter.ByOutput(value.getMember("output").asString()));
        }

        if (value.hasMember("input")) {
            andFilters.add(new RecipeFilter.ByInput(value.getMember("input").asString()));
        }

        if (value.hasMember("mod")) {
            andFilters.add(new RecipeFilter.ByMod(value.getMember("mod").asString()));
        }

        if (value.hasMember("group")) {
            andFilters.add(new RecipeFilter.ByGroup(value.getMember("group").asString()));
        }

        if (value.hasMember("id")) {
            andFilters.add(new RecipeFilter.ById(value.getMember("id").asString()));
        }

        if (value.hasMember("idStartsWith")) {
            andFilters.add(new RecipeFilter.ByIdStartsWith(value.getMember("idStartsWith").asString()));
        }

        if (value.hasMember("idEndsWith")) {
            andFilters.add(new RecipeFilter.ByIdEndsWith(value.getMember("idEndsWith").asString()));
        }

        if (value.hasMember("idContains")) {
            andFilters.add(new RecipeFilter.ByIdContains(value.getMember("idContains").asString()));
        }

        if (value.hasMember("type")) {
            andFilters.add(new RecipeFilter.ByType(value.getMember("type").asString()));
        }

        if (andFilters.isEmpty()) {
            // 空对象 = 无约束 = 匹配全部：And 空列表恒真，避免 apply 返回 null 让调用方 NPE
            return new RecipeFilter.And(List.of());
        }
        return andFilters.size() == 1 ? andFilters.get(0) : new RecipeFilter.And(andFilters);
    }
}