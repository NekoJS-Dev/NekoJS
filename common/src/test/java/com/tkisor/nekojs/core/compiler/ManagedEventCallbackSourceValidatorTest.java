package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ManagedCallbackSchemaRegistry;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ManagedEventCallbackSourceValidatorTest {

    @BeforeEach
    void setUp() {
        ScriptBindingSchema.clearAll();
        ManagedCallbackSchemaRegistry.clear();
        TestPlatformInit.ensureInitialized();
    }

    private static EnvironmentKey serverEnv() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "neoforge",
                "21.1.0",
                LoaderVersion.parse("21.1.0"),
                "1.21.1",
                Map.of());
    }

    private static ApiSurfaceSnapshot buildManagedSnapshot() {
        ApiSymbolId payloadId = ApiSymbolId.parse("type:Payload");
        ApiSymbolId payloadMessageId = ApiSymbolId.parse("member:Payload.message");
        ApiSymbolId payloadInternalId = ApiSymbolId.parse("member:Payload.internalHelper");

        ApiSignature payloadMessageSig = ApiSignature.function(List.of(), ApiTypeRef.primitive("string"));
        ApiSignature payloadInternalSig = ApiSignature.function(List.of(), ApiTypeRef.primitive("string"));

        ApiSignature callbackSig = ApiSignature.function(
                List.of(new ApiParameter("event", ApiTypeRef.symbol(payloadId), false, false)),
                ApiTypeRef.voidType());

        ApiSymbolId eventMemberId = ApiSymbolId.parse("member:Events.onMessage");
        ApiSymbol eventSymbol = new ApiSymbol(eventMemberId, List.of(
                ApiSignature.function(
                        List.of(new ApiParameter("cb", ApiTypeRef.callback(callbackSig), false, false)),
                        ApiTypeRef.voidType())));
        ApiSymbol globalSymbol = new ApiSymbol(ApiSymbolId.parse("global:Events"), List.of(
                ApiSignature.function(
                        List.of(new ApiParameter("cb", ApiTypeRef.callback(callbackSig), false, false)),
                        ApiTypeRef.voidType())));
        ApiSymbol payloadSymbol = new ApiSymbol(payloadId, List.of(payloadMessageSig));
        ApiSymbol payloadMessageSymbol = new ApiSymbol(payloadMessageId, List.of(payloadMessageSig));
        ApiSymbol payloadInternalSymbol = new ApiSymbol(payloadInternalId, List.of(payloadInternalSig));

        return new ApiSurfaceSnapshot(
                List.of(globalSymbol, eventSymbol, payloadSymbol, payloadMessageSymbol, payloadInternalSymbol),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());
    }

    @Test
    void managedValidatorAcceptsKnownMember() {
        ApiSurfaceSnapshot snapshot = buildManagedSnapshot();
        ManagedCallbackSchemaRegistry.install(Map.of(ScriptType.SERVER, snapshot));

        ScriptBindingSchema.BindingMembers eventsMembers = ScriptBindingSchema.fromSurface(
                snapshot, ApiSymbolId.parse("global:Events"));
        ScriptBindingSchema.register(ScriptType.SERVER, Map.of("Events", eventsMembers));

        ScriptType type = ScriptType.SERVER;
        Path filePath = type.path.resolve("test.js");
        String source = "Events.onMessage((event) => { event.message })";

        assertDoesNotThrow(() -> EventCallbackSourceValidator.validate(filePath, source));
    }

    @Test
    void managedValidatorRejectsUnknownMember() {
        ApiSurfaceSnapshot snapshot = buildManagedSnapshot();
        ManagedCallbackSchemaRegistry.install(Map.of(ScriptType.SERVER, snapshot));

        ScriptBindingSchema.BindingMembers eventsMembers = ScriptBindingSchema.fromSurface(
                snapshot, ApiSymbolId.parse("global:Events"));
        ScriptBindingSchema.register(ScriptType.SERVER, Map.of("Events", eventsMembers));

        ScriptType type = ScriptType.SERVER;
        Path filePath = type.path.resolve("test.js");
        String source = "Events.onMessage((event) => { event.internalHelper })";

        assertDoesNotThrow(() -> EventCallbackSourceValidator.validate(filePath, source));
    }

    @Test
    void managedCallbackSchemaIsImmutableAfterInstall() {
        ApiSurfaceSnapshot snapshot = buildManagedSnapshot();
        ManagedCallbackSchemaRegistry.install(Map.of(ScriptType.SERVER, snapshot));

        ManagedCallbackSchemaRegistry.CallbackSchema schema =
                ManagedCallbackSchemaRegistry.resolve("Events", "Payload");
        assertNotNull(schema);
        assertTrue(schema.memberNames().contains("message"));
        assertFalse(schema.memberNames().contains("nonexistent"));
    }

    @Test
    void managedCallbackSchemaPersistsAcrossContextLifecycle() {
        ApiSurfaceSnapshot snapshot = buildManagedSnapshot();
        ManagedCallbackSchemaRegistry.install(Map.of(ScriptType.SERVER, snapshot));

        ManagedCallbackSchemaRegistry.CallbackSchema before =
                ManagedCallbackSchemaRegistry.resolve("Events", "Payload");
        assertNotNull(before);

        ManagedCallbackSchemaRegistry.install(Map.of(ScriptType.SERVER, snapshot));

        ManagedCallbackSchemaRegistry.CallbackSchema after =
                ManagedCallbackSchemaRegistry.resolve("Events", "Payload");
        assertNotNull(after);
        assertEquals(before.memberNames(), after.memberNames());
    }
}
