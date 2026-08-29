package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AdapterAliasGenerator} 的注册表字面量形状渲染。
 *
 * <p>重点是「原料位置要能补全」：裸 {@code string} 会把联合里的 id 字面量吞掉，
 * 所以 {@code #tag} / {@code @mod} / {@code /regex} 这类前缀语法必须走模板字面量类型
 * （{@link AdapterInputShape.TemplateValue}）而不是 {@code string}。
 */
class AdapterAliasGeneratorTest {

    /** 别名目标夹具：包路径决定别名声明落在哪个模块。 */
    static class AliasTarget {}

    private static AdapterAliasGenerator.AdapterAlias render(List<AdapterInputShape> shapes) {
        AdapterAliasGenerator generator = new AdapterAliasGenerator(new TypeAliasRegistry());
        AdapterCatalogEntry entry = new AdapterCatalogEntry(
                AliasTarget.class, shapes, ConversionPrecedence.LOW, Optional.empty());
        generator.prepare(List.of(entry), Set.of(AliasTarget.class.getName()));
        AdapterAliasGenerator.AdapterAlias alias = generator.getAlias(AliasTarget.class.getName());
        assertNotNull(alias, "alias not generated");
        return alias;
    }

    @Test
    void registryTagRendersTagLiteralUnionAndPullsRegistryImport() {
        var alias = render(List.of(self(), registryTag("Item")));
        assertEquals("$AdapterAliasGeneratorTest$AliasTarget | RegistryTypes.ItemTag", alias.union());
        assertTrue(alias.usesRegistry(), "RegistryTypes import must be requested");
    }

    @Test
    void templateWrapsHoleInBacktickTemplateLiteral() {
        var alias = render(List.of(
                registry("Item"),
                template("#", registryTag("Item")),
                template("@", namespace()),
                literal("*"),
                template("/", string())));
        assertEquals("RegistryTypes.Item | `#${RegistryTypes.ItemTag}` | `@${RegistryTypes.Namespace}`"
                        + " | \"*\" | `/${string}`",
                alias.union());
        assertTrue(alias.usesRegistry());
    }

    @Test
    void unionSlotIsParenthesizedAndKeepsRegistryImport() {
        var alias = render(List.of(object(
                Slot.opt("output", union(registry("Item"), template("#", registryTag("Item")))),
                Slot.opt("id", string()))));
        assertEquals("{ output?: (RegistryTypes.Item | `#${RegistryTypes.ItemTag}`), id?: string }",
                alias.union());
        assertTrue(alias.usesRegistry());
    }

    @Test
    void templateSuffixIsAppendedAfterTheHole() {
        var alias = render(List.of(template("/", string(), "/")));
        assertEquals("`/${string}/`", alias.union());
        assertFalse(alias.usesRegistry(), "no registry referenced");
    }

    @Test
    void objectSlotsCanUseRegistryAndTagLiterals() {
        var alias = render(List.of(object(
                Slot.opt("item", registry("Item")),
                Slot.opt("tag", registryTag("Item")),
                Slot.opt("mod", string()))));
        assertEquals("{ item?: RegistryTypes.Item, tag?: RegistryTypes.ItemTag, mod?: string }",
                alias.union());
    }

    @Test
    void namespaceRendersSharedNamespaceUnionAndPullsRegistryImport() {
        // 命名空间联合与注册表字面量同住 @special/types，所以同样要触发 RegistryTypes import；
        // 不按注册表分（一个命名空间能不能用与它出现在哪个注册表无关）
        var alias = render(List.of(self(), namespace(), object(Slot.opt("mod", namespace()))));
        assertEquals("$AdapterAliasGeneratorTest$AliasTarget | RegistryTypes.Namespace"
                        + " | { mod?: RegistryTypes.Namespace }",
                alias.union());
        assertTrue(alias.usesRegistry());
    }

    @Test
    void bareStringStillRendersAsStringForAdaptersThatWantIt() {
        var alias = render(List.of(self(), string()));
        assertEquals("$AdapterAliasGeneratorTest$AliasTarget | string", alias.union());
        assertFalse(alias.usesRegistry());
    }

    @Test
    void nestedSelfWidensToInputAliasWhileTopLevelStaysTheClass() {
        // 嵌套位置（数组元素 / 槽位）运行时同样经适配器转换，所以必须放宽成 $Foo_；
        // 顶层若也放宽会写成 $Foo_ = $Foo_ | ... 自引用成环
        var alias = render(List.of(self(), arrayOf(self()), object(
                Slot.opt("any", arrayOf(self())),
                Slot.opt("not", self()))));
        assertEquals("$AdapterAliasGeneratorTest$AliasTarget"
                        + " | $AdapterAliasGeneratorTest$AliasTarget_[]"
                        + " | { any?: $AdapterAliasGeneratorTest$AliasTarget_[],"
                        + " not?: $AdapterAliasGeneratorTest$AliasTarget_ }",
                alias.union());
    }

    @Test
    void nestedHostWidensToThatHostsInputAliasRegardlessOfAdapterOrder() {
        // SizedIngredient 先于 Ingredient 注册时也要放宽：prepare 先登记全部别名名再渲染
        AdapterAliasGenerator generator = new AdapterAliasGenerator(new TypeAliasRegistry());
        AdapterCatalogEntry outer = new AdapterCatalogEntry(
                AliasTarget.class,
                List.of(object(Slot.opt("inner", host(NestedTarget.class)), Slot.req("count", number()))),
                ConversionPrecedence.LOW, Optional.empty());
        AdapterCatalogEntry inner = new AdapterCatalogEntry(
                NestedTarget.class, List.of(self(), string()), ConversionPrecedence.LOW, Optional.empty());
        generator.prepare(List.of(outer, inner),
                Set.of(AliasTarget.class.getName(), NestedTarget.class.getName()));

        assertEquals("{ inner?: $AdapterAliasGeneratorTest$NestedTarget_, count: number }",
                generator.getAlias(AliasTarget.class.getName()).union());
    }

    /** 嵌套 host 夹具：自身也有适配器别名。 */
    static class NestedTarget {}
}
