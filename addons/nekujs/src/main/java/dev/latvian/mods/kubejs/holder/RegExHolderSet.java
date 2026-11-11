package dev.latvian.mods.kubejs.holder;

import java.util.regex.Pattern;
import net.minecraft.core.HolderLookup;

/** Legacy constructor ABI backed by the native regular-expression holder set. */
public final class RegExHolderSet<T> extends NativeHolderSetView<T> {
    public RegExHolderSet(HolderLookup.RegistryLookup<T> lookup, Pattern pattern) {
        super(new com.tkisor.nekojs.holder.RegexHolderSet<>(lookup, pattern));
    }
}
