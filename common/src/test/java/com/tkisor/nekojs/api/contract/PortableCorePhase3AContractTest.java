package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableCorePhase3AContractTest {
    private static final String RESOURCE = "/nekojs/api-contract/portable-core-0.12.0.json";

    @Test
    void productionReaderAcceptsPlatformAndIdContract() {
        var stream = PortableCorePhase3AContractTest.class.getResourceAsStream(RESOURCE);
        assertNotNull(stream);

        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.12.0"));
        VerifiedApiContract verified = ApiContractReader.readVerified(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                URI.create("nekojs:///core"), RESOURCE, identity, null);

        Map<ApiSymbolId, ApiSymbol> symbols = verified.contract().symbols().stream()
                .collect(Collectors.toMap(ApiSymbol::id, Function.identity()));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("global:ID")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("global:Platform")));
        assertEquals(2, symbols.get(ApiSymbolId.parse("member:ID.of")).signatures().size());
        assertFalse(symbols.containsKey(ApiSymbolId.parse("member:ID.platform")));
        assertFalse(symbols.containsKey(ApiSymbolId.parse("member:Platform.getGameDir")));
        assertFalse(symbols.containsKey(ApiSymbolId.parse("member:Platform.getMods")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("global:Text")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:TextValue.append")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("global:JsonIO")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:JsonValue.toPrettyString")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:JsonIO.read")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:JsonIO.write")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("global:NBT")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:NbtValue.toSnbt")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:NBT.read")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:NBT.write")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("global:Registry")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:Registry.get")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:RegistryView.all")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:RegistryView.dataMapIds")));
        assertTrue(symbols.containsKey(ApiSymbolId.parse("member:RegistryView.dataMapValue")));
        assertEquals(Set.of("NO_MATCHING_SIGNATURE", "TYPE_MISMATCH"),
                symbols.get(ApiSymbolId.parse("member:Text.ofValues")).signatures().getFirst()
                        .errorCodes().stream().collect(Collectors.toSet()));

        Set<String> errorCodes = verified.contract().errors().stream()
                .map(NormativeApiContract.ContractError::code)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                ApiErrorCodes.UNSUPPORTED_CAPABILITY,
                ApiErrorCodes.UNSUPPORTED_MODULE,
                ApiErrorCodes.INVALID_REFERENCE,
                ApiErrorCodes.API_CONTRACT_VIOLATION,
                ApiErrorCodes.DUPLICATE_API_SYMBOL,
                ApiErrorCodes.DUPLICATE_CAPABILITY_PROVIDER,
                ApiErrorCodes.NATIVE_TYPE_LEAK,
                ApiErrorCodes.STALE_API_MANIFEST,
                ApiErrorCodes.NO_MATCHING_SIGNATURE,
                ApiErrorCodes.AMBIGUOUS_CALL,
                ApiErrorCodes.TYPE_MISMATCH,
                ApiErrorCodes.CALLBACK_NOT_EXECUTABLE,
                ApiErrorCodes.INVOCATION_ERROR,
                ApiErrorCodes.INVALID_JSON,
                ApiErrorCodes.JSON_LIMIT_EXCEEDED,
                ApiErrorCodes.JSON_PATH_FORBIDDEN,
                ApiErrorCodes.JSON_FILE_TOO_LARGE,
                ApiErrorCodes.JSON_IO_ERROR,
                ApiErrorCodes.JSON_ATOMIC_WRITE_FAILED,
                ApiErrorCodes.INVALID_NBT,
                ApiErrorCodes.NBT_LIMIT_EXCEEDED,
                ApiErrorCodes.NBT_PATH_FORBIDDEN,
                ApiErrorCodes.NBT_FILE_TOO_LARGE,
                ApiErrorCodes.NBT_IO_ERROR,
                ApiErrorCodes.NBT_ATOMIC_WRITE_FAILED), errorCodes);
        assertEquals(Set.of("symbolId", "platform", "minecraftVersion"),
                verified.contract().errors().stream()
                        .filter(error -> ApiErrorCodes.INVALID_REFERENCE.equals(error.code()))
                        .findFirst().orElseThrow().fields().stream().collect(Collectors.toSet()));
        assertTrue(verified.contract().errors().stream().allMatch(error ->
                error.fields().containsAll(Set.of("symbolId", "platform", "minecraftVersion"))));

        assertEvents(verified.contract().events());
    }

    /**
     * events 段已从 portable-core JSON 移除——事件契约现由 EventContractReflector
     * 从运行时 EventGroup 反射派生。JSON 解析应得到空 events 列表。
     */
    private static void assertEvents(List<NormativeApiContract.ContractEvent> events) {
        assertTrue(events.isEmpty(),
                "events should be empty (removed from JSON, now derived via EventContractReflector)");
    }
}
