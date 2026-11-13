package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.api.ApiSurfaceTestSupport;
import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.probe.backend.typescript.ManagedApiDeclarationGenerator;
import com.tkisor.nekojs.wrapper.nbt.CompoundTagBuilderJS;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the writes of a representative managed Builder fixture, the NBT compound builder:
 * <ul>
 *   <li>explicit setters ({@code putString/putInt/...}) and the generic typed write
 *       ({@code put(key, NbtValue)}) take the same validation and normalization path and produce
 *       the same {@code build()} result;</li>
 *   <li>the {@code build()} result is a final immutable snapshot; later builder changes do not
 *       write back into it;</li>
 *   <li>read-only members (the NbtEntry accessors) only promise accessor symbols in the contract,
 *       never setter symbols;</li>
 *   <li>builder creation ({@code NBT.compound}) is part of the contract, and the manifest and TS
 *       declaration derive from it.</li>
 * </ul>
 *
 * <p>Graal's built-in JavaBean property assignment on host objects is not a commitment, so this
 * fixture does not pin a contract for property writes. The public equivalence is covered by the
 * runtime fixture that uses explicit setters and typed put.
 */
class ManagedBuilderWriteSemanticsTest {

    private static final URI CODE_SOURCE = URI.create("file:///test-nekojs-builder.jar");

    /** Runtime fixture for the managed Builder write path. Under real Graal execution, a script
     *  property assignment writes the **public field** of the managed Builder, the current shape,
     *  like {@code ItemBuilder.maxStackSize}: `b.maxStackSize = 16` is a host field write. The
     *  fixture pins that path, so a Graal upgrade that changes field writes fails here first.
     *
     *  <p>Measured on Graal 25.x (2026-09-12): with a private field plus a bare bean setter,
     *  {@code p.maxStackSize = 16} **silently does nothing** (no error, the setter is not called).
     *  That is why Graal's natural bean behavior is not a commitment. Setter-equivalent dispatch
     *  is implemented by the managed Builder mechanism (ADR-0005 revision) and does not rely on
     *  it.</p>
     */
    @Test
    void propertyAssignmentWritesPublicFieldThroughGraalRuntime() {
        try (graal.graalvm.polyglot.Context context = graal.graalvm.polyglot.Context.newBuilder("js")
                .allowAllAccess(true).build()) {
            FieldSemanticsBuilder viaScript = new FieldSemanticsBuilder();
            FieldSemanticsBuilder viaJava = new FieldSemanticsBuilder();
            context.getBindings("js").putMember("b", viaScript);

            context.eval("js", "b.maxStackSize = 16; b.rarity = 'rare';");
            viaJava.maxStackSize = 16;
            viaJava.rarity = "rare";

            assertEquals(viaJava.maxStackSize, viaScript.maxStackSize,
                    "script property write must hit the public field exactly like a Java write");
            assertEquals(viaJava.rarity, viaScript.rarity,
                    "script property write must hit the public field exactly like a Java write");
        }
    }

    /** Representative managed Builder shape: public fields only, no setters, same structure as ItemBuilder. */
    public static final class FieldSemanticsBuilder {
        public int maxStackSize = 64;
        public String rarity = "common";
    }

    @Test
    void explicitSetterAndTypedPutNormalizeThroughOnePath() {
        CompoundTagBuilderJS viaSetter = new CompoundTagBuilderJS();
        viaSetter.putString("name", "neko").putInt("count", 2);

        CompoundTagBuilderJS viaTypedPut = new CompoundTagBuilderJS();
        viaTypedPut.put("name", NbtValue.string("neko")).put("count", NbtValue.intValue(2));

        assertEquals(viaSetter.build(), viaTypedPut.build(),
                "explicit setter and typed put must normalize to the identical compound value");

        // Nested compounds and lists go through the same single normalization path
        CompoundTagBuilderJS nestedSetter = new CompoundTagBuilderJS();
        nestedSetter.putCompound("inner", new CompoundTagBuilderJS().putString("a", "b"))
                .putList("items", List.of(NbtValue.intValue(1), NbtValue.intValue(2)));
        CompoundTagBuilderJS nestedTyped = new CompoundTagBuilderJS();
        nestedTyped.put("inner", NbtValue.compound(Map.of("a", NbtValue.string("b"))))
                .put("items", NbtValue.list(List.of(NbtValue.intValue(1), NbtValue.intValue(2))));
        assertEquals(nestedSetter.build(), nestedTyped.build(),
                "nested writes must normalize identically across both spellings");
    }

    @Test
    void builtValueKeepsFinalIdentity() {
        CompoundTagBuilderJS builder = new CompoundTagBuilderJS();
        builder.putString("a", "1");
        NbtValue.CompoundValue snapshot = builder.build();

        builder.putInt("b", 2);
        builder.putString("a", "overwritten");

        assertEquals(NbtValue.compound(Map.of("a", NbtValue.string("1"))), snapshot,
                "built snapshot must not be affected by later builder mutations (final identity)");
        assertEquals(2, builder.build().values().size());
    }

    @Test
    void readOnlyMembersPromiseAccessorsOnly() {
        Set<String> contractIds = contractIds();
        assertTrue(contractIds.contains("member:NbtEntry.key"), "NbtEntry.key accessor must be promised");
        assertTrue(contractIds.contains("member:NbtEntry.value"), "NbtEntry.value accessor must be promised");
        assertTrue(contractIds.stream().noneMatch(id -> id.startsWith("member:NbtEntry.set")
                        || id.startsWith("member:NbtEntry.with") || id.startsWith("member:NbtEntry.clear")),
                "read-only members must not gain setter symbols in the contract");
        // NbtEntry is a read-only record: its accessors are its only public members
        for (var method : NbtEntry.class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) continue;
            assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()),
                    "NbtEntry must not expose non-public surprises");
        }
    }

    @Test
    void builderCreationIsContractPromiseWithDerivationParity() {
        Set<String> contractIds = contractIds();
        assertTrue(contractIds.contains("member:NBT.compound"),
                "NBT.compound builder creation must be a contract promise");

        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                ApiSurfaceTestSupport.serverEnvironment(), core.contracts(), List.of(core.contributions()), List.of());

        ApiManifest manifest = ApiManifestGenerator.generate(
                ApiRuntimeVersionReader.read(), "test", "1.21.1",
                registry.environmentSnapshot().surfaceSnapshot());
        assertTrue(manifest.symbols().stream().anyMatch(s -> s.id().equals("member:NBT.compound")),
                "manifest must carry the builder creation symbol");

        String ts = new ManagedApiDeclarationGenerator()
                .generate(Map.of(ScriptType.SERVER, registry.environmentSnapshot()), ScriptType.SERVER);
        assertTrue(ts.contains("compound("), "TS declaration must declare the builder creation");
        assertFalse(ts.contains("putCompoundKey"), "declaration must not invent non-runtime members");
    }

    // ---------- helpers ----------

    private static Set<String> contractIds() {
        return CoreManagedApiBootstrap.buildContract(CODE_SOURCE).contract().symbols().stream()
                .map(s -> s.id().value()).collect(java.util.stream.Collectors.toSet());
    }

    private static final class EmptyPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return Path.of("."); }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0.0.0"; }
    }
}
