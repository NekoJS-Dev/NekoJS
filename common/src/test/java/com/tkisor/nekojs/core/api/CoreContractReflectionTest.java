package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link CoreManagedApiBootstrap#buildContract(URI)} 程序化反射产出的契约符号集。
 *
 * <p>替代旧的 {@code PortableCorePhase3AContractTest}（绑定已删除的 portable-core JSON）。
 * 断言反射覆盖全部域的关键符号、命名一致性（契约名 vs 类名 overrides）、以及类型引用闭环。
 */
class CoreContractReflectionTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-nekojs.jar");

    private static VerifiedApiContract buildContract() {
        return CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
    }

    private static Set<String> symbolIds() {
        return buildContract().contract().symbols().stream()
                .map(s -> s.id().value())
                .collect(Collectors.toSet());
    }

    @Test
    void contractIdentityIsPortableCore() {
        VerifiedApiContract contract = buildContract();
        ApiContractIdentity id = contract.identity();
        assertEquals("nekojs-core", id.owner());
        assertEquals(ApiContractKind.PORTABLE, id.kind());
        assertEquals("portable-core", id.contractId());
        // 版本来自 api-runtime.properties，不再硬编码
        assertNotNull(id.version());
    }

    @Test
    void contractHasNoEventsOrModules() {
        var contract = buildContract().contract();
        // events 由 EventContractReflector 运行时反射，契约不携带
        assertTrue(contract.events().isEmpty(), "events should be empty in static contract");
        assertTrue(contract.modules().isEmpty(), "PORTABLE contract has no modules");
    }

    @Test
    void allFacadeGlobalsPresent() {
        Set<String> ids = symbolIds();
        for (String global : List.of("global:ID", "global:Platform", "global:Text",
                "global:JsonIO", "global:NBT", "global:Registry", "global:Performance")) {
            assertTrue(ids.contains(global), "missing " + global);
        }
    }

    @Test
    void receiverDualOutputProducesDataTypeSymbols() {
        Set<String> ids = symbolIds();
        // 来自 facade receiver 双产出
        assertTrue(ids.contains("member:NekoId.namespace"), "NekoId.namespace via IdFacade receiver-dual");
        assertTrue(ids.contains("member:NekoId.path"), "NekoId.path via IdFacade receiver-dual");
        assertTrue(ids.contains("member:NekoId.asString"), "NekoId.asString via IdFacade receiver-dual");
        assertTrue(ids.contains("member:JsonValue.toString"), "JsonValue.toString via JsonFacade receiver-dual");
        assertTrue(ids.contains("member:JsonValue.toPrettyString"), "JsonValue.toPrettyString via JsonFacade receiver-dual");
        assertTrue(ids.contains("member:NbtValue.kind"), "NbtValue.kind via NbtFacade receiver-dual");
        assertTrue(ids.contains("member:NbtValue.toSnbt"), "NbtValue.toSnbt via NbtFacade receiver-dual");
        // TextValue 样式方法（receiver 双产出，不含 isEmpty——isEmpty 是 TextValue 自身方法）
        assertTrue(ids.contains("member:TextValue.bold"), "TextValue.bold via TextFacade receiver-dual");
        assertTrue(ids.contains("member:TextValue.color"), "TextValue.color via TextFacade receiver-dual");
        assertTrue(ids.contains("member:TextValue.black"), "TextValue.black via TextFacade receiver-dual");
        assertTrue(ids.contains("member:TextValue.append"), "TextValue.append via TextFacade receiver-dual");
    }

    @Test
    void nbtScalarRemapApplied() {
        Set<String> ids = symbolIds();
        // @Remap 把 byteValue→byte 等（Java 关键字冲突）
        assertTrue(ids.contains("member:NBT.byte"), "NBT.byte via @Remap");
        assertTrue(ids.contains("member:NBT.short"), "NBT.short via @Remap");
        assertTrue(ids.contains("member:NBT.int"), "NBT.int via @Remap");
        assertTrue(ids.contains("member:NBT.long"), "NBT.long via @Remap");
        assertTrue(ids.contains("member:NBT.float"), "NBT.float via @Remap");
        assertTrue(ids.contains("member:NBT.double"), "NBT.double via @Remap");
        assertFalse(ids.contains("member:NBT.byteValue"), "byteValue should be remapped to byte");
    }

    @Test
    void reflectDataTypeCoversOwnMethodsAndRecordAccessors() {
        Set<String> ids = symbolIds();
        // TextValue 自身实例方法（receiver 双产出覆盖不到）
        assertTrue(ids.contains("member:TextValue.isEmpty"), "TextValue.isEmpty via reflectDataType");
        // RegistryView 自身方法
        assertTrue(ids.contains("member:RegistryView.exists"), "RegistryView.exists via reflectDataType");
        assertTrue(ids.contains("member:RegistryView.all"), "RegistryView.all via reflectDataType");
        assertTrue(ids.contains("member:RegistryView.dataMapIds"), "RegistryView.dataMapIds via reflectDataType");
        // NbtEntry record 访问器
        assertTrue(ids.contains("member:NbtEntry.key"), "NbtEntry.key via reflectDataType (record component)");
        assertTrue(ids.contains("member:NbtEntry.value"), "NbtEntry.value via reflectDataType (record component)");
        // ModInfo record 访问器（契约名 ModInfo，类名 ModInfoValue）
        assertTrue(ids.contains("member:ModInfo.id"), "ModInfo.id via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:ModInfo.name"), "ModInfo.name via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:ModInfo.version"), "ModInfo.version via reflectDataType (contract name override)");
        // PerfTimer 实例方法（契约名 PerfTimer，类名 PerfTimerValue）
        assertTrue(ids.contains("member:PerfTimer.mark"), "PerfTimer.mark via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:PerfTimer.end"), "PerfTimer.end via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:PerfTimer.report"), "PerfTimer.report via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:PerfTimer.elapsedMillis"), "PerfTimer.elapsedMillis via reflectDataType (contract name override)");
    }

    @Test
    void nbtCompoundSymbolReflectedAfterFacadeAddition() {
        Set<String> ids = symbolIds();
        // NbtFacade.compound() 现在存在，反射自动产出
        assertTrue(ids.contains("member:NBT.compound"), "NBT.compound via NbtFacade reflection");
    }

    @Test
    void scriptEventRegistrationSymbolsReflected() {
        Set<String> ids = symbolIds();
        assertTrue(ids.contains("member:ScriptEventRegistrationEvent.targetType"),
                "ScriptEventRegistrationEvent.targetType via reflectEventRegistrationSymbols");
        assertTrue(ids.contains("member:ScriptEventRegistrationEvent.register"),
                "ScriptEventRegistrationEvent.register via reflectEventRegistrationSymbols");
    }

    @Test
    void contractNameOverridesPropagateToTypeReferences() {
        // PlatformFacade.getInfo 返回 ModInfoValue → UNION(type:ModInfo, null)
        // （契约名 ModInfo，非类名 ModInfoValue；nullable 因失败返回 null）
        ApiSymbol getInfo = findSymbol("member:Platform.getInfo");
        assertNotNull(getInfo);
        ApiTypeRef getInfoReturn = getInfo.signatures().getFirst().returnType();
        assertEquals(ApiTypeRef.Kind.UNION, getInfoReturn.kind());
        assertTrue(containsSymbolRef(getInfoReturn, "type:ModInfo"),
                "Platform.getInfo return UNION must contain type:ModInfo, got " + getInfoReturn.name());

        // PerformanceFacade.start 返回 PerfTimerValue → UNION(type:PerfTimer, null)
        ApiSymbol start = findSymbol("member:Performance.start");
        assertNotNull(start);
        ApiTypeRef startReturn = start.signatures().getFirst().returnType();
        assertTrue(containsSymbolRef(startReturn, "type:PerfTimer"),
                "Performance.start return must contain type:PerfTimer");
    }

    @Test
    void jsonValueAndNbtValueReturnAsSymbolButParamsAsPrimitive() {
        // JsonIO.parse 返回 JsonValue → UNION(type:JsonValue, null)
        // （被 proxy 包裹，可继续 .toString()；nullable 因失败返回 null）
        ApiSymbol parse = findSymbol("member:JsonIO.parse");
        assertNotNull(parse);
        ApiTypeRef parseReturn = parse.signatures().getFirst().returnType();
        assertTrue(containsSymbolRef(parseReturn, "type:JsonValue"),
                "JsonIO.parse return must contain type:JsonValue");

        // JsonIO.toString 参数 JsonValue → PRIMITIVE json（值透传）
        ApiSymbol toString = findSymbol("member:JsonIO.toString");
        assertNotNull(toString);
        assertEquals("json", toString.signatures().getFirst().parameters().getFirst().type().name(),
                "JsonIO.toString param must be PRIMITIVE json");
    }

    /** 递归检查 ApiTypeRef（可能是 UNION/SYMBOL）是否含指定 symbol 引用名。 */
    private static boolean containsSymbolRef(ApiTypeRef type, String symbolName) {
        if (type.kind() == ApiTypeRef.Kind.SYMBOL && symbolName.equals(type.name())) return true;
        return type.arguments().stream().anyMatch(a -> containsSymbolRef(a, symbolName));
    }

    @Test
    void symbolCountReasonable() {
        // 原 JSON 110 个，反射产出应 ≥ 110（允许更完整，如 PerfTimerValue 的 label/marks/ended 额外产出）
        int count = buildContract().contract().symbols().size();
        assertTrue(count >= 110, "reflected symbol count should be >= 110 (original JSON), got " + count);
    }

    @Test
    void contractSetWrapsIntoVerifiedContractSet() {
        VerifiedApiContract contract = buildContract();
        VerifiedContractSet set = VerifiedContractSet.of(contract);
        assertEquals(1, set.all().size());
        assertSame(contract, set.all().getFirst());
    }

    private ApiSymbol findSymbol(String id) {
        return buildContract().contract().symbols().stream()
                .filter(s -> s.id().value().equals(id))
                .findFirst()
                .orElse(null);
    }
}
