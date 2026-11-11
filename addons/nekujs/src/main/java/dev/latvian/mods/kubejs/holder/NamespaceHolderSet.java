package dev.latvian.mods.kubejs.holder;

import net.minecraft.core.HolderLookup;

/** Legacy constructor ABI backed by the native namespace holder set. */
public final class NamespaceHolderSet<T> extends NativeHolderSetView<T> {
    public NamespaceHolderSet(HolderLookup.RegistryLookup<T> lookup, String namespace) {
        super(new com.tkisor.nekojs.holder.NamespaceHolderSet<>(lookup, namespace));
    }
}
