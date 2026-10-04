package com.tkisor.nekojs.wrapper.registry.base;

import net.minecraft.resources.Identifier;

/**
 * 对于所有的实现类，可修改的属性都建议使用public的field 而 不 是 通过返回自身实现链式调用的方法，因为方法返回类型不是“this”而是一个固定类型，一旦涉及到子类，返回类型就不能有效表示自身
 *
 * @author ZZZank
 */
public abstract class RegistryObjectBuilder<T> {
    protected final RegistryInfo<T> info;
    public final Identifier id;

    public RegistryObjectBuilder(RegistryInfo<T> info, Identifier id) {
        this.info = info;
        this.id = id;
    }

    public abstract T build();
}
