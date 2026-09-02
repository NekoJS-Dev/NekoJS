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

    /** 条目原始值（物品 id 字符串或 ItemStack），displayItems 回调期才解析（见 build 注释）。 */
    private final List<Object> items = new ArrayList<>();

    public CreativeTabBuilder(Identifier id) {
        super(id);
    }

    /** 添加条目（物品 id 字符串或 ItemStack）。 */
    public void add(Object item) {
        if (item != null) {
            items.add(item);
        }
    }

    @Override
    public CreativeModeTab build() {
        // icon 与条目都留原始值、在懒回调期解析（渲染/打开创造页时）：fabric 的 mod init
        // 早于 vanilla 物品组件绑定（"Components not bound yet"），eager 构造 ItemStack
        // 在该时机必炸；懒回调期两侧组件均已绑定，且"注册后新增条目"的解析也更宽容
        final Object iconValue = icon;
        // NeoForge 给 CreativeModeTab.builder() 加了无参重载（默认 Row/列位）；vanilla 只有
        // (Row,int) 双参，fabric 经 creative-tab api 的 FabricCreativeModeTab.builder() 等价
        net.minecraft.world.item.CreativeModeTab.Builder tabBuilder;
//? if neoforge {
        tabBuilder = CreativeModeTab.builder();
//?} else {
/*        tabBuilder = net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder();
*///?}
        return tabBuilder
                .title(Component.literal(title))
                .icon(() -> {
                    ItemStack stack = resolveStack(iconValue);
                    return stack != null ? stack : new ItemStack(Items.BARRIER);
                })
                .displayItems((parameters, output) -> {
                    for (Object item : items) {
                        ItemStack stack = resolveStack(item);
                        if (stack != null) {
                            output.accept(stack);
                        }
                    }
                })
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
