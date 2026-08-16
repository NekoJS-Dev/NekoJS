package com.tkisor.nekojs.wrapper.item;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import net.minecraft.item.ItemStack;

/**
 * 1.12.2 ItemStack 专属 PersistentDataJS。
 *
 * <p>1.12.2 没有 1.21.x 的 DataComponents，ItemStack NBT 是单个可变 {@code NBTTagCompound}。
 * 这里挂载在子 compound {@code "neko"} 下，通过 {@link ItemStack#getOrCreateSubCompound(String)}
 * 拿到内部子节点引用 —— 直接修改该引用即原地持久化，saver 为空操作。
 */
@Doc("Persistent data view backed by an ItemStack's 'neko' NBT sub-compound.")
@Doc("Mutations write through to the stack's NBT immediately; use item.persistentData to obtain one.")
public class PersistentDataJS extends com.tkisor.nekojs.wrapper.pdata.PersistentDataJS {

    /** Creates a persistent data view over the stack's 'neko' sub-compound. */
    @Doc("Creates a persistent data view for an item stack.")
    @Param(name = "stack", value = "the item stack whose NBT backs this view")
    public PersistentDataJS(ItemStack stack) {
        super(
                () -> stack.getOrCreateSubCompound("neko"),
                t -> {}
        );
    }
}
