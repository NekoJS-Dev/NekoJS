// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
package com.tkisor.nekojs.holder;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import java.util.List;
import java.util.Optional;

/**
 * 按注册表命名空间过滤的惰性 HolderSet（对应 {@code @mod} 语法）。
 * {@link #contents()} 首次调用时遍历 registry 并按 namespace 过滤，结果缓存。
 * 仅用于运行时匹配，未注册 codec（不进 recipe JSON）。
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
