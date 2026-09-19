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
 * 工单 33（W9）declaration 覆盖 gate —— Fabric {@code common-api-processor} 延期后的
 * 非 processor 替代门禁之三：同一规范契约派生出的 TS 声明与 runtime 成员逐项对齐。
 *
 * <p><b>owner</b>：Managed Surface/Probe owner（declaration fixture）；build convention owner
 * 负责接线。<b>输入</b>：{@link CoreManagedApiBootstrap#buildContract} 从运行时反射得到的
 * {@code NormativeApiContract}（规范源）+ {@link ManagedApiDeclarationGenerator} 的产物。
 * <b>逐项输出</b>：每个 owner 的 member 集合、每个 global、每个 type 符号的声明存在性，
 * 与只读基线 {@code /nekojs/platform-gates/declaration-parity.txt} 对比；并落
 * {@code build/nekojs-gates/declaration-parity.json}。
 * <b>失败诊断</b>：逐条给出 {@code missing-member} / {@code extra-member} /
 * {@code missing-type} / {@code owner-missing} / {@code type-fallback}，
 * 带 owner、member/type 名与缺失方向。
 *
 * <p><b>为什么这是独立 gate 而不是复用 ticket 09 的派生测试</b>：09 的
 * {@code ManagedSurfaceDerivationDeterminismTest} 证明"重复生成稳定 + 成员集合相等"，
 * 但它不登记逐项基线，也没有把 {@code type:} 符号（对未识别类型会 fallback 成
 * {@code unknown}）纳入失败诊断。本 gate 补齐这两点，并明确 processor 延期的输入/输出/owner。
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
                default -> { /* 其它 kind 不在 declaration parity 输入内 */ }
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
        // 票 33 review F6：`kind="type"` 的 ApiSymbolId 目前**没有任何生产者**（实际构造的 kind
        // 只有 global/member/event/adapter/hostExt/java，grep 实证），所以"契约 type → 声明 interface"
        // 的断言是不可达的——那正是 AC3 要消灭的"把没证据写成覆盖"。这里改为把契约 type 集合
        // 作为**事实**逐项输出（当前为空集），一旦将来真出现 type-kind 符号，下面的
        // type-symbol-observed 会立刻把它暴露出来，而不是靠一条永远为真的断言装作已覆盖。
        // 契约携带但渲染器无法识别类型的符号：声明里只能是 unknown —— 显式记录，不静默通过。
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

        // 先报可定位的缺口，再比对基线：真正的回归应得到 missing-member/type-fallback 这类
        // 指名诊断，而不是只看到"基线漂移"（AC8）。
        assertTrue(gaps.isEmpty(), "declaration gate 失败（owner=Managed Surface/Probe owner；"
                + "逐项输出见 " + REPORT_DIR + "/declaration-parity.json）:\n" + String.join("\n", gaps));

        Map<String, String> expected = readFixture();
        assertEquals(expected, rows, "declaration parity 基线漂移（fixture=" + FIXTURE
                + "）。确认是规范/渲染器的有意变更后更新 fixture 并在工单 33 REPORT 的 golden 差异小节留记录");
    }

    /**
     * 票 33 review F6 的反向检查：`type-symbol-observed` 必须真的会被触发。
     * 用一个合成的 type-kind 契约符号跑同一段分类逻辑，断言它产出可定位缺口
     * （而不是像原 missing-type 那样永远不可达）。
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
     * 票 33 review F6：`kind="type"` 目前没有生产者，因此正向输入必然为空集；
     * 这条断言把这个**事实**钉住——断言空集本身（而不是一条永远为真的 missing-type）。
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
     * 票 33 review F6：契约里出现 {@code kind="type"} 符号时的可定位缺口。
     *
     * <p>当前契约没有 type-kind 生产者，所以正常输入返回空列表；`typeKindSymbolProducesObservableGap`
     * 用合成符号证明这条诊断**真的可达**（原 `missing-type` 断言两侧都由同一集合驱动、
     * 永远为真，属于 AC3 要消灭的假覆盖）。
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

    // ---- 渲染产物读取 ----

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

    // ---- 类型降级检测 ----

    private static String ownerOf(ApiSymbol symbol) {
        String qualified = symbol.id().qualifiedName();
        int separator = qualified.lastIndexOf('.');
        return separator > 0 && "member".equals(symbol.id().kind())
                ? qualified.substring(0, separator) : "<" + symbol.id().kind() + ">";
    }

    /** 契约里引用了、但渲染器会降级为 unknown 的类型（当前渲染器未覆盖的 SYMBOL/TYPE_VARIABLE）。 */
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

    // ---- 逐项输出落盘 ----

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

    // ---- 只读基线 ----

    /** 无需平台副作用的最小平台实现（同 ManagedSurfaceDerivationDeterminismTest.EmptyPlatform）。 */
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