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
 * Parity fixture for typed builder declarations: the TS renderer ({@link RegistryBuilderTsRenderer},
 * behind the probe backend's {@code @registry-builders/index.d.ts}) and the Python renderer
 * ({@link RegistryBuilderPyRenderer}, behind {@code _registry_builders/__init__.pyi}) render the same
 * entry list. Member name order, the member kind (writable, read-only, method) and the type mapping
 * match on both sides. Output is deterministic: rendering twice is byte-for-byte identical.
 *
 * <p>The real entries are derived in the version tree by reflecting the builder class; a golden test
 * in the root test tree, {@code RegistryBuilderSurfaceGoldenTest}, freezes that surface. This fixture
 * uses hand-written entries shaped like the real derivation to pin the two renderers to each other.
 * The common tree has no MC types.
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

        // Same contract input: both sides list builders in the same order and the member names match one for one
        assertEquals(builderNames(ts, "interface "), builderNames(py, "class "),
                "TS 与 Python 的 builder 列表必须同源同序");
        assertEquals(memberNames(ts), memberNames(py),
                "TS 与 Python 的成员名序列必须逐条相同（同一契约条目驱动）");
    }

    @Test
    void writablePropertyIsWritableInBothDeclarationsAndReadOnlyIsMarked() {
        String ts = RegistryBuilderTsRenderer.render(fixtureEntries());
        String py = RegistryBuilderPyRenderer.render(fixtureEntries());

        // Writable property: a plain property on both sides, with no readonly and no read-only marker
        assertTrue(ts.contains("    maxStackSize: number;"), ts);
        assertTrue(py.contains("    maxStackSize: int\n"), py);
        assertFalse(ts.contains("readonly maxStackSize"), "可写成员不得标 readonly");
        assertFalse(py.contains("maxStackSize: int  # read-only"), "可写成员不得标 read-only");

        // Read-only property: TS marks it {@code readonly} and Python adds a read-only comment; the
        // final id exception is shown the same way on both sides
        assertTrue(ts.contains("    readonly id: string;"), ts);
        assertTrue(py.contains("    id: str  # read-only\n"), py);

        // An explicit setter stays a method member, so no second write path is introduced
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

    /** Collects builder names from rendered text by declaration header:
     * TS {@code interface X}, Python {@code class X}. */
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

    /** Member names: each 4-space-indented member line is reduced to a name, so {@code readonly id:}
     * becomes id, {@code def foo(} becomes foo and {@code bar(} becomes bar. */
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
