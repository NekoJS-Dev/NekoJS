// 26.x implementation; this file must not contain version guards again. The 1.21.1
// implementation is the same-named file under versions/1.21.1/src, so keep it in sync when
// this file's behavior changes.
package com.tkisor.nekojs.holder;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Lazy HolderSet that filters a registry by namespace (the {@code @mod} syntax). The first
 * {@link #contents()} call walks the registry, filters by namespace and caches the result.
 * Used for runtime matching only; no codec is registered, so it never appears in recipe JSON.
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
                .filter(ref -> ref.key().identifier().getNamespace().equals(namespace))
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

    @Override
    public boolean isBound() {
        return contents != null;
    }

    @Override
    public Stream<Holder<T>> stream() {
        return contents().stream();
    }

    @Override
    public int size() {
        return contents().size();
    }
}
