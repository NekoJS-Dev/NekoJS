//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.wrapper.AssetGeneratorJS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 票 29 AC1/AC3 的 Assets typed binding 回归钉：
 * {@code Assets.blockState/blockModel/itemModel/texture} 是脚本作者的调用者入口，
 * 必须经**生产注册路径**（{@link NekoJSCorePlugin#registerBinding}）解析得到，
 * 且声明面（{@code TypeDocCatalogEntry}）与运行时注册面指向同一个绑定名。
 *
 * <p>为何需要本 fixture：{@code "档1 首批守卫退场"}（c8066519 / 59919f87）在折叠
 * {@code registerTypeDocs} 排版时，把 {@code registry.register("Assets", new
 * AssetGeneratorJS())} 整行删掉了，只留下同名的文档条目——脚本侧引用 {@code Assets.*}
 * 因此变成「未定义标识符」，而文档/补全仍宣称它存在。文档条目与运行时绑定各写一遍，
 * 正是这类静默丢失的温床，故此处把两者一起钉住。
 */
class Ticket29AssetBindingTest {

    @BeforeAll
    static void initPlatform() {
        // 根测试树的最小 Platform 注入（Platform.init 自带防御：已初始化则不动）。
        // 必须先立桩再触碰 ScriptType：它的静态初始化要 NekoJSPaths.get()。
        try {
            Platform.init(new StubPlatform());
        } catch (RuntimeException ignored) {
            // 其它测试已初始化过 Platform
        }
    }

    /** 走生产注册路径装配 registry（与 bootstrap 期 BindingsPoint 收集同一入口）。 */
    private static BindingRegistry productionBindings(ScriptType scriptType) {
        BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(scriptType);
        new NekoJSCorePlugin().registerBinding(registry);
        return registry;
    }

    @Test
    void assetsBindingResolvesThroughTheProductionRegistrationPath() {
        Binding assets = productionBindings(ScriptType.CLIENT).viewRegistered().get("Assets");

        assertNotNull(assets,
                "'Assets' must be registered by NekoJSCorePlugin.registerBinding: the script-facing"
                        + " typed asset generators (Assets.blockState/blockModel/itemModel/texture)"
                        + " have no other registration path");
        assertInstanceOf(AssetGeneratorJS.class, assets.value(),
                "the 'Assets' binding must carry the AssetGeneratorJS implementation");
        assertEquals(AssetGeneratorJS.class, assets.valueType(),
                "the declared value type must be exactly AssetGeneratorJS so preflight and probe"
                        + " describe the same members the runtime binding exposes");
    }

    /** 声明面与运行时面必须指同一个名字：文档条目不配上绑定就是「文档说有、实际没有」。 */
    @Test
    void assetsDocumentationEntryIsBackedByARuntimeBinding() {
        List<TypeDocCatalogEntry> docs = new ArrayList<>();
        new NekoJSCorePlugin().registerTypeDocs(new TypeDocsRegister() {
            @Override
            public void register(TypeDocCatalogEntry entry) {
                docs.add(entry);
            }

            @Override
            public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
            }
        });

        List<TypeDocCatalogEntry> assetsDocs = docs.stream()
                .filter(doc -> doc.kind().equals("binding") && doc.target().equals("Assets"))
                .toList();
        assertFalse(assetsDocs.isEmpty(),
                "NekoJSCorePlugin must document the 'Assets' binding (probe/completion source)");

        BindingRegistry registry = productionBindings(ScriptType.CLIENT);
        for (TypeDocCatalogEntry doc : assetsDocs) {
            assertNotNull(registry.viewRegistered().get(doc.target()),
                    "documented binding '" + doc.target() + "' has no runtime registration:"
                            + " declarations and the binding registry must agree");
        }
    }

    /** 根测试树的最小 Platform 桩（形态照 KeyBindEventsTest / QueryToolDeclarationParityTest）。 */
    private static final class StubPlatform implements IPlatform {
        @Override
        public boolean isClient() {
            return true;
        }

        @Override
        public boolean isDevelopment() {
            return true;
        }

        @Override
        public String getMcVersion() {
            return "test";
        }

        @Override
        public Path getGameDir() {
            return com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket29-assets");
        }

        @Override
        public String getLoaderId() {
            return "test";
        }

        @Override
        public String getLoaderVersion() {
            return "0";
        }

        @Override
        public java.util.Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() {
            return java.util.Map.of();
        }

        @Override
        public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) {
            return null;
        }

        @Override
        public java.util.Set<com.tkisor.nekojs.platform.PlatformCapability> capabilities() {
            return java.util.Set.of();
        }
    }
}
//?}
//?}
