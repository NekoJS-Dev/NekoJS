package com.tkisor.nekojs.probe.backend;

import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.probe.backend.python.RegistryBuilderPyRenderer;
import com.tkisor.nekojs.probe.backend.typescript.RegistryBuilderTsRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * typed Builder 声明两侧 parity fixture（ticket 15 AC4/AC9）：
 * TS（{@link RegistryBuilderTsRenderer}，probe 后端 {@code @registry-builders/index.d.ts}
 * 的渲染器）与 Python（{@link RegistryBuilderPyRenderer}，{@code _registry_builders/__init__.pyi}
 * 的渲染器）从<b>同一条目列表</b>渲染——成员名序列、种类标注（可写/只读/方法）与
 * 类型映射两侧一致；输出确定（重复渲染逐字节稳定）。
 *
 * <p>条目本身由版本树对 builder 类的契约反射派生（根测试树 golden
 * {@code RegistryBuilderSurfaceGoldenTest} 冻结真实面）；本 fixture 用与真实派生同构的
 * 手工条目钉住<b>渲染层</b>的双侧一致性——common 树零 MC 类型（ADR-0007 L1/L2）。
 */
class RegistryBuilderDeclarationParityTest {

    private static List<RegistryBuilderSurfaceEntry> fixtureEntries() {
        List<RegistryBuilderSurfaceEntry.Member> members = new ArrayList<>();
        members.add(new RegistryBuilderSurfaceEntry.Member(
                "id", RegistryBuilderSurfaceEntry.MemberKind.READ_ONLY_PROPERTY, "string", "str"));
        members.add(new RegistryBuilderSurfaceEntry.Member(
                "maxStackSize", RegistryBuilderSurfaceEntry.MemberKind.WRITABLE_PROPERTY, "number", "int"));
        members.add(new RegistryBuilderSurfaceEntry.Member(
                "fireResistant", RegistryBuilderSurfaceEntry.MemberKind.WRITABLE_PROPERTY, "boolean", "bool"));
        members.add(new RegistryBuilderSurfaceEntry.Member(
                "setMaxStackSize", RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                "setMaxStackSize(arg0: number): void", "def setMaxStackSize(self, arg0: int) -> None: ..."));
        members.add(new RegistryBuilderSurfaceEntry.Member(
                "food", RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                "food(arg0: (b: any) => void): void", "def food(self, arg0: Callable[[Any], None]) -> None: ..."));
        List<RegistryBuilderSurfaceEntry> entries = new ArrayList<>();
        entries.add(new RegistryBuilderSurfaceEntry(
                "ItemBuilder", "minecraft:item", "basic", "item", List.copyOf(members),
                "Startup registry builder for 'minecraft:item' (type 'basic')."));
        entries.add(new RegistryBuilderSurfaceEntry(
                "FluidBuilder", "minecraft:fluid", "basic", null, List.of(), "no members variant"));
        return List.copyOf(entries);
    }

    @Test
    void bothBackendsRenderTheSameMemberSequenceFromTheSameEntryList() {
        List<RegistryBuilderSurfaceEntry> entries = fixtureEntries();

        String ts = RegistryBuilderTsRenderer.render(entries);
        String py = RegistryBuilderPyRenderer.render(entries);

        // 同一契约输入 → 两侧 builder 面同序、成员名序列逐一相同（成员语义一致）
        assertEquals(builderNames(ts, "interface "), builderNames(py, "class "),
                "TS 与 Python 的 builder 列表必须同源同序");
        assertEquals(memberNames(ts), memberNames(py),
                "TS 与 Python 的成员名序列必须逐条相同（同一契约条目驱动）");
    }

    @Test
    void writablePropertyIsWritableInBothDeclarationsAndReadOnlyIsMarked() {
        String ts = RegistryBuilderTsRenderer.render(fixtureEntries());
        String py = RegistryBuilderPyRenderer.render(fixtureEntries());

        // 可写属性：两侧都是裸属性标注（无 readonly / 无 read-only 注释）
        assertTrue(ts.contains("    maxStackSize: number;"), ts);
        assertTrue(py.contains("    maxStackSize: int\n"), py);
        assertFalse(ts.contains("readonly maxStackSize"), "可写成员不得标 readonly");
        assertFalse(py.contains("maxStackSize: int  # read-only"), "可写成员不得标 read-only");

        // 只读属性：TS readonly，Python read-only 注释（final id 例外清单在两侧一致呈现）
        assertTrue(ts.contains("    readonly id: string;"), ts);
        assertTrue(py.contains("    id: str  # read-only\n"), py);

        // 显式 setter 形态作为方法成员保留（不引入第二写入语义）
        assertTrue(ts.contains("    setMaxStackSize(arg0: number): void;"), ts);
        assertTrue(py.contains("    def setMaxStackSize(self, arg0: int) -> None: ..."), py);
    }

    @Test
    void renderingIsDeterministicAcrossRepeatedCalls() {
        List<RegistryBuilderSurfaceEntry> entries = fixtureEntries();
        assertEquals(RegistryBuilderTsRenderer.render(entries), RegistryBuilderTsRenderer.render(entries),
                "TS 渲染必须逐字节稳定");
        assertEquals(RegistryBuilderPyRenderer.render(entries), RegistryBuilderPyRenderer.render(entries),
                "Python 渲染必须逐字节稳定");
    }

    @Test
    void emptyOrNullEntryListRendersEmptyOnBothBackends() {
        assertEquals("", RegistryBuilderTsRenderer.render(List.of()));
        assertEquals("", RegistryBuilderPyRenderer.render(List.of()));
        assertEquals("", RegistryBuilderTsRenderer.render(null));
        assertEquals("", RegistryBuilderPyRenderer.render(null));
    }

    /** 从渲染文本按声明头提取 builder 名序列（TS {@code interface X} / Py {@code class X}）。 */
    private static List<String> builderNames(String rendered, String header) {
        List<String> names = new ArrayList<>();
        for (String line : rendered.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(header)) {
                names.add(trimmed.substring(header.length()).split("[({ ]")[0]);
            }
        }
        return names;
    }

    /** 成员名序列：4 空格缩进的成员行归一为成员名（{@code readonly id:}→id、{@code def foo(}→foo、{@code bar(}→bar）。 */
    private static List<String> memberNames(String rendered) {
        List<String> names = new ArrayList<>();
        for (String line : rendered.split("\n", -1)) {
            if (!line.startsWith("    ") || line.startsWith("     ")) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")
                    || trimmed.startsWith("from ")) {
                continue;
            }
            if (trimmed.startsWith("def ")) {
                names.add(trimmed.substring(4).split("[(]")[0]);
                continue;
            }
            if (trimmed.startsWith("readonly ")) {
                trimmed = trimmed.substring("readonly ".length());
            }
            names.add(trimmed.split("[:\\s(]")[0]);
        }
        return names;
    }
}
