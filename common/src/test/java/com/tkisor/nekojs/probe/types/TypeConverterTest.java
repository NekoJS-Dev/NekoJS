package com.tkisor.nekojs.probe.types;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 集合输入别名的嵌套泛型回归：实参渲染串自身含 {@code ", "}（嵌套多实参泛型）时，
 * 集合别名必须仍按结构化实参数组取元素，而非把 join 后的字符串再按 ", " 拆分——
 * 旧实现曾把 {@code List<Map.Entry<String, Integer>>} 渲染成 {@code $Map$Entry<string[]}
 * （非法 TS，d.ts 解析失败，IDE 补全全灭）。
 */
class TypeConverterTest {

    static final class Fixture {
        List<Map.Entry<String, Integer>> nestedEntryList;
        Map<String, Map.Entry<String, Integer>> nestedEntryMap;
        Map<String, Integer> plainMap;
        List<String> plainList;
        Set<TimeUnit> enumSet;
        List<Map.Entry<String, Integer>>[] nestedEntryArray;
    }

    private static TypeConverter converter() {
        return new TypeConverter(new TypeAliasRegistry());
    }

    private static Type generic(String fieldName) throws NoSuchFieldException {
        Field f = Fixture.class.getDeclaredField(fieldName);
        return f.getGenericType();
    }

    @Test
    void nestedTwoArgGenericInsideCollectionInputKeepsNestedArgs() throws Exception {
        // 旧实现：split(", ") 后只留 "$Map$Entry<string" + "[]" → "$Map$Entry<string[]"（非法）
        assertEquals("$Map$Entry<string, number>[]",
                converter().toTypeScript(generic("nestedEntryList"), true));
    }

    @Test
    void nestedTwoArgGenericAsMapValueRendersFully() throws Exception {
        // 旧实现：split 得 3 段 ≠ 2，Map 分支静默退化为 { [key: string]: any }
        assertEquals("{ [key: string]: $Map$Entry<string, number> }",
                converter().toTypeScript(generic("nestedEntryMap"), true));
    }

    @Test
    void plainCollectionInputsUnchanged() throws Exception {
        assertEquals("string[]", converter().toTypeScript(generic("plainList"), true));
        assertEquals("{ [key: string]: number }", converter().toTypeScript(generic("plainMap"), true));
        assertEquals("$TimeUnit_[]", converter().toTypeScript(generic("enumSet"), true));
    }

    @Test
    void returnPositionStillRendersFullGenericType() throws Exception {
        assertEquals("$List<$Map$Entry<string, number>>",
                converter().toTypeScript(generic("nestedEntryList"), false));
    }

    @Test
    void genericArrayOfNestedGenericRendersClosedBrackets() throws Exception {
        assertEquals("$List<$Map$Entry<string, number>>[]",
                converter().toTypeScript(generic("nestedEntryArray"), false));
    }
}
