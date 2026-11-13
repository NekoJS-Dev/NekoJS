// 1.21.1 implementation, paired with the same-named 26.x file under the version tree's src/. The content
// is what that file becomes once evaluated for this node. Keep this file in sync when the 26.x behavior
// changes.
package com.tkisor.nekojs.holder;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import java.util.List;
import java.util.Optional;

/**
 * Lazily populated HolderSet that filters a registry by namespace; it backs the {@code @mod} syntax.
 * The first call to {@link #contents()} walks the registry, keeps the entries in that namespace, and
 * caches the result. It is only used for runtime matching and registers no codec, so it never appears
 * in recipe JSON.
 */
public final class NamespaceHolderSet<T> extends HolderSet.ListBacked<T> {
    private final HolderLookup.RegistryLookup<T> lookup;
    private final String namespace;
    private List<Holder<T>> contents;

    public NamespaceHolderSet(HolderLookup.RegistryLookup<T> lookup, String namespace) {
        this.lookup = lookup;
        this.namespace = namespace;
    }

    @Override
    protected List<Holder<T>> contents() {
        if (contents == null) {
            contents = List.copyOf(lookup.listElements()
                .filter(ref -> ref.key().location().getNamespace().equals(namespace))
                .<Holder<T>>map(ref -> ref)
                .toList());
        }
        return contents;
    }

    @Override
    public Either<TagKey<T>, List<Holder<T>>> unwrap() {
        return Either.right(contents());
    }

    @Override
    public Optional<TagKey<T>> unwrapKey() {
        return Optional.empty();
    }

    @Override
    public boolean contains(Holder<T> holder) {
        return contents().contains(holder);
    }

}
