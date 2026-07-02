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
import java.util.stream.Stream;

/**
 * 用 GraalJS 谓词函数过滤注册表元素的惰性 HolderSet（对应 {@code {filter:(item)=>boolean}} 语法）。
 * {@link #contents()} 遍历 registry，对每个 holder 经 {@code argumentMapper} 映射成参数对象
 * （item 端是 ItemStack，fluid 端是 FluidStack），执行 JS 谓词，返回 true 才收录。
 * 仅运行时匹配（含 JS 函数无法序列化）。
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
