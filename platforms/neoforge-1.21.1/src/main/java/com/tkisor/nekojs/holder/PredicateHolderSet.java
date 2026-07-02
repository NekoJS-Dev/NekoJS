package com.tkisor.nekojs.holder;

import com.mojang.datafixers.util.Either;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** 1.21.1 HolderSet：JS 谓词过滤。 */
public final class PredicateHolderSet<T> extends HolderSet.ListBacked<T> {
    private final HolderLookup.RegistryLookup<T> lookup;
    private final Value predicate;
    private final Function<Holder<T>, Object> argumentMapper;
    private List<Holder<T>> contents;

    public PredicateHolderSet(HolderLookup.RegistryLookup<T> lookup, Value predicate, Function<Holder<T>, Object> argumentMapper) {
        this.lookup = lookup;
        this.predicate = predicate;
        this.argumentMapper = argumentMapper;
    }

    @Override
    protected List<Holder<T>> contents() {
        if (contents == null) {
            List<Holder<T>> list = new ArrayList<>();
            for (Holder<T> ref : lookup.listElements().<Holder<T>>map(r -> r).toList()) {
                Object arg = argumentMapper.apply(ref);
                Value result = predicate.execute(arg);
                if (result != null && result.isBoolean() && result.asBoolean()) {
                    list.add(ref);
                }
            }
            contents = List.copyOf(list);
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
