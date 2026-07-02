package com.tkisor.nekojs.holder;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** 1.21.1 HolderSet：按正则匹配注册表 id。 */
public final class RegexHolderSet<T> extends HolderSet.ListBacked<T> {
    private final HolderLookup.RegistryLookup<T> lookup;
    private final Pattern pattern;
    private List<Holder<T>> contents;

    public RegexHolderSet(HolderLookup.RegistryLookup<T> lookup, Pattern pattern) {
        this.lookup = lookup;
        this.pattern = pattern;
    }

    @Override
    protected List<Holder<T>> contents() {
        if (contents == null) {
            contents = List.copyOf(lookup.listElements()
                .filter(ref -> pattern.matcher(ref.key().location().toString()).find())
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
