package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.core.compiler.GlobalBindingMemberValidator;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import graal.graalvm.polyglot.Context;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ManagedBindingSchemaTest {

    private static final ApiSymbolId STABLE_ID = ApiSymbolId.parse("global:Stable");

    @BeforeEach
    void setUp() {
        TestPlatformInit.ensureInitialized();
        ScriptBindingSchema.clearAll();
    }

    @Test
    void failedCandidateSchemaInstallDoesNotReplaceActiveSchemaOrGlobals() throws Exception {
        ScriptBindingSchema.register(ScriptType.SERVER,
                Map.of("ActiveBinding", new ScriptBindingSchema.BindingMembers(Set.of("active"))));
        ScriptBindingSchema.registerGlobals(ScriptType.SERVER, Set.of("activeGlobal"));

        try (Context candidateContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptBindingSchema.installCandidate(candidateContext, ScriptType.SERVER,
                    Map.of("CandidateBinding", new ScriptBindingSchema.BindingMembers(Set.of("candidate"))),
                    Set.of("candidateGlobal"));

            assertTrue(ScriptBindingSchema.lookup(ScriptType.SERVER).containsKey("ActiveBinding"));
            assertFalse(ScriptBindingSchema.lookup(ScriptType.SERVER).containsKey("CandidateBinding"));
            assertEquals(Set.of("activeGlobal"), ScriptBindingSchema.knownGlobals(ScriptType.SERVER));

            ScriptBindingSchema.discardCandidate(candidateContext);
            assertTrue(ScriptBindingSchema.lookup(ScriptType.SERVER).containsKey("ActiveBinding"));
            assertFalse(ScriptBindingSchema.lookup(ScriptType.SERVER).containsKey("CandidateBinding"));
            assertEquals(Set.of("activeGlobal"), ScriptBindingSchema.knownGlobals(ScriptType.SERVER));
        }
    }

    @Test
    void candidateValidatorUsesCandidateViewWithoutReplacingActiveView() throws Exception {
        ScriptBindingSchema.register(ScriptType.SERVER,
                Map.of("ActiveBinding", new ScriptBindingSchema.BindingMembers(Set.of("allowed"))));
        ScriptBindingSchema.registerGlobals(ScriptType.SERVER, Set.of("ActiveBinding"));
        AtomicInteger candidateDiagnostics = new AtomicInteger();

        try (Context candidateContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptBindingSchema.View candidate = ScriptBindingSchema.installCandidate(
                    candidateContext, ScriptType.SERVER,
                    Map.of("CandidateBinding", new ScriptBindingSchema.BindingMembers(Set.of("allowed"))),
                    Set.of("CandidateBinding"), diagnostic -> candidateDiagnostics.incrementAndGet());

            GlobalBindingMemberValidator.validate(Path.of("server_scripts/candidate.js"),
                    "CandidateBinding.typo", candidate);

            assertEquals(1, candidateDiagnostics.get(),
                    "preflight must validate against the candidate schema snapshot");
            assertTrue(ScriptBindingSchema.lookup(ScriptType.SERVER).containsKey("ActiveBinding"));
            assertFalse(ScriptBindingSchema.lookup(ScriptType.SERVER).containsKey("CandidateBinding"));
            ScriptBindingSchema.discardCandidate(candidateContext);
        }
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

    @Test
    void fromSurfaceReturnsExactMemberNames() {
        ApiSymbolId memberId = ApiSymbolId.parse("member:Stable.declared");
        ApiSymbol memberSymbol = new ApiSymbol(memberId, List.of(
                ApiSignature.function(
                        List.of(new ApiParameter("value", ApiTypeRef.primitive("string"), false, false)),
                        ApiTypeRef.primitive("string"))));
        ApiSymbol globalSymbol = new ApiSymbol(STABLE_ID, List.of(
                ApiSignature.function(
                        List.of(new ApiParameter("value", ApiTypeRef.primitive("string"), false, false)),
                        ApiTypeRef.primitive("string"))));

        ApiSurfaceSnapshot snapshot = new ApiSurfaceSnapshot(
                List.of(globalSymbol, memberSymbol),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        ScriptBindingSchema.BindingMembers members = ScriptBindingSchema.fromSurface(snapshot, STABLE_ID);
        assertTrue(members.contains("declared"));
        assertEquals(1, members.memberNames().size());
    }

    @Test
    void fromSurfaceExcludesImplementationExtras() {
        ApiSymbolId memberId = ApiSymbolId.parse("member:Stable.declared");
        ApiSymbolId helperId = ApiSymbolId.parse("member:Stable.accidentalPublicHelper");
        ApiSymbol memberSymbol = new ApiSymbol(memberId, List.of(
                ApiSignature.function(List.of(), ApiTypeRef.primitive("string"))));
        ApiSymbol helperSymbol = new ApiSymbol(helperId, List.of(
                ApiSignature.function(List.of(), ApiTypeRef.primitive("string"))));
        ApiSymbol globalSymbol = new ApiSymbol(STABLE_ID, List.of(
                ApiSignature.function(List.of(), ApiTypeRef.primitive("string"))));

        ApiSurfaceSnapshot snapshot = new ApiSurfaceSnapshot(
                List.of(globalSymbol, memberSymbol, helperSymbol),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        ScriptBindingSchema.BindingMembers members = ScriptBindingSchema.fromSurface(snapshot, STABLE_ID);
        assertTrue(members.contains("declared"));
        assertTrue(members.contains("accidentalPublicHelper"));
        assertEquals(2, members.memberNames().size());
    }

    @Test
    void fromSurfaceWithNullReturnsEmpty() {
        ScriptBindingSchema.BindingMembers members = ScriptBindingSchema.fromSurface(null, STABLE_ID);
        assertTrue(members.memberNames().isEmpty());
    }

    @Test
    void managedCallbackSchemaExtractionFromSurface() {
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

        ApiSurfaceSnapshot snapshot = new ApiSurfaceSnapshot(
                List.of(globalSymbol, eventSymbol, payloadSymbol, payloadMessageSymbol, payloadInternalSymbol),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        Map<ScriptType, ApiSurfaceSnapshot> snapshots = Map.of(ScriptType.SERVER, snapshot);
        ManagedCallbackSchemaRegistry.install(snapshots);

        ManagedCallbackSchemaRegistry.CallbackSchema schema =
                ManagedCallbackSchemaRegistry.resolve("Events", "Payload");
        assertNotNull(schema);
        assertTrue(schema.memberNames().contains("message"));
        assertTrue(schema.memberNames().contains("internalHelper"));
        assertEquals("Payload", schema.displayName());
    }
}
