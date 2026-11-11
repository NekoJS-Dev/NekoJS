package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PythonInterfaceBeanTest {
    public interface ReadOnly {
        String getLabel();
    }

    public interface ReadWrite {
        int getCount();
        void setCount(int value);
    }

    public interface ConflictingNames {
        int version = 7;
        int getVersion();
        String getTitle();
        void setTitle(String value);
        String title(String input);
    }

    public interface OverloadedGetter {
        String getLabel();
        String getLabel(int index);
        void ping();
    }

    public static class ClassBean {
        private int count;
        public int getCount() { return count; }
        public void setCount(int value) { count = value; }
    }

    @TempDir Path directory;

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void readonlyInterfacePropertiesKeepTheirRealGetterCalls() {
        String module = render(new TypeReflector().reflect(ReadOnly.class));

        assertTrue(module.contains("    @property\n    def label(self) -> str: ..."), module);
        assertFalse(module.contains("@label.setter"), module);
        assertTrue(module.contains("    def getLabel(self) -> str: ..."), module);
    }

    @Test
    void readwriteInterfacePropertiesKeepBothRealAccessorCalls() {
        String module = render(new TypeReflector().reflect(ReadWrite.class));

        assertTrue(module.contains("    @property\n    def count(self) -> int: ..."), module);
        assertTrue(module.contains("    @count.setter\n    def count(self, value: int) -> None: ..."), module);
        assertTrue(module.contains("    def getCount(self) -> int: ..."), module);
        assertTrue(module.contains("    def setCount(self, value: int) -> None: ..."), module);
    }

    @Test
    void realMethodsAndConstantsWinExactPropertyNameConflicts() {
        String module = render(new TypeReflector().reflect(ConflictingNames.class));

        assertFalse(module.contains("    @property\n    def title("), module);
        assertFalse(module.contains("    @title.setter"), module);
        assertFalse(module.contains("    @property\n    def version("), module);
        assertTrue(module.contains("    version: ClassVar[int]"), module);
        assertTrue(module.contains("    def title(self, input: str) -> str: ..."), module);
        assertTrue(module.contains("    def getTitle(self) -> str: ..."), module);
        assertTrue(module.contains("    def setTitle(self, value: str) -> None: ..."), module);
        assertTrue(module.contains("    def getVersion(self) -> int: ..."), module);
    }

    @Test
    void retainedGetterOverloadsImportTypingOverloadAndDecorateBothRealCalls() {
        var declaration = new TypeReflector().reflect(OverloadedGetter.class);
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("getLabel")
                && method.params.isEmpty() && method.isGetter));
        String module = render(declaration);

        assertTrue(module.lines().anyMatch(line -> line.startsWith("from typing import ")
                && line.contains("overload")), module);
        assertEquals(2, module.split(java.util.regex.Pattern.quote("    @overload\n    def getLabel("), -1)
                .length - 1, module);
        assertTrue(module.contains("    @property\n    def label(self) -> str: ..."), module);
        assertTrue(module.contains("    def ping(self) -> None: ..."), module);
        assertFalse(module.contains("    @overload\n    def ping("), module);
    }

    @Test
    void interfacePropertiesUseEditedReadAndWriteTypes() {
        var declaration = new TypeReflector().reflect(ReadWrite.class);
        var getter = declaration.methods.stream().filter(method -> method.name.equals("getCount"))
                .findFirst().orElseThrow();
        getter.returnType.ref = ApiTypeRef.primitive("boolean");
        getter.setterParamType.ref = ApiTypeRef.primitive("string");
        String module = render(declaration);

        assertTrue(module.contains("    @property\n    def count(self) -> bool: ..."), module);
        assertTrue(module.contains("    @count.setter\n    def count(self, value: str) -> None: ..."), module);
    }

    @Test
    void classesKeepTheirExistingBeanOnlyAccessorRepresentation() {
        String module = render(new TypeReflector().reflect(ClassBean.class));

        assertTrue(module.contains("    @property\n    def count(self) -> int: ..."), module);
        assertTrue(module.contains("    @count.setter\n    def count(self, value: int) -> None: ..."), module);
        assertFalse(module.contains("    def getCount("), module);
        assertFalse(module.contains("    def setCount("), module);
    }

    private String render(TypeDecl declaration) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, Map.of(), List.of());
        var context = new ProbeContext.Of(snapshot, List.of(declaration.sourceClass), ProbeConfig.defaultConfig(),
                NekoJSPaths.fromGameDir(directory), "python", directory.resolve("python"), List.of(declaration));
        String module = new PythonProbeBackend().render(context)
                .get("nekojs/_java/com/tkisor/nekojs/probe/__init__.pyi");
        assertNotNull(module);
        return module;
    }
}
