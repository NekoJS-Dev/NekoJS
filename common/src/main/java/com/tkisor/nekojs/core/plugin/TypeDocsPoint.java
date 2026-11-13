package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanMethodProjectionCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Built-in extension point {@code nekojs:type_docs}. The merge policy is
 * {@link MergePolicy#append}: it collects a list of documentation entries, so no keys can
 * conflict.
 *
 * <p>The {@link Bucket} accumulator and the {@link TypeDocsSnapshot} output are also used by the
 * twin point {@code nekojs:node_type_docs} ({@link NodeTypeDocsPoint}). Both points share the
 * {@link TypeDocsRegister} registration interface and differ only in which plugins they collect.
 */
public final class TypeDocsPoint {

    /** Extension point id. */
    public static final String ID = "nekojs:type_docs";

    private TypeDocsPoint() {
    }

    /** Explicit declaration form: the plugin is collected by the {@code nekojs:type_docs} extension point, the
     * same result as overriding the matching NekoJSPlugin hook directly. */
    public interface Contributor extends NekoJSPlugin {

        /** Registers type documentation: descriptions and examples for script-side types. It feeds probe
         * output and completion. */
        default void registerTypeDocs(TypeDocsRegister registry) {
        }
    }

    /** Shared products for type_docs and node_type_docs; class replacements resolve after both products merge. */
    record TypeDocsSnapshot(
            List<TypeDocCatalogEntry> docs,
            List<ManualDeclarationCatalogEntry> manualDeclarations,
            List<ClassDeclarationCatalogEntry> classDeclarations,
            List<BeanAliasExclusionCatalogEntry> beanAliasExclusions,
            List<BeanMethodProjectionCatalogEntry> beanMethodProjections,
            List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces) {
        TypeDocsSnapshot {
            docs = docs.stream().sorted(Comparator.comparingInt(TypeDocCatalogEntry::priority)).toList();
            manualDeclarations = manualDeclarations.stream()
                    .sorted(Comparator.comparingInt(ManualDeclarationCatalogEntry::priority)).toList();
            classDeclarations = List.copyOf(classDeclarations);
            beanAliasExclusions = List.copyOf(beanAliasExclusions);
            beanMethodProjections = List.copyOf(beanMethodProjections);
            registryBuilderSurfaces = registryBuilderSurfaces.stream()
                    .sorted(Comparator.comparing(RegistryBuilderSurfaceEntry::builderName)
                            .thenComparing(RegistryBuilderSurfaceEntry::typeName))
                    .toList();
        }
    }

    /** Collects documentation and declaration channels, then seals them when a snapshot is taken. */
    static final class Bucket implements TypeDocsRegister, Sealable {
        private final List<TypeDocCatalogEntry> docs = new ArrayList<>();
        private final List<ManualDeclarationCatalogEntry> manualDeclarations = new ArrayList<>();
        private final List<ClassDeclarationCatalogEntry> classDeclarations = new ArrayList<>();
        private final List<BeanAliasExclusionCatalogEntry> beanAliasExclusions = new ArrayList<>();
        private final List<BeanMethodProjectionCatalogEntry> beanMethodProjections = new ArrayList<>();
        private final List<RegistryBuilderSurfaceEntry> registryBuilderSurfaces = new ArrayList<>();
        private boolean sealed;

        private void checkSealed() {
            if (sealed) {
                throw new IllegalStateException("扩展点已 finish，累积器密封，不可再收集");
            }
        }

        @Override
        public void register(TypeDocCatalogEntry entry) {
            checkSealed();
            docs.add(java.util.Objects.requireNonNull(entry, "entry"));
        }

        @Override
        public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
            checkSealed();
            manualDeclarations.add(java.util.Objects.requireNonNull(entry, "entry"));
        }

        @Override
        public void registerClassDeclaration(ClassDeclarationCatalogEntry entry) {
            checkSealed();
            classDeclarations.add(java.util.Objects.requireNonNull(entry, "entry"));
        }

        @Override
        public void registerBeanAliasExclusion(BeanAliasExclusionCatalogEntry entry) {
            checkSealed();
            beanAliasExclusions.add(java.util.Objects.requireNonNull(entry, "entry"));
        }

        @Override
        public void registerBeanMethodProjection(BeanMethodProjectionCatalogEntry entry) {
            checkSealed();
            beanMethodProjections.add(java.util.Objects.requireNonNull(entry, "entry"));
        }

        @Override
        public void registerRegistryBuilderSurface(RegistryBuilderSurfaceEntry entry) {
            checkSealed();
            registryBuilderSurfaces.add(java.util.Objects.requireNonNull(entry, "entry"));
        }

        TypeDocsSnapshot snapshot() {
            sealed = true;
            return new TypeDocsSnapshot(List.copyOf(docs), List.copyOf(manualDeclarations),
                    List.copyOf(classDeclarations), List.copyOf(beanAliasExclusions), List.copyOf(beanMethodProjections), List.copyOf(registryBuilderSurfaces));
        }

        @Override
        public void seal() {
            this.sealed = true;
        }
    }

    /** The extension point definition, registered from the {@link NekoBuiltinPointsPlugin} list. */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, Bucket, TypeDocsSnapshot> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, Bucket, TypeDocsSnapshot>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .initializer(context -> new Bucket())
                    .collector(NekoJSPlugin::registerTypeDocs)
                    .finish(Bucket::snapshot)
                    // Optional ordering dependency. The version-tree plugin (NekoRegistryPointsPlugin) reads the
                    // registry_types output in registerTypeDocs to derive typed Builder entries, and that
                    // point is defined in the version tree, so common cannot reference its type here and
                    // uses the id string instead. A common-only bootstrap does not include that plugin, so
                    // the id stays unregistered and no edge is added. Without this edge, type_docs runs
                    // first because it is built in and registered earlier, and registry_types then throws
                    // "has not finished yet".
                    .dependsOnOptionalId("nekojs:registry_types")
                    .build();
}
