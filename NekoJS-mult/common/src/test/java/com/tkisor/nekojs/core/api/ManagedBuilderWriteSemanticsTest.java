package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
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
 * AC6（ticket 09）代表性受管 Builder fixture（NBT compound builder）：
 * <ul>
 *   <li>显式 setter（{@code putString/putInt/...}）与类型化通用写入（{@code put(key, NbtValue)}）
 *       走同一条校验/规范化路径，产生相同 {@code build()} 结果（同一写入语义）；</li>
 *   <li>{@code build()} 产物是 final identity（不可变快照），后续 builder 变更不回写；</li>
 *   <li>只读成员（NbtEntry 访问器）在契约中只承诺访问器符号，无 setter 符号；</li>
 *   <li>builder 创建（{@code NBT.compound}）是契约承诺，manifest/TS declaration 同步派生。</li>
 * </ul>
 *
 * <p>Graal 对 host object 的天然 JavaBean property assignment 行为<b>不作为承诺</b>
 * （spec 04），因此本 fixture 不为 property 写法建立契约；公开等价性以显式 setter
 * 与类型化 put 的 runtime fixture 为准。
 */
class ManagedBuilderWriteSemanticsTest {

    private static final URI CODE_SOURCE = URI.create("file:///test-nekojs-builder.jar");

    @Test
    void explicitSetterAndTypedPutNormalizeThroughOnePath() {
        CompoundTagBuilderJS viaSetter = new CompoundTagBuilderJS();
        viaSetter.putString("name", "neko").putInt("count", 2);

        CompoundTagBuilderJS viaTypedPut = new CompoundTagBuilderJS();
        viaTypedPut.put("name", NbtValue.string("neko")).put("count", NbtValue.intValue(2));

        assertEquals(viaSetter.build(), viaTypedPut.build(),
                "explicit setter and typed put must normalize to the identical compound value");

        // 嵌套 compound 与列表同样单路径规范化
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
        // NbtEntry 是只读 record：访问器即全部公开成员
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
                environment(), core.contracts(), List.of(core.contributions()), List.of());

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

    private static EnvironmentKey environment() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "test",
                "0.0.0",
                LoaderVersion.parse("0.0.0"),
                "1.21.1",
                Map.of());
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
