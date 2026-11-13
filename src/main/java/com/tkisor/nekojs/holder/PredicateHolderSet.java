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
//? if >=26 {
import java.util.stream.Stream;
//?}

/**
 * Lazy HolderSet that filters registry elements with a GraalJS predicate function (the
 * {@code {filter:(item)=>boolean}} syntax). {@link #contents()} walks the registry, maps each
 * holder to an argument object through {@code argumentMapper} (an ItemStack on the item side,
 * a FluidStack on the fluid side), runs the JS predicate and keeps the holders it accepts.
 * Runtime matching only, since a JS function cannot be serialized.
 */
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

//? if >=26 {
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
//?}
}
