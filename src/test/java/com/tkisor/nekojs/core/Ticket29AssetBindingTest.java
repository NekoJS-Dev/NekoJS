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
 * Pins the {@code Assets} typed binding: {@code Assets.blockState/blockModel/itemModel/texture} is
 * the caller entry for script authors and must resolve through the **production registration path**
 * ({@link NekoJSCorePlugin#registerBinding}), with the declaration side
 * ({@code TypeDocCatalogEntry}) and the runtime registration side pointing at the same binding
 * name.
 *
 * <p>Why this fixture exists: collapsing the {@code registerTypeDocs} formatting once removed the
 * whole {@code registry.register("Assets", new AssetGeneratorJS())} line and left only the
 * documentation entry, so {@code Assets.*} in scripts became an undefined identifier while the
 * docs and completion still claimed it existed. Writing the documentation entry and the runtime
 * binding in two places invites exactly that kind of silent loss, so both are pinned together.
 */
class Ticket29AssetBindingTest {

    @BeforeAll
    static void initPlatform() {
        // Minimal Platform injection for the root test tree (Platform.init is defensive: it leaves an
        // already-initialized platform alone). Install the stub before touching ScriptType, whose
        // static init needs NekoJSPaths.get().
        try {
            Platform.init(new StubPlatform());
        } catch (RuntimeException ignored) {
            // Another test already initialized Platform
        }
    }

    /** Builds a registry through the production registration path, the same entry BindingsPoint uses during bootstrap. */
    private static BindingRegistry productionBindings(ScriptType scriptType) {
        BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(scriptType).scopedTo("nekojs");
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

    /** The declaration side and the runtime side must name the same binding: a documentation entry with no binding documents something that does not exist. */
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

    /** Minimal Platform stub for the root test tree, shaped like KeyBindEventsTest / QueryToolDeclarationParityTest. */
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
