package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.backend.typescript.ManagedApiDeclarationGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Declaration coverage gate, the non-processor replacement for the deferred Fabric
 * {@code common-api-processor}: it checks that the TS declarations derived from one contract line
 * up item by item with the runtime members.
 *
 * <p><b>Owner</b>: Managed Surface/Probe owner (the declaration fixture); the build convention
 * owner wires it in. <b>Input</b>: the {@code NormativeApiContract} that
 * {@link CoreManagedApiBootstrap#buildContract} reflects from the runtime (the normative source)
 * plus the output of {@link ManagedApiDeclarationGenerator}. <b>Output per item</b>: the member
 * set of each owner, each global, and whether each type symbol is declared, compared with the
 * read-only baseline {@code /nekojs/platform-gates/declaration-parity.txt}; the report is written
 * to {@code build/nekojs-gates/declaration-parity.json}. <b>Failure diagnosis</b>: one entry per
 * gap using {@code missing-member} / {@code extra-member} / {@code missing-type} /
 * {@code owner-missing} / {@code type-fallback}, with the owner, the member or type name and the
 * direction of the gap.
 *
 * <p>This is a separate gate rather than a reuse of
 * {@code ManagedSurfaceDerivationDeterminismTest} because that test proves repeated generation is
 * stable and the member sets are equal, but it records no item-by-item baseline and does not
 * report a {@code type:} symbol (which falls back to {@code unknown} for an unrecognized type) as
 * a failure. This gate adds both, and states the input, output and owner of the deferred
 * processor.
 */
class ManagedDeclarationCoverageGateTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-declaration-gate.jar");
    private static final Path FIXTURE = Path.of("src/test/resources/nekojs/platform-gates/declaration-parity.txt");
    private static final Path REPORT_DIR = Path.of("build/nekojs-gates");

    @Test
    void declarationMatchesRuntimeMembersAndReportsGaps() throws Exception {
        CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), TEST_CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                ApiSurfaceTestSupport.serverEnvironment(), core.contracts(),
                List.of(core.contributions()), List.of());
        ApiEnvironmentSnapshot environment = registry.environmentSnapshot();
        List<ApiSymbol> symbols = environment.surfaceSnapshot().symbols();

        String ts = new ManagedApiDeclarationGenerator()
                .generate(Map.of(ScriptType.SERVER, environment), ScriptType.SERVER);
        assertTrue(!ts.isBlank(), "managed TS declaration 不能为空");

        Set<String> renderedOwners = interfaceOwners(ts);
        Set<String> renderedGlobals = constNames(ts);

        Set<String> contractOwners = new TreeSet<>();
        Set<String> contractGlobals = new TreeSet<>();
        Set<String> contractTypes = new TreeSet<>();
        Set<String> contractMembers = new TreeSet<>();

        for (ApiSymbol symbol : symbols) {
            ApiSymbolId id = symbol.id();
            switch (id.kind()) {
                case "member" -> {
                    String qualified = id.qualifiedName();
                    int separator = qualified.lastIndexOf('.');
                    assertTrue(separator > 0, "member symbol must be qualified: " + id.value());
                    String owner = qualified.substring(0, separator);
                    contractOwners.add(owner);
                    contractMembers.add(owner + "#" + qualified.substring(separator + 1));
                }
                case "global" -> contractGlobals.add(id.qualifiedName());
                case "type" -> contractTypes.add(id.qualifiedName());
                default -> { /* Any other kind is not part of the declaration parity input */ }
            }
        }

        List<String> gaps = new ArrayList<>();
        for (String owner : contractOwners) {
            if (!renderedOwners.contains(owner)) {
                gaps.add("owner-missing owner=" + owner + " owner_ref=Managed Surface/Probe owner"
                        + " :: 契约有该 owner 的 member，但声明的 interface $" + owner + " 不存在");
            }
        }
        for (String owner : renderedOwners) {
            if (!contractOwners.contains(owner)) {
                gaps.add("extra-member owner=" + owner + " owner_ref=Managed Surface/Probe owner"
                        + " :: 声明渲染了契约没有的 interface $" + owner);
            }
        }
        for (String member : contractMembers) {
            String owner = member.substring(0, member.indexOf('#'));
            String name = member.substring(member.indexOf('#') + 1);
            if (!renderedOwners.contains(owner) || !rendersMember(ts, owner, name)) {
                gaps.add("missing-member owner=" + owner + " member=" + name
                        + " owner_ref=Managed Surface/Probe owner"
                        + " :: 契约 member 未出现在 interface $" + owner + " 的声明中");
            }
        }
        for (String global : contractGlobals) {
            if (!renderedGlobals.contains(global)) {
                gaps.add("missing-member owner=<global> member=" + global
                        + " owner_ref=Managed Surface/Probe owner"
                        + " :: 契约 global 未渲染为 const " + global);
            }
        }
        for (String global : renderedGlobals) {
            if (!contractGlobals.contains(global)) {
                gaps.add("extra-member owner=<global> member=" + global
                        + " owner_ref=Managed Surface/Probe owner"
                        + " :: 声明渲染了契约没有的 const " + global);
            }
        }
        // No producer currently creates an ApiSymbolId with kind="type"; the kinds actually built
        // are global/member/event/adapter/hostExt/java (verified with grep). So an assertion that
        // "contract type -> declared interface" can never run, and it would claim coverage with no
        // evidence. The contract type set is reported as a fact instead, item by item (today it is
        // empty); if a type-kind symbol ever appears, the check below lists it as a typed gap
        // rather than passing on an assertion that is always true.
        // A symbol whose type the generator cannot render becomes unknown in the declaration. It
        // is recorded explicitly, not passed over.
        for (ApiSymbol symbol : symbols) {
            for (String fallback : unknownFallbackTypes(symbol)) {
                gaps.add("type-fallback owner=" + ownerOf(symbol) + " type=" + fallback
                        + " owner_ref=Managed Surface/Probe owner"
                        + " :: 契约类型引用 " + fallback
                        + " 未被 declararation 渲染器识别，声明降级为 unknown（先修渲染器或补契约类型，"
                        + "不要放宽断言）");
            }
        }

        gaps.addAll(typeKindGaps(symbols));

        Map<String, String> rows = new TreeMap<>();
        rows.put("globals", String.join(",", contractGlobals));
        rows.put("owners", String.join(",", contractOwners));
        rows.put("types", contractTypes.isEmpty()
                ? "<empty: no kind=type producer in the contract>" : String.join(",", contractTypes));
        rows.put("members", String.valueOf(contractMembers.size()));
        rows.put("signatures", String.valueOf(symbols.stream().mapToInt(s -> s.signatures().size()).sum()));
        emitReport(rows, gaps);

        // Report locatable gaps before comparing the baseline, so a real regression surfaces as a
        // named diagnostic such as missing-member or type-fallback, not just "baseline drifted".
        assertTrue(gaps.isEmpty(), "declaration gate 失败（owner=Managed Surface/Probe owner；"
                + "逐项输出见 " + REPORT_DIR + "/declaration-parity.json）:\n" + String.join("\n", gaps));

        Map<String, String> expected = readFixture();
        assertEquals(expected, rows, "declaration parity 基线漂移（fixture=" + FIXTURE
                + "）。确认是规范/渲染器的有意变更后更新 fixture 并在工单 33 REPORT 的 golden 差异小节留记录");
    }

    /**
     * Reverse check for `type-symbol-observed`: the gap must actually fire. A synthetic type-kind
     * contract symbol goes through the same classification, and the test asserts that it produces
     * a locatable gap of its own.
     */
    @Test
    void typeKindSymbolProducesObservableGap() {
        List<ApiSymbol> symbols = List.of(new ApiSymbol(
                ApiSymbolId.parse("type:Synthetic"),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType()))));

        List<String> gaps = typeKindGaps(symbols);

        assertTrue(gaps.stream().anyMatch(g -> g.startsWith("type-symbol-observed type=Synthetic")),
                "type-kind 符号必须产出可定位缺口，而不是被静默忽略：" + gaps);
    }

    /**
     * `kind="type"` has no producer, so the positive input must be the empty set. This test pins
     * that fact by asserting the empty set itself, rather than relying on an always-true
     * missing-type check.
     */
    @Test
    void contractCarriesNoTypeKindSymbolsToday() {
        CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), TEST_CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                ApiSurfaceTestSupport.serverEnvironment(), core.contracts(),
                List.of(core.contributions()), List.of());

        List<String> gaps = typeKindGaps(registry.environmentSnapshot().surfaceSnapshot().symbols());

        assertTrue(gaps.stream().noneMatch(g -> g.startsWith("type-symbol-observed")),
                "当前契约不应出现 type-kind 符号；出现则必须先补渲染器：" + gaps);
    }

    /**
     * Locatable gap for a {@code kind="type"} symbol in the contract.
     *
     * <p>No producer emits type-kind symbols today, so normal input returns an empty list.
     * `typeKindSymbolProducesObservableGap` feeds a synthetic symbol and proves the diagnostic is
     * reachable. A `missing-type` check would compare the same set on both sides and always pass,
     * which is why this gate reports the gap instead.
     */
    static List<String> typeKindGaps(List<ApiSymbol> symbols) {
        List<String> gaps = new ArrayList<>();
        for (ApiSymbol symbol : symbols) {
            if (!"type".equals(symbol.id().kind())) continue;
            gaps.add("type-symbol-observed type=" + symbol.id().qualifiedName()
                    + " owner_ref=Managed Surface/Probe owner"
                    + " :: 契约出现了 type-kind 符号，但 ManagedApiDeclarationGenerator 尚无该 kind 的"
                    + "渲染路径（TS 声明只渲染 global/member）。必须补渲染器并更新本 gate，"
                    + "不要在 gate 里放宽断言");
        }
        return gaps;
    }

    // ---- Reading the rendered declaration ----

    private static Set<String> interfaceOwners(String ts) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = Pattern.compile("^    interface \\$(\\w+) \\{", Pattern.MULTILINE).matcher(ts);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    private static Set<String> constNames(String ts) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = Pattern.compile("^    const (\\w+):", Pattern.MULTILINE).matcher(ts);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    private static boolean rendersMember(String ts, String owner, String member) {
        int start = ts.indexOf("interface $" + owner + " {");
        if (start < 0) return false;
        int end = ts.indexOf("\n    }", start);
        String body = end < 0 ? ts.substring(start) : ts.substring(start, end);
        return Pattern.compile("^        " + Pattern.quote(member) + "\\(", Pattern.MULTILINE)
                .matcher(body).find();
    }

    // ---- Detecting type fallback ----

    private static String ownerOf(ApiSymbol symbol) {
        String qualified = symbol.id().qualifiedName();
        int separator = qualified.lastIndexOf('.');
        return separator > 0 && "member".equals(symbol.id().kind())
                ? qualified.substring(0, separator) : "<" + symbol.id().kind() + ">";
    }

    /** Types the contract references but the generator falls back to unknown (SYMBOL or
     *  TYPE_VARIABLE the current generator does not cover). */
    private static Set<String> unknownFallbackTypes(ApiSymbol symbol) {
        Set<String> found = new TreeSet<>();
        for (ApiSignature signature : symbol.signatures()) {
            collectFallback(signature.returnType(), found);
            for (ApiParameter parameter : signature.parameters()) {
                collectFallback(parameter.type(), found);
            }
        }
        return found;
    }

    private static void collectFallback(ApiTypeRef type, Set<String> found) {
        if (type == null) return;
        if (type.kind() == ApiTypeRef.Kind.TYPE_VARIABLE) {
            found.add("typevar:" + type.name());
        }
        for (ApiTypeRef argument : type.arguments()) collectFallback(argument, found);
        if (type.callbackSignature() != null) {
            collectFallback(type.callbackSignature().returnType(), found);
            for (ApiParameter parameter : type.callbackSignature().parameters()) {
                collectFallback(parameter.type(), found);
            }
        }
    }

    // ---- Writing the report ----

    private static void emitReport(Map<String, String> rows, List<String> gaps) throws IOException {
        Files.createDirectories(REPORT_DIR);
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"check\": \"declaration-parity\",\n");
        json.append("  \"owner\": \"Managed Surface/Probe owner; build convention owner (wiring)\",\n");
        json.append("  \"rows\": [\n");
        int i = 0;
        for (Map.Entry<String, String> row : rows.entrySet()) {
            String line = row.getKey() + "=" + row.getValue();
            json.append("    \"").append(line.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"")
                    .append(++i < rows.size() ? ",\n" : "\n");
        }
        json.append("  ],\n  \"failures\": [\n");
        for (int j = 0; j < gaps.size(); j++) {
            json.append("    \"").append(gaps.get(j).replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"").append(j + 1 < gaps.size() ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");
        Files.writeString(REPORT_DIR.resolve("declaration-parity.json"), json.toString());
    }

    // ---- Read-only baseline ----

    /** Minimal platform implementation with no side effects (same as
     *  ManagedSurfaceDerivationDeterminismTest.EmptyPlatform). */
    private static final class EmptyPlatform implements com.tkisor.nekojs.platform.IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public java.nio.file.Path getGameDir() { return java.nio.file.Path.of("."); }
        @Override public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
        @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0.0.0"; }
    }

    private static Map<String, String> readFixture() throws IOException {
        Map<String, String> result = new TreeMap<>();
        if (!Files.isRegularFile(FIXTURE)) return result;
        for (String raw : Files.readString(FIXTURE, StandardCharsets.UTF_8).split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            assertTrue(separator > 0, "基线行格式错误（期望 key=value）：" + line);
            result.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return result;
    }
}