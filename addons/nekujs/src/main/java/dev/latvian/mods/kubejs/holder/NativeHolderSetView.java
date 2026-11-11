package dev.latvian.mods.kubejs.holder;

import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;

/** Keeps legacy class names while the native holder set owns matching and lazy contents. */
abstract class NativeHolderSetView<T> extends HolderSet.ListBacked<T> {
    private final HolderSet.ListBacked<T> delegate;

    NativeHolderSetView(HolderSet.ListBacked<T> delegate) { this.delegate = Objects.requireNonNull(delegate); }

    @Override protected List<Holder<T>> contents() {
        // Both native delegates unwrap their cached direct list, never a named tag.
        return delegate.unwrap().right().orElseThrow();
    }
    @Override public boolean contains(Holder<T> holder) { return delegate.contains(holder); }
    @Override public Either<TagKey<T>, List<Holder<T>>> unwrap() { return delegate.unwrap(); }
    @Override public Optional<TagKey<T>> unwrapKey() { return delegate.unwrapKey(); }
    @Override public boolean canSerializeIn(HolderOwner<T> owner) { return delegate.canSerializeIn(owner); }
}
