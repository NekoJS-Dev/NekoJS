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
 * Checks the contract symbols that {@link CoreManagedApiBootstrap#buildContract(URI)}
 * produces by reflection.
 *
 * <p>Assertions cover the key symbols of every domain, name consistency (contract names
 * vs class-name overrides), and that type references resolve.
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
        // version comes from api-runtime.properties, not a hardcoded value
        assertNotNull(id.version());
    }

    @Test
    void contractHasNoEventsOrModules() {
        var contract = buildContract().contract();
        // events are reflected at runtime by EventContractReflector; the contract does not carry them
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
        // produced from the facade receivers, which emit both the facade and the data-type member
        assertTrue(ids.contains("member:NekoId.namespace"), "NekoId.namespace via IdFacade receiver-dual");
        assertTrue(ids.contains("member:NekoId.path"), "NekoId.path via IdFacade receiver-dual");
        assertTrue(ids.contains("member:NekoId.asString"), "NekoId.asString via IdFacade receiver-dual");
        assertTrue(ids.contains("member:JsonValue.toString"), "JsonValue.toString via JsonFacade receiver-dual");
        assertTrue(ids.contains("member:JsonValue.toPrettyString"), "JsonValue.toPrettyString via JsonFacade receiver-dual");
        assertTrue(ids.contains("member:NbtValue.kind"), "NbtValue.kind via NbtFacade receiver-dual");
        assertTrue(ids.contains("member:NbtValue.toSnbt"), "NbtValue.toSnbt via NbtFacade receiver-dual");
        // TextValue style methods (the receiver produces both members; isEmpty is not
        // one of them, it is a TextValue method of its own)
        assertTrue(ids.contains("member:TextValue.bold"), "TextValue.bold via TextFacade receiver-dual");
        assertTrue(ids.contains("member:TextValue.color"), "TextValue.color via TextFacade receiver-dual");
        assertTrue(ids.contains("member:TextValue.black"), "TextValue.black via TextFacade receiver-dual");
        assertTrue(ids.contains("member:TextValue.append"), "TextValue.append via TextFacade receiver-dual");
    }

    @Test
    void nbtScalarRemapApplied() {
        Set<String> ids = symbolIds();
        // @Remap maps byteValue→byte and so on (Java keyword conflicts)
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
        // TextValue's own instance methods (the receivers' dual output does not cover them)
        assertTrue(ids.contains("member:TextValue.isEmpty"), "TextValue.isEmpty via reflectDataType");
        // RegistryView's own methods
        assertTrue(ids.contains("member:RegistryView.exists"), "RegistryView.exists via reflectDataType");
        assertTrue(ids.contains("member:RegistryView.all"), "RegistryView.all via reflectDataType");
        assertTrue(ids.contains("member:RegistryView.dataMapIds"), "RegistryView.dataMapIds via reflectDataType");
        // NbtEntry record accessors
        assertTrue(ids.contains("member:NbtEntry.key"), "NbtEntry.key via reflectDataType (record component)");
        assertTrue(ids.contains("member:NbtEntry.value"), "NbtEntry.value via reflectDataType (record component)");
        // ModInfo record accessors (contract name ModInfo, class name ModInfoValue)
        assertTrue(ids.contains("member:ModInfo.id"), "ModInfo.id via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:ModInfo.name"), "ModInfo.name via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:ModInfo.version"), "ModInfo.version via reflectDataType (contract name override)");
        // PerfTimer instance methods (contract name PerfTimer, class name PerfTimerValue)
        assertTrue(ids.contains("member:PerfTimer.mark"), "PerfTimer.mark via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:PerfTimer.end"), "PerfTimer.end via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:PerfTimer.report"), "PerfTimer.report via reflectDataType (contract name override)");
        assertTrue(ids.contains("member:PerfTimer.elapsedMillis"), "PerfTimer.elapsedMillis via reflectDataType (contract name override)");
    }

    @Test
    void nbtCompoundSymbolReflectedAfterFacadeAddition() {
        Set<String> ids = symbolIds();
        // NbtFacade.compound() exists, so reflection emits it automatically
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
        // PlatformFacade.getInfo returns ModInfoValue -> UNION(type:ModInfo, null)
        // (contract name ModInfo, not the class name ModInfoValue; nullable because it returns null on failure)
        ApiSymbol getInfo = findSymbol("member:Platform.getInfo");
        assertNotNull(getInfo);
        ApiTypeRef getInfoReturn = getInfo.signatures().getFirst().returnType();
        assertEquals(ApiTypeRef.Kind.UNION, getInfoReturn.kind());
        assertTrue(containsSymbolRef(getInfoReturn, "type:ModInfo"),
                "Platform.getInfo return UNION must contain type:ModInfo, got " + getInfoReturn.name());

        // PerformanceFacade.start returns PerfTimerValue -> UNION(type:PerfTimer, null)
        ApiSymbol start = findSymbol("member:Performance.start");
        assertNotNull(start);
        ApiTypeRef startReturn = start.signatures().getFirst().returnType();
        assertTrue(containsSymbolRef(startReturn, "type:PerfTimer"),
                "Performance.start return must contain type:PerfTimer");
    }

    @Test
    void jsonValueAndNbtValueReturnAsSymbolButParamsAsPrimitive() {
        // JsonIO.parse returns JsonValue -> UNION(type:JsonValue, null)
        // (wrapped in a proxy, so .toString() still works; nullable because it returns null on failure)
        ApiSymbol parse = findSymbol("member:JsonIO.parse");
        assertNotNull(parse);
        ApiTypeRef parseReturn = parse.signatures().getFirst().returnType();
        assertTrue(containsSymbolRef(parseReturn, "type:JsonValue"),
                "JsonIO.parse return must contain type:JsonValue");

        // JsonIO.toString takes JsonValue -> PRIMITIVE json (the value passes through)
        ApiSymbol toString = findSymbol("member:JsonIO.toString");
        assertNotNull(toString);
        assertEquals("json", toString.signatures().getFirst().parameters().getFirst().type().name(),
                "JsonIO.toString param must be PRIMITIVE json");
    }

    /** Recursively checks whether an ApiTypeRef (possibly UNION or SYMBOL) contains the given symbol name. */
    private static boolean containsSymbolRef(ApiTypeRef type, String symbolName) {
        if (type.kind() == ApiTypeRef.Kind.SYMBOL && symbolName.equals(type.name())) return true;
        return type.arguments().stream().anyMatch(a -> containsSymbolRef(a, symbolName));
    }

    @Test
    void symbolCountReasonable() {
        // The original JSON lists 110 symbols; reflection must produce at least 110. Extra symbols are
        // fine, for example the label, marks and ended members that PerfTimerValue adds.
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
