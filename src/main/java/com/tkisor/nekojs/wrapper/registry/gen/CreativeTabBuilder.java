// TODO(loader-port): deferred to the LoaderBridge fabric port
//? if neoforge {
package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 创造模式标签页 builder：标题、图标与条目；条目在注册时快照到标签页
 * （注册后新增条目需重新注册/重进存档）。
 * <pre>
 * event.creativeModeTab('mymod:main', b =&gt; { b.title = '我的模组'; b.icon('minecraft:ruby'); b.add('mymod:ruby') })
 * </pre>
 */
public class CreativeTabBuilder extends RegistryObjectBuilder<CreativeModeTab> {

    public String title = "NekoJS";
    /** 图标（物品 id 字符串或 ItemStack；null 回退屏障图标）。 */
    public Object icon = null;

    private final List<ItemStack> items = new ArrayList<>();

    public CreativeTabBuilder(Identifier id) {
        super(id);
    }

    /** 添加条目（物品 id 字符串或 ItemStack）。 */
    public void add(Object item) {
        ItemStack stack = resolveStack(item);
        if (stack != null) {
            items.add(stack);
        }
    }

    @Override
    public CreativeModeTab build() {
        ItemStack iconStack = resolveStack(icon);
        if (iconStack == null) {
            iconStack = new ItemStack(Items.BARRIER);
        }
        final ItemStack finalIcon = iconStack;
        return CreativeModeTab.builder()
                .title(Component.literal(title))
                .icon(() -> finalIcon)
                .displayItems((parameters, output) -> output.acceptAll(items))
                .build();
    }

    private static ItemStack resolveStack(Object value) {
        if (value instanceof ItemStack stack) {
            return stack;
        }
        if (value instanceof String itemId) {
            return ItemStackAdapter.stringToItemStack(itemId);
        }
        return null;
    }
}
//?}
