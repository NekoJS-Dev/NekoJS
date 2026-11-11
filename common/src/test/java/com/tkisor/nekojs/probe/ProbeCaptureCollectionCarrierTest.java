package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Collection input aliases must not hide the host arm that capture views need to constrain. */
class ProbeCaptureCollectionCarrierTest {
    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void everyNativeAddAllOverloadKeepsItsCaptureCarrierAndArrayBranch(@TempDir Path directory) throws Exception {
        var snapshot = LegacyProbeFixture.snapshot();
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("java.util"), List.of(), List.of("java.time"), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot,
                List.of(Collection.class, List.class, SequencedCollection.class, Iterator.class), config,
                NekoJSPaths.fromGameDir(directory), "typescript", directory.resolve("typescript"));
        var files = new TypeScriptProbeBackend().render(context);
        String declarations = files.get("@package/java/util/index.d.ts");
        assertNotNull(declarations);
        assertTrue(declarations.contains("type $NekoCaptureCollectionInput<$Value> = globalThis.NoInfer<$Collection<any>>"
                + " & { readonly \"iterator\": () => $Iterator<$Value> };"), declarations);
        var views = Map.<Class<?>, String>of(Collection.class, "$CollectionView", List.class, "$ListView",
                SequencedCollection.class, "$SequencedCollectionView");
        int total = 0;
        for (var entry : views.entrySet()) {
            String header = "export interface " + entry.getValue() + "<B> extends $" + entry.getKey().getSimpleName() + "<B>";
            int start = declarations.indexOf(header);
            assertTrue(start >= 0, header + " missing");
            String view = declarations.substring(start, declarations.indexOf("\n    }", start));
            var signatures = Pattern.compile("addAll\\([^;]*?\\): boolean;").matcher(view).results()
                    .map(match -> match.group()).toList();
            long expected = Arrays.stream(entry.getKey().getMethods()).filter(method -> method.getName().equals("addAll")).count();
            assertEquals(expected, signatures.size(), entry.getValue() + " must retain every native overload: " + view);
            total += signatures.size();
            for (String signature : signatures) {
                assertTrue(signature.contains("$NekoCaptureCollectionInput<B>"), signature);
                assertTrue(signature.contains("B[]"), signature);
                assertFalse(signature.contains("$Collection_<B>"), "An opaque ordinary alias must not replace the capture host constraint: " + signature);
            }
            if (entry.getKey() == List.class) {
                assertEquals(1, signatures.stream().filter(signature -> Pattern.compile("addAll\\(\\s*[^,:()]+: number\\s*,")
                        .matcher(signature).find()).count(), "List's indexed addAll overload must remain callable: " + view);
            }
        }
        assertEquals(4, total, "Collection, SequencedCollection and both List addAll overloads must all be present");
    }
}
