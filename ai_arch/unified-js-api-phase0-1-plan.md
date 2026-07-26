# NekoJS Unified JS API Phase 0/1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立全版本统一 JS API 的规范格式、canonical surface、运行时代理边界、capability/module 解析、manifest/API diff，并让现有 Probe 在保留 legacy 输出的同时开始消费同一 surface。

**Architecture:** 人工审阅的 `NormativeApiContract` 是规范输入；插件和平台提交带 owner/tier 的实现贡献；`JsApiSurfaceResolver` 生成 `FrozenApiRegistry`。运行时、preflight、Probe 和 `ApiManifest` 都消费该 registry。Phase 0/1 只建立契约内核和 Probe 双轨接入，不在本计划中冻结 ID、Item、事件或配方的最终 API；这些领域必须在后续独立 contract/spec 中精确定义。

**Tech Stack:** Java 21、Gradle 9.6、JUnit Jupiter 6、Gson 2.11、GraalVM Polyglot ProxyObject/ProxyExecutable、JSON Schema 2020-12、TypeScript 5.8.3。

## Global Constraints

- 设计规范以 `ai_arch/unified-js-api-design.md` 和提交 `b0856c7` 为准。
- `NormativeApiContract` 决定产品契约；observed `ApiManifest` 只能证明实现符合规范，不能生成或扩大规范。
- schema tier 枚举固定为 `PORTABLE_STABLE`、`FEATURE`、`PLATFORM`、`VERSION`、`ADDON`、`UNSAFE_NATIVE`、`LEGACY_PREVIEW`。
- stable/feature/platform/addon 的 global、module namespace、参数、返回值和 callback payload 必须经过 `FrozenApiRegistry` 代理与转换。
- stable Facade 的 unwrap 只能位于匹配 loader + MC 的 `VERSION` module。
- `UNSAFE_NATIVE` 不得定义以 stable Facade 为输入的 unwrap export。
- 原生 MC/loader/Graal 类型不得进入 portable stable type graph。
- capability resolution 针对单个 `EnvironmentKey`，eligible provider 必须唯一。
- module dependency 先验证 DAG，再按 dependency-first 顺序求 active 传递闭包；并列节点按 module ID Unicode code-point 升序。
- 现有 bindings/events/Probe 输出在迁移期归入 `LEGACY_PREVIEW`，本计划不得破坏其现有声明。
- 每个任务采用 TDD：先失败测试，再最小实现，再全量相关测试，再提交。
- 每个 Probe 相关任务必须运行 `:common:test`；最终任务还必须运行 pinned TypeScript `tsc --noEmit`。
- 不提交 `.zcode/`、`kubejs-2601/` 或本计划无关的未跟踪 `ai_arch/` 文件。

---

## File Structure

### 规范和模型

- `common/src/main/resources/nekojs/api-contract/api-contract.schema.json`：Normative contract、module、capability、symbol 的 JSON Schema 2020-12 定义。
- `common/src/main/java/com/tkisor/nekojs/api/contract/NormativeApiContract.java`：core/addon 规范性契约 DTO。
- `common/src/main/java/com/tkisor/nekojs/api/contract/ApiContractReader.java`：读取、解析和基本 schema 语义校验。
- `common/src/main/java/com/tkisor/nekojs/api/contract/ApiContractViolation.java`：稳定、结构化 contract validation 结果。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiTier.java`：固定 tier 枚举。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiSymbolId.java`：stable symbol ID 值对象和 parser。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiTypeRef.java`：结构化 primitive/symbol/array/union/callback type graph。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiSignature.java`：参数、返回值、overload 和 callback 签名。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiSymbol.java`：canonical symbol metadata。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiSurfaceSnapshot.java`：不含 invoker 的 immutable API 层观测快照，供 catalog/Probe/manifest 使用。
- `common/src/main/java/com/tkisor/nekojs/api/surface/EnvironmentKey.java`：ScriptType/dist/loader/MC/mod 集合。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiRuntimeView.java`：API 层只读 runtime view；core frozen registry 实现它。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiRuntimeProvider.java`：按 EnvironmentKey 查询 view。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiCallHandler.java`：Graal-free addon invocation contract。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiEnvironmentSnapshot.java`：surface + verified contract hashes 的 Probe/manifest 输入。

### Capability 和 module

- `common/src/main/java/com/tkisor/nekojs/api/capability/CapabilityDefinition.java`：规范定义。
- `common/src/main/java/com/tkisor/nekojs/api/capability/CapabilityProviderContribution.java`：实现贡献。
- `common/src/main/java/com/tkisor/nekojs/api/capability/CapabilityResolver.java`：唯一 provider 和 environment activation。
- `common/src/main/java/com/tkisor/nekojs/api/module/ApiModuleDescriptor.java`：按 tier 判别的 module descriptor。
- `common/src/main/java/com/tkisor/nekojs/api/module/ApiModuleDependency.java`：按目标 tier 判别的 dependency union。
- `common/src/main/java/com/tkisor/nekojs/api/module/ApiModuleResolver.java`：DAG、inactive reason 和确定性 active set。

### Canonical runtime

- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiContribution.java`：owner/tier/module/symbol 与 runtime invoker 的实现贡献。
- `common/src/main/java/com/tkisor/nekojs/api/surface/ApiContributionRegistry.java`：插件注册接口。
- `common/src/main/java/com/tkisor/nekojs/core/api/JsApiSurfaceResolver.java`：normative conformance、overload 保留和冲突检测。
- `common/src/main/java/com/tkisor/nekojs/core/api/FrozenApiRegistry.java`：按 EnvironmentKey 冻结的查询和 runtime descriptor。
- `common/src/main/java/com/tkisor/nekojs/core/api/ApiFacadeProxy.java`：registry-backed `ProxyObject`。
- `common/src/main/java/com/tkisor/nekojs/core/api/ApiInvoker.java`：受控调用入口。
- `common/src/main/java/com/tkisor/nekojs/core/api/ApiValueMarshaller.java`：参数、返回值、callback payload 的 canonical wrapping/validation。
- `common/src/main/java/com/tkisor/nekojs/core/api/ApiRuntimeException.java`：稳定 error code 和 metadata。

### Manifest、diff 和 Probe

- `common/src/main/java/com/tkisor/nekojs/api/manifest/ApiManifestBundle.java`：root observed manifest DTO。
- `common/src/main/java/com/tkisor/nekojs/api/manifest/ApiEnvironmentManifest.java`：单 EnvironmentKey observed DTO。
- `common/src/main/java/com/tkisor/nekojs/api/manifest/ApiManifestWriter.java`：canonical JSON 和四类 hash。
- `common/src/main/java/com/tkisor/nekojs/api/manifest/ApiCompatibilityDiff.java`：breaking/additive/documentation-only diff。
- `common/src/main/java/com/tkisor/nekojs/api/catalog/LegacySurfaceAdapter.java`：当前 catalog 到 `LEGACY_PREVIEW` surface 的只读适配。
- `common/src/main/java/com/tkisor/nekojs/probe/ManagedApiDeclarationGenerator.java`：从 canonical surface 生成 managed global/module `.d.ts`。
- `common/src/main/java/com/tkisor/nekojs/probe/ApiManifestGenerator.java`：Probe staging 内输出 `api-manifest.json`。
- `common/src/main/java/com/tkisor/nekojs/probe/ProbeExternalArtifacts.java`：隔离 `.github/agents` 和 workspace config 等 outputDir 外副作用，测试使用 no-op。
- `common/src/main/java/com/tkisor/nekojs/probe/ProbeOrchestrator.java`：保留现有 legacy 路径，追加 managed/manifest 路径。

### TypeScript 门禁

- `package.json`：固定 TypeScript 5.8.3 与 `test:probe-types` 命令。
- `package-lock.json`：锁定 npm 依赖。
- `common/src/test/probe-ts/tsconfig.json`：严格、无 emit 的 Probe 声明测试配置。
- `common/src/test/probe-ts/managed-api-usage.ts`：使用 generated managed globals/modules 的类型 fixture。

---

## Execution Preflight

本机默认 `java` 是 Java 8，不能启动 Gradle 9.6。每个执行 session 在首次 Gradle 命令前运行：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
Test-Path -LiteralPath "C:\Program Files\Java\jdk-21.0.10\bin\javac.exe"
.\gradlew.bat --version
```

Expected: launcher Java 25；JDK 21 path 为 `True`；Gradle 9.6 成功启动。`common` 使用 Java 21 toolchain，26.x/Cleanroom 使用 Java 25 toolchain。

---

### Task 1: 固化四平台现状和完整 Probe 输出基线

**Files:**
- Create: `ai_arch/current-surface/neoforge-26.1.json`
- Create: `ai_arch/current-surface/neoforge-26.2.json`
- Create: `ai_arch/current-surface/neoforge-1.21.1.json`
- Create: `ai_arch/current-surface/cleanroom-1.12.2.json`
- Create: `ai_arch/unified-js-api-phase0-current-surface.md`
- Create: `common/src/main/java/com/tkisor/nekojs/probe/ProbeExternalArtifacts.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/probe/ProbeOrchestrator.java`
- Create: `common/src/test/java/com/tkisor/nekojs/probe/LegacyProbeCompatibilityTest.java`
- Create: `common/src/test/java/com/tkisor/nekojs/probe/LegacyProbeFixture.java`
- Create: `common/src/test/java/com/tkisor/nekojs/probe/LegacyProbeTreeTest.java`
- Create: `common/src/test/resources/nekojs/probe/legacy-bindings.expected.d.ts`
- Create: `common/src/test/resources/nekojs/probe/legacy-events.expected.d.ts`
- Create: `common/src/test/resources/nekojs/probe/legacy-tree/**`
- Modify: `common/src/test/java/com/tkisor/nekojs/probe/ClassDeclGeneratorTest.java`

**Interfaces:**
- Consumes: 当前 `BindingDeclarationGenerator`、`EventDeclarationGenerator`、`ClassDeclGenerator`。
- Produces: 提交 `b0856c7` 的四平台 current-surface 事实快照，以及覆盖全部 Probe 输出类别的 legacy golden tree。

- [ ] **Step 1: 记录四平台 current-surface JSON 和审计文档**

逐一读取四个平台 `NekoJSCorePlugin`、event group、adapter registration 和 recipe handler，写四个 deterministic JSON。每个文件固定字段：`sourceCommit`、`platform`、`globals`、`events`、`adapters`、`nativeTypeLeaks`、`fakeOrPartialBehaviors`、`targetTiers`。数组按 name 排序；`sourceCommit` 固定为 `b0856c7`。

审计文档汇总四份 JSON，必须引用具体源码路径，并明确“这是 preview current-surface dump，不是 API 1.0.0 normative contract”。26.1/26.2 即使当前相同也保留两个独立文件。

- [ ] **Step 2: 抽取 Probe 外部副作用以允许完整目录测试**

创建本计划 Task 10 所定义的 `ProbeExternalArtifacts`，给 `ProbeOrchestrator` 增加 default constructor 和 package-private injection constructor。此步骤只移动 `AgentTemplateGenerator.generate(outputDir.getParent().resolve(".github").resolve("agents"))` 与 `WorkspaceGenerator.createWorkspaceConfigs()` 调用，不改变任何 outputDir 内生成逻辑。

- [ ] **Step 3: 写 legacy binding/event golden 测试**

创建 fixture：

```java
package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyProbeCompatibilityTest {
    public static final class Helper {
        public static String of(String id) { return id; }
        public String getName() { return "helper"; }
    }

    public static final class SampleEvent {
        public String getMessage() { return "ok"; }
    }

    @Test
    void legacyBindingDeclarationMatchesGolden() throws Exception {
        var entry = BindingCatalogEntry.of("Helper", ScriptType.SERVER, Helper.class, true);
        String actual = new BindingDeclarationGenerator().generate(List.of(entry), ScriptType.SERVER);
        assertEquals(resource("legacy-bindings.expected.d.ts"), normalize(actual));
    }

    @Test
    void legacyEventDeclarationMatchesGolden() throws Exception {
        var aliases = new TypeAliasRegistry();
        var converter = new TypeConverter(aliases);
        var generator = new EventDeclarationGenerator(converter, new AdapterAliasGenerator(aliases));
        var event = EventCatalogEntry.of("ServerEvents", "sample", ScriptType.SERVER,
                SampleEvent.class, null, false, false);
        assertEquals(resource("legacy-events.expected.d.ts"), normalize(generator.generate(List.of(event), ScriptType.SERVER)));
    }

    private static String resource(String name) throws Exception {
        try (var in = LegacyProbeCompatibilityTest.class.getResourceAsStream("/nekojs/probe/" + name)) {
            if (in == null) throw new IllegalStateException("Missing golden " + name);
            return normalize(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n");
    }
}
```

- [ ] **Step 4: 创建完整 LegacyProbeFixture 和 tree test**

`LegacyProbeFixture.snapshot()` 构造一个当前形状的 `NekoScriptCatalogSnapshot`，必须至少包含：

- SERVER static binding 和 instance binding。
- cancellable event 和带 dispatch key event。
- 一个 AdapterCatalogEntry 和 input alias。
- 一个 recipe namespace/type。
- 一个 registry literal type。
- 一个 manual declaration。
- 一个 snippet。
- 默认 TypeOutputLayout。

`LegacyProbeTreeTest` 使用 `new ProbeOrchestrator(ProbeExternalArtifacts.NONE)` 生成到 `@TempDir`，递归读取全部相对路径和 UTF-8 bytes，与 `legacy-tree/` resource 完整比较。比较范围必须覆盖 `@package`、`@side-only`、recipe、bindings、events、`@special`、`@manual` 和 side root indexes。

- [ ] **Step 5: 运行测试并确认先失败**

Run: `.\gradlew.bat :common:test --tests "*LegacyProbeCompatibilityTest" --tests "*LegacyProbeTreeTest"`

Expected: FAIL，原因是 golden resources/tree 尚不存在。

- [ ] **Step 6: 从当前 generator 输出创建精确 golden 文件和 tree**

把失败 actual output 逐字写入两个 resource 和完整 tree。不得手工“改善”声明；Task 1 的目标是记录现状。

- [ ] **Step 7: 扩展 ClassDeclGenerator characterization**

在 `ClassDeclGeneratorTest` 增加 overload 和 extra-public-helper 基线：

```java
public static class OverloadedSample {
    public String find(String id) { return id; }
    public String find(int index) { return String.valueOf(index); }
    public String internalHelper() { return "legacy-visible"; }
}

@Test
void legacyGeneratorKeepsBothOverloads() {
    String decl = new ClassDeclGenerator(new TypeConverter(new TypeAliasRegistry()))
            .generate(OverloadedSample.class);
    assertEquals(2, count(decl, "find("), decl);
    assertTrue(decl.contains("internalHelper():"), decl);
}
```

添加本地 `count(String, String)` helper。

- [ ] **Step 8: 运行 Probe 与 common 全量测试**

Run: `.\gradlew.bat :common:test`

Expected: BUILD SUCCESSFUL；legacy golden、现有 111 项及新增测试全部通过。

- [ ] **Step 9: 提交**

```bash
git add ai_arch/current-surface ai_arch/unified-js-api-phase0-current-surface.md common/src/main/java/com/tkisor/nekojs/probe/ProbeExternalArtifacts.java common/src/main/java/com/tkisor/nekojs/probe/ProbeOrchestrator.java common/src/test/java/com/tkisor/nekojs/probe common/src/test/resources/nekojs/probe
git commit -m "test(probe): baseline current surfaces and legacy output"
```

---

### Task 2: 定义 canonical symbol、环境、capability 和 module 描述模型

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiTier.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiSymbolId.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiTypeRef.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiParameter.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiSignature.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiSymbol.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiSurfaceSnapshot.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiVersion.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiVersionRange.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/LoaderVersion.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/LoaderVersionRange.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/EnvironmentKey.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/EnvironmentScope.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/RuntimeDist.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiCallContext.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiCallback.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiCallHandler.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiRuntimeView.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiRuntimeProvider.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiContractHashes.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiRuntimeVersions.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiEnvironmentSnapshot.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/LegacyGlobalReservation.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/contract/ApiContractKind.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/contract/ApiContractIdentity.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/plugin/PluginIdentity.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/plugin/OwnedPlugin.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/capability/CapabilityDefinition.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/capability/CapabilityImplementationMode.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/capability/CapabilityProviderContribution.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/capability/ActiveCapability.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/module/ApiModuleDescriptor.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/module/ApiModuleDependency.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/module/ActiveModule.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/module/InactiveModule.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/module/InactiveReason.java`
- Test: `common/src/test/java/com/tkisor/nekojs/api/surface/ApiSurfaceModelTest.java`
- Test: `common/src/test/java/com/tkisor/nekojs/api/surface/ApiDescriptorModelTest.java`

**Interfaces:**
- Consumes: `ScriptType`。
- Produces: 后续 reader/resolver/manifest/Probe 所需的全部纯 API DTO；Task 3 不再引用未来任务中的类型。

- [ ] **Step 1: 写失败测试**

测试必须覆盖合法 ID、非法 ID、overload 去重键和不可变集合：

```java
@Test
void symbolIdRequiresKindAndQualifiedName() {
    assertEquals("global:Item", ApiSymbolId.parse("global:Item").value());
    assertEquals("module-member:@nekojs/feature/tags.add",
            ApiSymbolId.parse("module-member:@nekojs/feature/tags.add").value());
    assertThrows(IllegalArgumentException.class, () -> ApiSymbolId.parse("Item"));
    assertThrows(IllegalArgumentException.class, () -> ApiSymbolId.parse("global:"));
}

@Test
void signatureKeyPreservesOverloads() {
    ApiSignature byId = ApiSignature.function(List.of(
            new ApiParameter("id", ApiTypeRef.primitive("string"), false, false)),
            ApiTypeRef.primitive("string"));
    ApiSignature byIndex = ApiSignature.function(List.of(
            new ApiParameter("index", ApiTypeRef.primitive("number"), false, false)),
            ApiTypeRef.primitive("string"));
    assertNotEquals(byId.compatibilityKey(), byIndex.compatibilityKey());
}
```

- [ ] **Step 2: 运行测试确认缺类失败**

Run: `.\gradlew.bat :common:test --tests "*ApiSurfaceModelTest"`

Expected: compilation FAIL，模型类型不存在。

- [ ] **Step 3: 实现固定 tier 和 symbol ID**

`ApiTier` 必须精确包含七个枚举值。`ApiSymbolId` 构造器拒绝空白、缺少 `:`、空 kind、空 qualified name 和控制字符，并保留原始大小写。

- [ ] **Step 4: 实现结构化 type graph**

`ApiTypeRef` 使用非反射 DTO，并让 callback 直接携带完整 `ApiSignature`：

```java
public record ApiTypeRef(Kind kind, String name, List<ApiTypeRef> arguments,
                         ApiSignature callbackSignature) {
    public enum Kind { PRIMITIVE, SYMBOL, ARRAY, UNION, CALLBACK, VOID }

    public ApiTypeRef {
        arguments = List.copyOf(arguments == null ? List.of() : arguments);
    }

    public static ApiTypeRef primitive(String name) {
        return new ApiTypeRef(Kind.PRIMITIVE, requireName(name), List.of(), null);
    }
    public static ApiTypeRef symbol(ApiSymbolId id) {
        return new ApiTypeRef(Kind.SYMBOL, Objects.requireNonNull(id, "id").value(), List.of(), null);
    }
    public static ApiTypeRef array(ApiTypeRef element) {
        return new ApiTypeRef(Kind.ARRAY, null, List.of(Objects.requireNonNull(element, "element")), null);
    }
    public static ApiTypeRef union(List<ApiTypeRef> members) {
        List<ApiTypeRef> normalized = members.stream()
                .distinct()
                .sorted(Comparator.comparing(ApiTypeRef::compatibilityKey))
                .toList();
        if (normalized.size() < 2) throw new IllegalArgumentException("union requires at least two members");
        return new ApiTypeRef(Kind.UNION, null, normalized, null);
    }
    public static ApiTypeRef callback(ApiSignature signature) {
        return new ApiTypeRef(Kind.CALLBACK, null, List.of(), Objects.requireNonNull(signature, "signature"));
    }
}
```

union members 按 `compatibilityKey()` 排序和去重，确保 hash 稳定。

- [ ] **Step 5: 实现 signature 和 symbol**

`ApiSignature.callKey()` 只包含参数顺序、optional/varargs 和参数类型；`compatibilityKey()` 在 callKey 基础上增加返回类型。`ApiSymbol` 拒绝重复 callKey，因此“参数相同、仅返回类型不同”的两个签名是非法 JS overload；文档不进入任一 key。

member/module-member symbol ID 固定为不含参数的 JS 成员 ID，例如 `member:nekojs.api.PlayerRef.give`。所有 overload 都存放在该 symbol 的 `signatures` 中；参数类型只进入 callKey/compatibilityKey。

`ApiSurfaceSnapshot` 只包含 immutable symbols、active capability records、active/inactive module records 和 EnvironmentKey metadata；不得引用 `ApiInvoker`、`FrozenApiRegistry`、Class、Method 或 Graal 类型。

`ApiCallback` 的精确签名为 `Object call(List<Object> arguments) throws Exception`。`ApiCallHandler` 的精确签名为 `Object invoke(ApiCallContext context, Object receiver, List<Object> arguments) throws Exception`；二者都不引用 Graal。`ApiRuntimeView` 提供 `environmentSnapshot()`、`memberNames(typeId)` 和受控 `invoke(memberId, signatureKey, receiver, arguments)`；`ApiRuntimeProvider.view(EnvironmentKey)` 返回当前环境的 view。`ApiEnvironmentSnapshot` 由 `EnvironmentKey`、`ApiSurfaceSnapshot`、`ApiContractHashes` 三部分组成。`PluginIdentity`/`OwnedPlugin` 使用 Task 8 给出的精确 record 形状，因此 contribution registry 从首次编译起就使用 identity，而不是裸字符串。

`ApiRuntimeVersions` 精确字段为 `nekojsVersion`（String）、`apiVersion`、`spiVersion`、`runtimeContractVersion`、`catalogSchemaVersion`（正整数）。Phase 1 三个 contract version 都是 `0.0.0`，不得假装已冻结 1.0.0。

`ApiContractHashes` 精确字段为 `portableApiVersion`、`portableContractHash`、`Map<String,String> moduleContractHashes`；hash 值全部来自 VerifiedApiContract.compatibilitySha256。

`ApiContractKind` 固定为 PORTABLE、FEATURE、PLATFORM、ADDON、SPI；`ApiContractIdentity` 精确键为 `(owner, kind, contractId, version)`，允许同一 owner 拥有多个 module contracts。

- [ ] **Step 6: 实现版本、环境和 descriptor DTO**

`ApiVersion` 实现完整 SemVer 2.0（含 prerelease/build）；Normative contract validator 单独禁止 contractVersion 使用 prerelease。`ApiVersionRange` 支持 exact 和 `[min,max)`。

loader version 不使用 SemVer。`LoaderVersion` 解析 1 到 4 段非负整数和可选 `-qualifier`；比较时缺失数字段补 0、数字段逐段比较、同数字时 prerelease 低于 release、两个 qualifier 按 Unicode code-point 比较。它必须接受真实值 `26.1.2.36-beta`、`26.2.0.7-beta`、`0.5.14-alpha`。`LoaderVersionRange` 支持 exact 和 `[min,max)`。

`EnvironmentKey` 精确字段为 ScriptType、dist、loaderId、loaderVersionRaw、parsed LoaderVersion、minecraftVersion、`Map<String,String> installedMods`；raw loader/mod versions 参与 environment hash。`EnvironmentScope` 将 ScriptType、dist、requiredMods、allowedLoaderIds、LoaderVersionRange、minecraftVersionRange 组合为 `boolean matches(EnvironmentKey)`。`ApiDescriptorModelTest` 必须加入上述三个真实 loader version 的范围匹配断言。

`ApiModuleDescriptor` 构造器执行 tier discriminator：FEATURE/PLATFORM/ADDON 必须有 contractVersion；VERSION/UNSAFE_NATIVE 必须有正整数 moduleRevision。`InactiveReason` 固定包含 `SCOPE_MISMATCH`、`CAPABILITY_UNAVAILABLE`、`MISSING_MODULE_DEPENDENCY`、`DEPENDENCY_INACTIVE`、`MODULE_VERSION_MISMATCH`。

- [ ] **Step 7: 运行测试**

Run: `.\gradlew.bat :common:test --tests "*ApiSurfaceModelTest" --tests "*ApiDescriptorModelTest"`

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add common/src/main/java/com/tkisor/nekojs/api/surface common/src/main/java/com/tkisor/nekojs/api/capability common/src/main/java/com/tkisor/nekojs/api/module common/src/main/java/com/tkisor/nekojs/api/plugin/PluginIdentity.java common/src/main/java/com/tkisor/nekojs/api/plugin/OwnedPlugin.java common/src/test/java/com/tkisor/nekojs/api/surface
git commit -m "feat(api): add canonical JS symbol and type model"
```

---

### Task 3: 定义 NormativeApiContract schema 和 reader

**Files:**
- Modify: `common/build.gradle`
- Create: `common/src/main/resources/nekojs/api-contract/api-contract.schema.json`
- Create: `common/src/main/java/com/tkisor/nekojs/api/contract/NormativeApiContract.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/contract/ApiContractReader.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/contract/ApiContractViolation.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/contract/ApiContractException.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/contract/VerifiedApiContract.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/contract/VerifiedContractSet.java`
- Create: `common/src/test/resources/nekojs/api-contract/valid/minimal-portable.json`
- Create: `common/src/test/resources/nekojs/api-contract/invalid/version-with-contract-version.json`
- Create: `common/src/test/resources/nekojs/api-contract/invalid/addon-core-namespace.json`
- Test: `common/src/test/java/com/tkisor/nekojs/api/contract/ApiContractReaderTest.java`

**Interfaces:**
- Consumes: Task 2 surface model。
- Produces: `ApiContractReader.readVerified(Reader, URI codeSource, String resourceName, ApiContractIdentity expectedIdentity, String expectedIntegritySha256)` 和不可伪造的 `VerifiedApiContract`/`VerifiedContractSet`。

- [ ] **Step 1: 写 parser/validator 失败测试**

```java
@Test
void readsMinimalPortableContract() {
    VerifiedApiContract verified = read("valid/minimal-portable.json");
    NormativeApiContract contract = verified.contract();
    assertEquals("nekojs-core", contract.owner());
    assertEquals("1.0.0", contract.apiVersion());
    assertEquals(ApiTier.PORTABLE_STABLE, contract.symbols().getFirst().tier());
}

@Test
void rejectsVersionModuleWithContractVersion() {
    ApiContractException error = assertThrows(ApiContractException.class,
            () -> read("invalid/version-with-contract-version.json"));
    assertEquals("INVALID_MODULE_VERSION_DISCRIMINATOR", error.violation().code());
}

@Test
void rejectsAddonUsingNekojsNamespace() {
    ApiContractException error = assertThrows(ApiContractException.class,
            () -> read("invalid/addon-core-namespace.json"));
    assertEquals("RESERVED_MODULE_NAMESPACE", error.violation().code());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*ApiContractReaderTest"`

Expected: compilation FAIL。

- [ ] **Step 3: 编写 JSON Schema 2020-12**

schema 必须包含：

- 根字段 `schemaVersion`、`identity`、symbols、capabilities、modules。identity 必须含 owner、kind、contractId、version，并与 reader 的 expected identity 精确相等。
- `oneOf` 判别 FEATURE/PLATFORM/ADDON 的 `contractVersion` 与 VERSION/UNSAFE_NATIVE 的 `moduleRevision`。
- `additionalProperties: false`。
- `requiredCapabilities` 的 `id + contractVersionRange`。
- dependency 的四种 target-tier union。
- addon module `^@[a-z0-9_.-]+/[a-z0-9_./-]+$`；core module `^@nekojs/`。

contract 文件粒度固定：PORTABLE contract 不包含可导入 module，contractId 为 `portable-core`；FEATURE/PLATFORM/ADDON contract 必须且只能包含一个 module，identity.contractId == module.id 且 identity.version == module.contractVersion；SPI contract 不包含 JS modules/symbols。version/unsafe module 不属于本阶段 normative SemVer contract set。

- [ ] **Step 4: 实现 immutable DTO 与 reader**

在 `common/build.gradle` 增加 `api 'com.networknt:json-schema-validator:1.5.9'`。当前四个平台直接重编译 common source，同时 `implementation project(':common')`；使用 `api` 才会把 validator 发布到 consumer compile variant，保证平台编译 `ApiContractReader` 时可见。production reader 必须使用 networknt 的 JSON Schema 2020-12 validator 校验完整 schema，再转 DTO并执行跨字段语义校验；不得自行实现一个不完整 schema 子集。测试直接调用同一个 production reader，并额外断言 schema resource 可由 validator factory 加载。所有错误转换为 `ApiContractViolation(code, path, message)`，测试不得依赖 networknt/Gson 默认异常文本。Phase 2 仍需决定最终 artifact 的 bundling/jar-in-jar strategy。

同时实现 `ApiContractException extends IllegalArgumentException`，持有非 null `ApiContractViolation violation()`。`VerifiedApiContract` 的 constructor 设为 package-private，只能由 reader 在 identity、SHA-256、schema 和语义全部通过后创建；字段包含 identity、contract、codeSource URI、resourceName、integritySha256、compatibilitySha256。integrity hash 覆盖完整 canonical contract（包括 docs）并与 descriptor expected hash 比较；compatibility hash 使用排除 docs/examples 的兼容投影，供 API/module baseline 使用。`VerifiedContractSet` 按 `ApiContractIdentity` 建 immutable index，允许同 owner 多条，拒绝重复 identity，并提供 `forOwner(String)`。

`ApiContractReader.emptyVerifiedCorePreview(URI nekojsCodeSource)` 返回 identity `(nekojs-core, PORTABLE, portable-core, 0.0.0)`、空 symbols/modules/capabilities、preview=true envelope，只允许 Phase 1 production bootstrap 使用。`VerifiedContractSet.requirePortable("nekojs-core")` 必须恰好返回这一条 PORTABLE identity；零条或多条 fail-fast。

- [ ] **Step 5: 测试 schema 文件本身可解析且 fixture 全覆盖**

增加测试断言 `$schema == "https://json-schema.org/draft/2020-12/schema"`，并枚举 `invalid/` 下每个 fixture 都必须失败。

- [ ] **Step 6: 运行测试和 diff check**

Run: `.\gradlew.bat :common:test --tests "*ApiContractReaderTest"`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add common/build.gradle common/src/main/resources/nekojs/api-contract common/src/main/java/com/tkisor/nekojs/api/contract common/src/test/resources/nekojs/api-contract common/src/test/java/com/tkisor/nekojs/api/contract
git commit -m "feat(api): define normative API contract schema"
```

---

### Task 4: 实现 Environment、Capability 和 Module 确定性解析

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/api/capability/CapabilityResolution.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/capability/CapabilityResolver.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/module/ModuleResolution.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/module/ApiModuleResolver.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiResolutionException.java`
- Test: `common/src/test/java/com/tkisor/nekojs/api/capability/CapabilityResolverTest.java`
- Test: `common/src/test/java/com/tkisor/nekojs/api/module/ApiModuleResolverTest.java`

**Interfaces:**
- Consumes: Task 2/3 DTO。
- Produces: `CapabilityResolver.resolve(EnvironmentKey, definitions, providers)` 和 `ApiModuleResolver.resolve(EnvironmentKey, ApiVersion portableApiVersion, descriptors, capabilities)`。

- [ ] **Step 1: 写 capability 失败测试**

覆盖：无 provider=`UNAVAILABLE`、唯一 provider 激活、scope 越界、重复 eligible provider、addon 冒用 core capability、contractVersion 不匹配。

```java
@Test
void duplicateEligibleProvidersFail() {
    var error = assertThrows(ApiResolutionException.class,
            () -> resolver.resolve(serverEnv(), List.of(coreDefinition()),
                    List.of(provider("a"), provider("b"))));
    assertEquals("DUPLICATE_CAPABILITY_PROVIDER", error.code());
}
```

- [ ] **Step 2: 写 module DAG 失败测试**

必须覆盖依赖失活传递闭包和排序：

```java
@Test
void inactiveDependencyPropagatesThroughAllDependents() {
    ModuleResolution result = resolve(
            module("C").requiresCapability("missing", "[1.0.0,2.0.0)"),
            module("B").dependsOnFeature("C", "[1.0.0,2.0.0)"),
            module("A").dependsOnFeature("B", "[1.0.0,2.0.0)"));
    assertEquals(InactiveReason.CAPABILITY_UNAVAILABLE, result.inactive("C").reason());
    assertEquals(InactiveReason.DEPENDENCY_INACTIVE, result.inactive("B").reason());
    assertEquals(InactiveReason.DEPENDENCY_INACTIVE, result.inactive("A").reason());
}

@Test
void readyNodesUseCodePointOrder() {
    assertEquals(List.of("@x/a", "@x/z"), resolve(module("@x/z"), module("@x/a")).activeIds());
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*CapabilityResolverTest" --tests "*ApiModuleResolverTest"`

Expected: compilation FAIL。

- [ ] **Step 4: 实现 EnvironmentKey 和 scope predicate**

`EnvironmentKey` 构造器规范化 installed mods 为 sorted immutable set。scope containment 通过 `EnvironmentScope.matches(EnvironmentKey)` 和测试环境枚举判断，不对 requiredMods 做错误方向的简单 subset。

`ApiResolutionException extends IllegalStateException` 在本任务创建，至少提供 `String code()` 和 immutable `Map<String, String> details()`，capability/module resolver 与 Task 5 surface resolver 共用同一异常类型。

- [ ] **Step 5: 实现 capability resolver**

严格按设计 §8.2 的 12 步算法；provider services 必须覆盖 `requiredServiceKeys`；`CORE_ONLY` 和 `ALLOWLIST` 必须在选择前校验。

- [ ] **Step 6: 实现 module descriptor 判别和 DAG resolver**

dependency edges 使用 dependency -> dependent。先对完整 descriptor universe 做 cycle detection；再按 EnvironmentKey 和 capability 过滤；最后 dependency-first 计算 active/inactive。

`PORTABLE_STABLE` dependency 不进入 DAG，由 resolver 使用显式 `portableApiVersion` 参数检查 `apiVersionRange`。不得从任一 module contractVersion 推断 portable API version。

- [ ] **Step 7: 运行测试**

Run: `.\gradlew.bat :common:test --tests "*CapabilityResolverTest" --tests "*ApiModuleResolverTest"`

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add common/src/main/java/com/tkisor/nekojs/api/{surface,capability,module} common/src/test/java/com/tkisor/nekojs/api/{capability,module}
git commit -m "feat(api): resolve capabilities and modules deterministically"
```

---

### Task 5: 实现 JsApiSurfaceResolver 和 normative conformance

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiContribution.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/ApiContributionRegistry.java`
- Create: `common/src/main/java/com/tkisor/nekojs/core/api/ApiInvoker.java`
- Create: `common/src/main/java/com/tkisor/nekojs/core/api/JsApiSurfaceResolver.java`
- Create: `common/src/main/java/com/tkisor/nekojs/core/api/FrozenApiRegistry.java`
- Create: `common/src/main/java/com/tkisor/nekojs/core/api/FrozenApiRegistrySet.java`
- Test: `common/src/test/java/com/tkisor/nekojs/core/api/JsApiSurfaceResolverTest.java`

**Interfaces:**
- Consumes: Normative contract、EnvironmentKey、capability/module resolution。
- Produces: `FrozenApiRegistry implements ApiRuntimeView` 和 `FrozenApiRegistrySet implements ApiRuntimeProvider`，按 EnvironmentKey 冻结并缓存不同 surface。

- [ ] **Step 1: 写 conformance 失败测试**

覆盖：规范缺实现、core 未授权贡献、addon 未双向授权、duplicate ID、duplicate signature、overload 保留、version nativeReturn 合法、platform nativeReturn 非法。

```java
@Test
void keepsDistinctOverloadsUnderOneSymbol() {
    FrozenApiRegistry registry = resolve(contractWithFindOverloads(),
            contribution("member:type:Finder.find", stringSignature()),
            contribution("member:type:Finder.find", numberSignature()));
    assertEquals(2, registry.require(ApiSymbolId.parse("member:type:Finder.find")).signatures().size());
}

@Test
void rejectsRawReturnOutsideVersionTier() {
    ApiResolutionException error = assertThrows(ApiResolutionException.class,
            () -> resolve(platformContract(), nativeReturnContribution(ApiTier.PLATFORM)));
    assertEquals("NATIVE_TYPE_LEAK", error.code());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*JsApiSurfaceResolverTest"`

Expected: compilation FAIL。

- [ ] **Step 3: 实现 contribution registry**

`ApiContributionRegistry.ownedBy(PluginIdentity identity, VerifiedContractSet ownerContracts)` 返回 owner-bound view；先验证 set 中所有 contract identity.owner == identity.ownerId 且 identity.codeSource == contract.codeSource，integrity hashes 已由 reader 验证，注册时不允许调用者覆盖 owner。每个 symbol/provider contribution 再按 tier/module 找到唯一 matching contract identity。`ApiContribution` 把 metadata 与 `ApiInvoker` 分离，manifest DTO 不得持有 invoker。

`ApiContribution` 只携带 API 层 `ApiCallHandler`；core `ApiInvoker` 是 runtime adapter，不得从 `api.surface` import `core.api`。resolver 额外接收每个 EnvironmentKey 的 `List<LegacyGlobalReservation>`，managed global 与 legacy 保留名冲突时在创建 Context 前 fail-fast。

owner-bound registry 同时提供：

```java
void registerSymbol(ApiContribution contribution);
void registerCapabilityProvider(String capabilityId,
                                ApiVersion contractVersion,
                                CapabilityImplementationMode mode,
                                EnvironmentScope scope,
                                Map<String, Object> services);
```

第二个方法由 bound registry 填入 owner 并构造 `CapabilityProviderContribution`；plugin 不能传 owner。`FrozenApiRegistrySet` 把该集合传给 `CapabilityResolver`，不得另建绕过 verified contract 的 provider 通道。

- [ ] **Step 4: 实现 resolver**

顺序：contract validation -> owner/tier/namespace authorization -> capability/module resolution -> contribution 双向 conformance -> overload merge -> raw type leak validation -> deep immutable freeze。

- [ ] **Step 5: 实现 FrozenApiRegistry 查询**

至少提供：

```java
Optional<ApiSymbol> find(ApiSymbolId id);
ApiSymbol require(ApiSymbolId id);
Map<String, ApiSymbol> globals(ScriptType type);
Map<String, ApiSymbol> moduleExports(String moduleId, ScriptType type);
Set<String> memberNames(ApiSymbolId ownerType);
ApiInvoker invoker(ApiSymbolId memberId, String signatureKey);
ApiEnvironmentSnapshot environmentSnapshot();
```

`FrozenApiRegistrySet` 接收 `VerifiedContractSet`、贡献、legacy reservations 和 EnvironmentKey set，对每个 key 独立运行 capability/module/surface resolution；`view(EnvironmentKey)` 不得回退到其他 ScriptType。它用 `contracts.requirePortable("nekojs-core").identity().version()` 作为 `portableApiVersion` 传入 module resolver。它从 PORTABLE envelope 的 compatibilitySha256 构造 portableContractHash，并按 module ID 使用对应单-module envelope 的 compatibilitySha256 构造 moduleContractHashes；integritySha256 只用于 descriptor/resource 验证。每个 `ApiEnvironmentSnapshot` 携带该 verified summary；observed hash 仍由 Task 7 writer 计算。

- [ ] **Step 6: 运行测试**

Run: `.\gradlew.bat :common:test --tests "*JsApiSurfaceResolverTest"`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add common/src/main/java/com/tkisor/nekojs/api/surface common/src/main/java/com/tkisor/nekojs/core/api common/src/test/java/com/tkisor/nekojs/core/api
git commit -m "feat(api): resolve normative contracts into a frozen API registry"
```

---

### Task 6: 用 ProxyObject 强制 runtime surface

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/core/api/ApiRuntimeException.java`
- Create: `common/src/main/java/com/tkisor/nekojs/core/api/ApiValueMarshaller.java`
- Create: `common/src/main/java/com/tkisor/nekojs/core/api/ApiFacadeProxy.java`
- Test: `common/src/test/java/com/tkisor/nekojs/core/api/ApiFacadeProxyTest.java`

**Interfaces:**
- Consumes: FrozenApiRegistry、ApiInvoker、ApiTypeRef。
- Produces: `ApiFacadeProxy global(ApiRuntimeView, ApiSymbolId, Object)`、`ApiFacadeProxy value(ApiRuntimeView, ApiSymbolId, Object)`，稳定 error code。

- [ ] **Step 1: 写真实 Graal Context 失败测试**

fixture 实现类包含声明成员和额外 public helper：

```java
public static final class Implementation {
    public String declared(String value) { return value.toUpperCase(); }
    public String accidentalPublicHelper() { return "must stay hidden"; }
    public Object rawReturn() { return new Object(); }
}

@Test
void proxyExposesOnlyFrozenMembers() {
    try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
        context.getBindings("js").putMember("Stable", proxyForFixture());
        assertEquals("OK", context.eval("js", "Stable.declared('ok')").asString());
        assertFalse(context.eval("js", "'accidentalPublicHelper' in Stable").asBoolean());
    }
}

@Test
void rawReturnFailsAtBoundary() {
    ApiRuntimeException error = assertThrows(ApiRuntimeException.class,
            () -> invokeRawReturn(proxyForFixture()));
    assertEquals("NATIVE_TYPE_LEAK", error.code());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*ApiFacadeProxyTest"`

Expected: compilation FAIL。

- [ ] **Step 3: 实现结构化 runtime error**

`ApiRuntimeException` 必须携带 `code`、`symbolId`、`platform`、`minecraftVersion`、`requiredCapability`、`replacement`；允许 null 的 metadata 使用 `Optional` getter，不把 null 暴露给 JS proxy。

- [ ] **Step 4: 实现 marshaller**

参数按 signature 选择 overload；当 ApiTypeRef 为 CALLBACK 时，把可执行 Graal `Value` 包装成 API 层 `ApiCallback`。handler 调用 `ApiCallback.call(rawArgs)` 时，wrapper 先按 callback signature marshal payload，再调用 JS Value。返回值只允许 primitive、registered value proxy、registered Facade proxy、声明的 array/union/callback。VERSION export 只有 `nativeReturn=true` 时可透传 host object。

- [ ] **Step 5: 实现 ApiFacadeProxy**

`getMemberKeys()` 只返回 registry member names；`hasMember()` 同源；`getMember()` 返回常量值或 `ProxyExecutable`；禁止通过 reflection fallback 访问实现类。

- [ ] **Step 6: 增加 callback payload 测试**

invoker 向 JS callback 传入带额外 public helper 的实现对象，断言 callback 只看到 frozen payload members。

- [ ] **Step 7: 运行测试**

Run: `.\gradlew.bat :common:test --tests "*ApiFacadeProxyTest"`

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add common/src/main/java/com/tkisor/nekojs/core/api common/src/test/java/com/tkisor/nekojs/core/api/ApiFacadeProxyTest.java
git commit -m "feat(api): enforce frozen JS surfaces with registry-backed proxies"
```

---

### Task 7: 生成 canonical ApiManifest、hash 和 API diff

**Files:**
- Modify: `common/build.gradle`
- Create: `common/src/main/templates/nekojs/api-runtime.properties`
- Create: `common/src/main/java/com/tkisor/nekojs/api/manifest/ApiManifestBundle.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/manifest/ApiEnvironmentManifest.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/manifest/CanonicalJson.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/manifest/ApiManifestWriter.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/manifest/ApiCompatibilityDiff.java`
- Create: `common/src/main/java/com/tkisor/nekojs/core/api/ApiRuntimeVersionReader.java`
- Test: `common/src/test/java/com/tkisor/nekojs/api/manifest/ApiManifestWriterTest.java`
- Test: `common/src/test/java/com/tkisor/nekojs/api/manifest/ApiCompatibilityDiffTest.java`

**Interfaces:**
- Consumes: `ApiEnvironmentSnapshot`；其中 `ApiContractHashes` 由 VerifiedContractSet/resolver 生成，surface 不能自行制造 normative hash。
- Produces: `ApiManifestWriter.writeBundle(ApiRuntimeVersions, Map<ScriptType, ApiEnvironmentSnapshot>)`、canonical JSON、四类 hash、diff entries。

- [ ] **Step 1: 写 deterministic hash 失败测试**

同一 symbols/modules 以不同输入顺序构造两个 registry，断言 canonical JSON 和四类 hash 相同；改变文档不改变 compatibility hash；改变参数类型改变 surface hash。

- [ ] **Step 2: 写 API diff 失败测试**

覆盖：删除 stable=BREAKING，参数改型=BREAKING，新增 overload=ADDITIVE，文档变更=DOCUMENTATION_ONLY，VERSION 变化不进入 portable diff。

- [ ] **Step 3: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*ApiManifestWriterTest" --tests "*ApiCompatibilityDiffTest"`

Expected: compilation FAIL。

- [ ] **Step 4: 实现 canonical JSON**

固定 UTF-8、对象 key 字典序、symbols/exports/capabilities/modules 按 stable ID 排序；hash 输入排除 docs、时间戳、绝对路径和构建机信息。数字只允许 schema 中定义的整数，避免浮点 canonicalization 歧义。

唯一 wire shape：`ApiManifestBundle` 根字段为 `catalogSchemaVersion`、`nekojsVersion`、`apiVersion`、`spiVersion`、`runtimeContractVersion`、`portableContractHash`、`moduleContractHashes`、`environments`。`environments` key 是 ScriptType name；value `ApiEnvironmentManifest` 包含 EnvironmentKey、portableSurfaceHash、environmentSurfaceHash、capabilities、active/inactive modules、symbols。后续 Task 不得临时增加未进入 DTO 的字段。

`common/build.gradle` 从 `common/src/main/templates` 展开 `${mod_version}` 到 `api-runtime.properties`；api/spi/runtime contract version 固定 `0.0.0`，catalogSchemaVersion 固定 `1`。`ApiRuntimeVersionReader` 只读取该 resource，测试可注入显式 ApiRuntimeVersions。

`ApiManifestWriter.writeBundle` 在写出前断言 `versions.apiVersion == contracts portable identity.version`；不一致抛 `API_VERSION_MISMATCH`，不能选择其中任一个继续生成。

- [ ] **Step 5: 实现四类 hash**

不要复用一个含混 `coreSurfaceHash`。manifest JSON 必须显式输出字符串字段 `portableContractHash`、`portableSurfaceHash`、`environmentSurfaceHash` 和对象字段 `moduleContractHashes`；每个字符串值必须匹配正则 `^sha256:[0-9a-f]{64}$`。`portableContractHash` 和 module contract hashes 只能取自 reader 产生的 verified envelope summary；writer 只计算 observed portable/environment surface hash。

- [ ] **Step 6: 实现 normalized diff**

diff entry 结构：`severity`、`symbolId`、`changeKind`、`before`、`after`。replacement 文档变化不是 breaking；required capability 收紧是 breaking。

- [ ] **Step 7: 运行测试**

Run: `.\gradlew.bat :common:test --tests "*ApiManifestWriterTest" --tests "*ApiCompatibilityDiffTest"`

Expected: PASS。

- [ ] **Step 8: 提交**

```bash
git add common/build.gradle common/src/main/templates/nekojs/api-runtime.properties common/src/main/java/com/tkisor/nekojs/api/manifest common/src/main/java/com/tkisor/nekojs/core/api/ApiRuntimeVersionReader.java common/src/test/java/com/tkisor/nekojs/api/manifest
git commit -m "feat(api): emit canonical manifests and compatibility diffs"
```

---

### Task 8: 将 managed API contributions 接入 plugin bootstrap

**Files:**
- Modify: `common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/core/NekoJSBasePluginManager.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginBootstrap.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginRuntime.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/api/plugin/IPluginRuntime.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/surface/EnvironmentKeyFactory.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/platform/IPlatform.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/platform/Platform.java`
- Modify: `platforms/neoforge-26-shared/src/main/java/com/tkisor/nekojs/platform/NeoForgePlatform.java`
- Modify: `platforms/neoforge-1.21.1/src/main/java/com/tkisor/nekojs/platform/NeoForgePlatform.java`
- Modify: `platforms/cleanroom-1.12.2/src/main/java/com/tkisor/nekojs/platform/ForgePlatform.java`
- Modify: `platforms/neoforge-26-shared/src/main/java/com/tkisor/nekojs/NekoJSMod.java`
- Modify: `platforms/neoforge-1.21.1/src/main/java/com/tkisor/nekojs/NekoJSMod.java`
- Modify: `platforms/cleanroom-1.12.2/src/main/java/com/tkisor/nekojs/NekoJSMod.java`
- Test: `common/src/test/java/com/tkisor/nekojs/core/plugin/ApiSurfaceBootstrapTest.java`

**Interfaces:**
- Consumes: ApiContributionRegistry、JsApiSurfaceResolver。
- Produces: `NekoJSPlugin.registerApiSurface(ApiContributionRegistry)`、`OwnedPlugin` bootstrap、`IPluginRuntime.apiRuntime(EnvironmentKey)`。

- [ ] **Step 1: 写 owner-bound bootstrap 失败测试**

两个 test plugin 分别注册 symbol 和 capability provider，断言 owner 都来自 bootstrap 提供的 `PluginIdentity` 和 reader 产生的 `VerifiedApiContract`，而不是调用者参数；没有 verified contract envelope 的 legacy plugin 不会进入 managed collection。provider services 缺少 definition requiredServiceKey 时 bootstrap 必须 fail-fast。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*ApiSurfaceBootstrapTest"`

Expected: compilation FAIL。

- [ ] **Step 3: 添加 plugin hook**

```java
default void registerApiSurface(ApiContributionRegistry registry) {
}
```

不要把 owner-sensitive API 注册塞进现有只传 `(plugin, context)` 的 extension collector。`NekoPluginBootstrap.bootstrapOwned(List<OwnedPlugin>, ScriptPropertyRegistry, VerifiedContractSet)` 在普通 extension points 收集完成后单独遍历 `OwnedPlugin`。只有 `ownerContracts = contracts.forOwner(owned.identity().ownerId())` 非空时才调用：

```java
owned.plugin().registerApiSurface(
        state.apiContributions().ownedBy(owned.identity(), ownerContracts));
```

不能把未绑定 registry 直接传给 plugin。保留 `bootstrap(List<NekoJSPlugin>, ScriptPropertyRegistry)` 仅供旧测试兼容，它把每个实例映射为 `legacy:<class FQN>` 且没有 matching verified contract，不调用 managed hook。

`PluginIdentity` 定义为：

```java
public record PluginIdentity(String ownerId, String pluginClassName, URI codeSource) {
    public PluginIdentity {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId");
        if (pluginClassName == null || pluginClassName.isBlank()) throw new IllegalArgumentException("pluginClassName");
        Objects.requireNonNull(codeSource, "codeSource");
    }
}
```

`OwnedPlugin` 定义为：

```java
public record OwnedPlugin(PluginIdentity identity, NekoJSPlugin plugin) {
    public OwnedPlugin {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(plugin, "plugin");
    }
}
```

`NekoJSBasePluginManager.PluginEntry` 保存 identity。新增 `registerClass(PluginIdentity identity, Class<?> clazz)` 供后续平台 loader 传入已验证 mod owner；现有 `registerClass(Class<?>)` 暂时创建 `ownerId = "legacy:" + clazz.getName()`，codeSource 来自 protection domain，保持发现流程兼容。没有 matching `VerifiedApiContract` 的 identity 可以继续使用现有 legacy hooks，但 bootstrap 不调用其 managed hook。Phase 2 再把四个平台 loader 改为传入真实 mod owner；本计划不猜测 NeoForge/Cleanroom scan API。

manager 新增 `List<OwnedPlugin> getOwnedPlugins()` 并保留 `getPlugins()`。三个 `NekoJSMod` bootstrap 调用改为 `NekoPluginRuntime.bootstrapOwned(NekoJSBasePluginManager.getOwnedPlugins(), scriptProperties)`；这不会改变 plugin 排序。

`IPlatform` 增加 `String getLoaderId()` 和 `String getLoaderVersion()`；NeoForge 两份实现返回 loader ID `neoforge`，version 从 `getMods().get("neoforge")` 读取；Cleanroom 返回 ID `cleanroom`，version 优先从 `getMods().get("cleanroom")`、其次 `getMods().get("forge")` 读取，均缺失时返回 `0.0.0` 并记录 warning。`Platform` 增加对应静态转发。`EnvironmentKeyFactory.current(ScriptType)` 使用 client/dedicated dist、loader ID/version、MC 版本和 `Platform.getMods()` 的 `modId -> version` map 构造 key；不得使用 `getMcVersionInt()` 或只保留 mod ID 的 `Platform.getList()`。

- [ ] **Step 4: 在 bootstrap freeze 时解析 registry**

`NekoPluginRuntime.bootstrapOwned(List<OwnedPlugin>, ScriptPropertyRegistry)` 的生产默认调用 `NekoPluginBootstrap.bootstrapOwned(ownedPlugins, scriptProperties, VerifiedContractSet.of(ApiContractReader.emptyVerifiedCorePreview(NekoJS.class.getProtectionDomain().getCodeSource().getLocation().toURI())))`，并把当前 API 留在 LEGACY_PREVIEW adapter；当前 legacy owner 不匹配 `nekojs-core` envelope，因此返回每个 EnvironmentKey 都为空的 runtime provider，确保现有启动行为不变。测试调用三参数 bootstrap overload，传入与 fixture identity/contributions 精确匹配且 codeSource 相同的 verified contract set。后续领域计划将 loader-verified core/addon contract resources 加入 production set。

普通 binding/event extension points 已先完成收集。bootstrap 从 `bindingsByScriptType()` 和 frozen event groups 构造每个 EnvironmentKey 的 `LegacyGlobalReservation`：binding 使用 name + valueType FQN diagnostic；event group 使用 group name + event-group diagnostic。随后把 reservations 与 managed contributions 一起传给 `FrozenApiRegistrySet`。同名 managed global 必须在此处 fail-fast，不能等到 Context `putMember`。

- [ ] **Step 5: 发布 registry**

`NekoPluginRuntime` 增加 final `ApiRuntimeProvider apiRuntimeProvider`；`IPluginRuntime` 增加 API 层返回类型 `ApiRuntimeView apiRuntime(EnvironmentKey environment)`。构造器和测试 fixture 全部显式传入，禁止 nullable，也不得从 api package 引用 `FrozenApiRegistry`。

- [ ] **Step 6: 运行 plugin/bootstrap 全量测试**

Run: `.\gradlew.bat :common:test --tests "*plugin*" --tests "*Binding*"`

Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add common/src/main/java/com/tkisor/nekojs/api/NekoJSPlugin.java common/src/main/java/com/tkisor/nekojs/api/plugin common/src/main/java/com/tkisor/nekojs/api/surface/EnvironmentKeyFactory.java common/src/main/java/com/tkisor/nekojs/core/NekoJSBasePluginManager.java common/src/main/java/com/tkisor/nekojs/core/plugin common/src/main/java/com/tkisor/nekojs/platform platforms/neoforge-26-shared/src/main/java/com/tkisor/nekojs/NekoJSMod.java platforms/neoforge-26-shared/src/main/java/com/tkisor/nekojs/platform/NeoForgePlatform.java platforms/neoforge-1.21.1/src/main/java/com/tkisor/nekojs/NekoJSMod.java platforms/neoforge-1.21.1/src/main/java/com/tkisor/nekojs/platform/NeoForgePlatform.java platforms/cleanroom-1.12.2/src/main/java/com/tkisor/nekojs/NekoJSMod.java platforms/cleanroom-1.12.2/src/main/java/com/tkisor/nekojs/platform/ForgePlatform.java common/src/test/java/com/tkisor/nekojs/core/plugin/ApiSurfaceBootstrapTest.java
git commit -m "feat(plugin): collect and freeze managed API contributions"
```

---

### Task 9: 适配现有 catalog 为 LEGACY_PREVIEW current surface

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/api/catalog/LegacySurfaceAdapter.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/catalog/CurrentSurfaceReport.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/api/catalog/NekoScriptCatalogSnapshot.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/api/catalog/NekoScriptCatalog.java`
- Test: `common/src/test/java/com/tkisor/nekojs/api/catalog/LegacySurfaceAdapterTest.java`

**Interfaces:**
- Consumes: 现有 BindingCatalogEntry/EventCatalogEntry/AdapterCatalogEntry。
- Produces: `NekoScriptCatalogSnapshot.managedApis()`、`NekoScriptCatalogSnapshot.legacySurface()` 和 deterministic current-surface report。

- [ ] **Step 1: 写 legacy mapping 失败测试**

断言 binding `Item` 映射为 `global:Item` + `LEGACY_PREVIEW`，event 映射为 `event:Group.name`，原生 Java FQN 只作为 diagnostic metadata，不进入 managed type ref。

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*LegacySurfaceAdapterTest"`

Expected: compilation FAIL。

- [ ] **Step 3: 实现只读 adapter**

adapter 不得改变现有 catalog entries，也不得把 legacy symbol 当 normative stable。无法准确表达的参数使用 diagnostic `legacyJavaType`，而不是伪造 stable `ApiTypeRef`。

- [ ] **Step 4: 扩展 snapshot**

在 `NekoScriptCatalogSnapshot` 增加：

```java
Map<ScriptType, ApiEnvironmentSnapshot> managedApis,
List<ApiSymbol> legacySurface
```

`snapshot(IPluginRuntime)` 对 `ScriptType.all()` 分别调用 API 层 `EnvironmentKeyFactory.current(type)` 和 `runtime.apiRuntime(key).environmentSnapshot()`，构造 immutable map；单 ScriptType overload 只放一个 entry。更新所有测试 fixture。catalog API 层不得引用 core `FrozenApiRegistry` 或 core factory。

- [ ] **Step 5: 实现 current report**

输出 globals/events/adapters/hostExtensions 的 owner/tier/scriptTypes/FQN diagnostics，按 stable ID 排序。该报告是迁移审计，不是 normative contract。

- [ ] **Step 6: 运行 catalog/Probe 测试**

Run: `.\gradlew.bat :common:test --tests "*Catalog*" --tests "*Probe*"`

Expected: PASS；Task 1 golden 不变。

- [ ] **Step 7: 提交**

```bash
git add common/src/main/java/com/tkisor/nekojs/api/catalog common/src/test/java/com/tkisor/nekojs/api/catalog
git commit -m "feat(api): expose the current catalog as a legacy migration surface"
```

---

### Task 10: Probe 输出 managed declarations 和 api-manifest

**Files:**
- Create: `common/src/main/java/com/tkisor/nekojs/probe/ManagedApiDeclarationGenerator.java`
- Create: `common/src/main/java/com/tkisor/nekojs/probe/ApiManifestGenerator.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/probe/ProbeOrchestrator.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/script/WorkspaceGenerator.java`
- Test: `common/src/test/java/com/tkisor/nekojs/probe/ManagedApiDeclarationGeneratorTest.java`
- Test: `common/src/test/java/com/tkisor/nekojs/probe/ApiManifestGeneratorTest.java`
- Test: `common/src/test/java/com/tkisor/nekojs/probe/ProbeOutputCompatibilityTest.java`
- Test: `common/src/test/java/com/tkisor/nekojs/script/WorkspaceGeneratorManagedTypesTest.java`

**Interfaces:**
- Consumes: `NekoScriptCatalogSnapshot.managedApis()`（每 ScriptType 一个 ApiEnvironmentSnapshot）、ApiManifestWriter。
- Produces: 每个 ScriptType 的 managed global/type declarations、包含 environments map 的 `api-manifest.json`；legacy files 保持原路径和内容。本阶段不生成可导入 managed module 声明。

- [ ] **Step 1: 写 managed declaration 失败测试**

使用纯 canonical fixture，不使用 Java reflection：

```java
@Test
void generatesGlobalFromSurfaceWithoutNativeTypes() {
    String output = generator.generate(fixtureSnapshot(), ScriptType.SERVER);
    assertTrue(output.contains("declare global"), output);
    assertTrue(output.contains("const Stable: $StableFacade"), output);
    assertFalse(output.contains("net.minecraft"), output);
}
```

- [ ] **Step 2: 写 manifest staging 失败测试**

断言 writer 只写目标 staging directory，UTF-8，重复生成字节相同。

- [ ] **Step 3: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*ManagedApiDeclarationGeneratorTest" --tests "*ApiManifestGeneratorTest"`

Expected: compilation FAIL。

- [ ] **Step 4: 实现 managed declaration generator**

直接渲染 ApiTypeRef/ApiSignature；不调用 `ClassDeclGenerator`、`MemberVisibilityQuery` 或 raw reflection。Phase 1 只渲染 managed globals/types。若 unit fixture snapshot 中存在 active importable module，declaration generator 跳过 module declaration；不得生成 runtime 尚不能 import 的假 module 类型，也不得向 manifest DTO 临时增加状态字段。production Phase 1 使用的 empty preview contract 不含 module。

- [ ] **Step 5: 接入 ProbeOrchestrator**

使用 Task 1 已建立的 outputDir 外副作用接口：

```java
@FunctionalInterface
interface ProbeExternalArtifacts {
    void generate(Path outputDir) throws Exception;

    ProbeExternalArtifacts DEFAULT = outputDir -> {
        AgentTemplateGenerator.generate(outputDir.getParent().resolve(".github").resolve("agents"));
        WorkspaceGenerator.createWorkspaceConfigs();
    };

    ProbeExternalArtifacts NONE = outputDir -> {};
}
```

`ProbeOrchestrator()` 继续使用 `DEFAULT`；兼容测试使用 package-private 构造器和 `NONE`。随后在现有 legacy bindings/events/recipe/package 生成之后、staging commit 之前：

1. 按 ScriptType 输出 managed declarations。
2. `ApiManifestGenerator` 使用 `ApiRuntimeVersionReader.current()` 和 managedApis map 调用 Task 7 的 `writeBundle`，输出一个 `api-manifest.json`；测试 constructor 注入固定 ApiRuntimeVersions。
3. 输出 `current-surface.json`。

现有 legacy generator 调用顺序和文件内容不改。

把 `WorkspaceGenerator.createConfigForEnv` 的纯 model 构造部分提取为 package-private `buildConfigForEnv(ScriptType, Path scriptDir, Path probeDir)`，并在 include 中加入 `@nekojs/managed/<scriptType>/**/*.d.ts`。`WorkspaceGeneratorManagedTypesTest` 直接断言该 include；不为 feature/platform/version module 添加 paths，因为本阶段 runtime 尚未安装这些模块。

- [ ] **Step 6: 写 Probe output compatibility 测试**

对同一完整 legacy fixture tree 做比较：允许新增 `@nekojs/managed/<scriptType>/index.d.ts`、`api-manifest.json`、`current-surface.json`；Task 1 golden tree 中所有旧路径必须仍存在且字节相同。生成两次后递归文件 hash map 必须相同。

- [ ] **Step 7: 运行所有 Probe 测试**

Run: `.\gradlew.bat :common:test --tests "*probe*" --tests "*Probe*"`

Expected: PASS；manifest/managed 新测试通过，legacy golden 无变化。

- [ ] **Step 8: 提交**

```bash
git add common/src/main/java/com/tkisor/nekojs/probe common/src/main/java/com/tkisor/nekojs/script/WorkspaceGenerator.java common/src/test/java/com/tkisor/nekojs/probe common/src/test/java/com/tkisor/nekojs/script/WorkspaceGeneratorManagedTypesTest.java
git commit -m "feat(probe): emit managed API declarations and canonical manifests"
```

---

### Task 11: 让 runtime/preflight 使用 managed registry，legacy 保持 fallback

**Files:**
- Modify: `common/src/main/java/com/tkisor/nekojs/script/ScriptEnvironmentFactory.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginRuntime.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/api/event/ScriptBindingSchema.java`
- Create: `common/src/main/java/com/tkisor/nekojs/api/event/ManagedCallbackSchemaRegistry.java`
- Modify: `common/src/main/java/com/tkisor/nekojs/core/compiler/EventCallbackSourceValidator.java`
- Test: `common/src/test/java/com/tkisor/nekojs/script/ManagedApiEnvironmentTest.java`
- Test: `common/src/test/java/com/tkisor/nekojs/api/event/ManagedBindingSchemaTest.java`
- Test: `common/src/test/java/com/tkisor/nekojs/core/compiler/ManagedEventCallbackSourceValidatorTest.java`

**Interfaces:**
- Consumes: `IPluginRuntime.apiRuntime(EnvironmentKey)`、ApiFacadeProxy。
- Produces: managed globals 的真实 runtime proxy 和同源 preflight member names。

- [ ] **Step 1: 写 environment 失败测试**

构造一个 managed global + 一个 legacy binding：

- managed global 只能访问 registry 成员。
- legacy binding 仍按当前 HostAccess/JavaMemberIndex 行为工作。
- 同名 managed/legacy global 在 bootstrap 前必须被 resolver 判为冲突，不能靠 putMember 覆盖。

- [ ] **Step 2: 写 preflight 同源失败测试**

`ScriptBindingSchema` 对 managed global 的 members 必须精确等于 ApiSurfaceSnapshot 中对应 type symbol 的 member names；实现类 extra public helper 不得出现。另写 managed event callback fixture：payload type 只声明 `message`，实现类还有 `internalHelper`；validator 必须接受 `event.message` 并拒绝 `event.internalHelper`。

- [ ] **Step 3: 运行测试确认失败**

Run: `.\gradlew.bat :common:test --tests "*ManagedApiEnvironmentTest" --tests "*ManagedBindingSchemaTest"`

Expected: FAIL，当前 factory 只绑定 raw Java object/class。

- [ ] **Step 4: 修改 ScriptEnvironmentFactory**

绑定顺序：

1. event bridge legacy globals。
2. legacy plugin bindings（现状路径）。
3. 通过 `EnvironmentKeyFactory.current(scriptType)` 获取 key，再调用 `pluginRuntime.apiRuntime(key)`；managed globals 使用该 view 创建 `ApiFacadeProxy`。

name collision 已由 Task 8 bootstrap reservations 处理，factory 不再重复决定策略。managed binding schema 来自当前 EnvironmentKey 的 ApiRuntimeView；legacy 继续使用 `resolveMembers(binding)`，直到对应领域迁移。

- [ ] **Step 5: 扩展 ScriptBindingSchema API**

增加：

```java
public static BindingMembers fromSurface(ApiSurfaceSnapshot snapshot, ApiSymbolId typeId)
```

返回 immutable member set，不调用 JavaMemberIndex，也不引用 core `FrozenApiRegistry`。

`ManagedCallbackSchemaRegistry.register(ScriptType, ApiSurfaceSnapshot)` 从 managed event symbols 的 callback ApiTypeRef 中提取 payload symbol、display name 和 member set。`EventCallbackSourceValidator` 查询 managed registry：命中时直接使用 canonical member set/display name；未命中时才回退现有 `EventSchemaRegistry<Class<?>>` + JavaMemberIndex legacy 路径。

managed callback schema 是 plugin-runtime 级 immutable 数据，不属于 Context。`NekoPluginRuntime.bootstrapOwned` 在 `ApiRuntimeProvider` 完成后，为 ScriptType.all() 计算 EnvironmentKey 并一次性调用 `ManagedCallbackSchemaRegistry.install(Map<ScriptType, ApiSurfaceSnapshot>)`；registry 使用单个 immutable map 原子替换。reload/Environment close 不清理它，因此 candidate Context 与旧 Context 不会互相删除 schema。测试模拟事务顺序（install -> create candidate -> close old）并断言 schema 始终存在。

- [ ] **Step 6: 运行 managed、legacy 和 compiler 测试**

Run: `.\gradlew.bat :common:test --tests "*Managed*" --tests "*GlobalBindingMemberValidator*" --tests "*EventCallbackSourceValidator*"`

Expected: PASS。

- [ ] **Step 7: 运行 full common test**

Run: `.\gradlew.bat :common:test`

Expected: BUILD SUCCESSFUL，0 failures。

- [ ] **Step 8: 提交**

```bash
git add common/src/main/java/com/tkisor/nekojs/script/ScriptEnvironmentFactory.java common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginRuntime.java common/src/main/java/com/tkisor/nekojs/api/event/ScriptBindingSchema.java common/src/main/java/com/tkisor/nekojs/api/event/ManagedCallbackSchemaRegistry.java common/src/main/java/com/tkisor/nekojs/core/compiler/EventCallbackSourceValidator.java common/src/test/java/com/tkisor/nekojs/script common/src/test/java/com/tkisor/nekojs/api/event common/src/test/java/com/tkisor/nekojs/core/compiler/ManagedEventCallbackSourceValidatorTest.java
git commit -m "feat(runtime): bind managed APIs from the frozen surface registry"
```

---

### Task 12: 增加 pinned TypeScript Probe 门禁和 Phase 0/1 总验收

**Files:**
- Create: `package.json`
- Create: `package-lock.json`
- Create: `common/src/test/probe-ts/tsconfig.json`
- Create: `common/src/test/probe-ts/managed-api-usage.ts`
- Create: `common/src/test/probe-ts/generated/index.d.ts`
- Create: `common/src/test/java/com/tkisor/nekojs/probe/ProbeTypeScriptFixtureWriterTest.java`
- Modify: `.github/workflows/ci-build.yml`
- Modify: `ai_arch/unified-js-api-phase0-1-plan.md`（勾选已执行步骤时由执行者更新）

**Interfaces:**
- Consumes: Probe managed declaration output、api-manifest、current-surface report。
- Produces: 可重复 `npm run test:probe-types` 门禁和 Phase 0 current-surface 审计文档。

- [ ] **Step 1: 添加固定 TypeScript 工具链**

`package.json`：

```json
{
  "name": "nekojs-build-tools",
  "private": true,
  "scripts": {
    "test:probe-types": "tsc -p common/src/test/probe-ts/tsconfig.json --noEmit"
  },
  "devDependencies": {
    "typescript": "5.8.3"
  }
}
```

运行 `npm install --package-lock-only` 生成 lock；不得手写 lock。

- [ ] **Step 2: 添加 tsconfig 和使用 fixture**

`tsconfig.json` 固定：

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "noEmit": true,
    "skipLibCheck": false,
    "typeRoots": ["./generated"]
  },
  "include": ["managed-api-usage.ts", "generated/**/*.d.ts"]
}
```

usage fixture 必须调用 overloaded managed global 并验证 callback payload；不得 import Phase 6 才接线的 feature/platform/version module，不得使用 `any` 或 `@ts-ignore`。

- [ ] **Step 3: 添加 generated fixture writer JUnit test**

测试调用 `ManagedApiDeclarationGenerator`，把 actual 固定输出到 `common/build/probe-ts-actual/index.d.ts`，读取 checked-in `common/src/test/probe-ts/generated/index.d.ts` 并断言字节一致。正常 `:common:test` 不得写入 `src/` 或修改工作树。

- [ ] **Step 4: 先运行 tsc 并确认缺声明失败**

Run: `npm ci; if ($?) { npm run test:probe-types }`

Expected: 首次 FAIL，直到 generated golden 放入正确目录。

- [ ] **Step 5: 生成并审阅 managed declaration golden**

将 JUnit actual 输出复制为 checked-in `generated/index.d.ts`，确认无 `net.minecraft`、Forge、NeoForge、Graal FQN。

- [ ] **Step 6: 运行 TypeScript 门禁**

Run: `npm run test:probe-types`

Expected: 0 TypeScript errors。

- [ ] **Step 7: 把 TypeScript 检查接入 CI**

在 `.github/workflows/ci-build.yml` 的 JDK setup 后增加：

```yaml
      - name: Set up Node.js 22
        uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: npm

      - name: Install Probe typecheck dependencies
        run: npm ci
```

在 `Run common tests` 后增加：

```yaml
      - name: Typecheck Probe declarations
        run: npm run test:probe-types
```

- [ ] **Step 8: 运行完整验证矩阵**

Run:

```powershell
.\gradlew.bat :common:clean :common:test
npm ci
npm run test:probe-types
.\gradlew.bat :platforms:neoforge-26.1:compileJava :platforms:neoforge-26.2:compileJava :platforms:neoforge-1.21.1:compileJava :platforms:cleanroom-1.12.2:compileJava
```

Expected:

- `:common:test` BUILD SUCCESSFUL，0 failures。
- TypeScript 0 errors。
- 四个平台 compileJava 全部 BUILD SUCCESSFUL。
- Task 1 legacy Probe golden 完全不变。
- managed Probe output 与 manifest 重复生成字节一致。

- [ ] **Step 9: 检查 API/Probe 禁止项**

Run:

```powershell
git diff --check
git status --short
```

人工检查 staged diff：不得包含 `build/`、`node_modules/`、`.neko_probe/`、`.zcode/`、`kubejs-2601/` 或无关 `ai_arch/` 文件。

- [ ] **Step 10: 提交**

```bash
git add package.json package-lock.json common/src/test/probe-ts common/src/test/java/com/tkisor/nekojs/probe/ProbeTypeScriptFixtureWriterTest.java .github/workflows/ci-build.yml ai_arch/unified-js-api-phase0-1-plan.md
git commit -m "test(probe): enforce managed API declarations with TypeScript"
```

---

## Final Review Checklist

- [ ] `NormativeApiContract` 与 `ApiManifest` 职责未混淆。
- [ ] `ApiSymbol` 保留 overload，不按 JS 名折叠。
- [ ] managed runtime 成员集与 preflight/Probe 完全一致。
- [ ] implementation extra public helper 在 Graal 中不可见。
- [ ] raw return/callback payload 在非 VERSION/UNSAFE_NATIVE 边界被拒绝。
- [ ] capability provider 唯一、scope 不越界、addon 不冒用 core。
- [ ] module inactive 原因可诊断，依赖失活传递到全部 dependents。
- [ ] four hashes 使用明确且不同的输入集合。
- [ ] addon contract 缺失或不一致 fail-fast，不自动降级。
- [ ] legacy Probe golden 不变。
- [ ] managed Probe declaration 不含 MC/loader/Graal FQN。
- [ ] managed `.d.ts` 通过 TypeScript 5.8.3 strict typecheck。
- [ ] `:common:test` 与四平台 compileJava 全部通过。
- [ ] Phase 0 current-surface 审计已写入 `ai_arch/`，但未误标为 stable baseline。
