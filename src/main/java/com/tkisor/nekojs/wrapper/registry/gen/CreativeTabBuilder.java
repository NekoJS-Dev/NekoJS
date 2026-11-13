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
 * Builder for a creative mode tab: title, icon and entries. The entries are snapshotted into the tab
 * when it is registered, so entries added after registration need a re-registration or a save reload.
 * <pre>
 * event.creativeModeTab('mymod:main', b =&gt; { b.title = 'My Mod'; b.icon = 'minecraft:ruby'; b.add('mymod:ruby') })
 * </pre>
 */
public class CreativeTabBuilder extends RegistryObjectBuilder<CreativeModeTab> {

    private String title = "NekoJS";
    /** Icon: an item id string or an ItemStack; null falls back to the barrier icon. */
    private Object icon = null;

    /** Raw entry values (item id string or ItemStack), resolved only during the displayItems callback (see
     * the build comment). */
    private final List<Object> items = new ArrayList<>();

    public CreativeTabBuilder(Identifier id) {
        super(id);
    }

    /** Title; null or blank falls back to 'NekoJS'. The setter normalizes the value on write. */
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null || title.isBlank() ? "NekoJS" : title;
    }

    /** Icon: an item id string or an ItemStack; null falls back to the barrier icon. */
    public Object getIcon() {
        return icon;
    }

    public void setIcon(Object icon) {
        this.icon = icon;
    }

    /** Adds an entry: an item id string or an ItemStack. */
    public void add(Object item) {
        if (item != null) {
            items.add(item);
        }
    }

    @Override
    public CreativeModeTab build() {
        // icon and entries keep their raw values and are resolved in the lazy callback (when rendering or
        // opening the creative page). Fabric's mod init runs before vanilla item components are bound
        // ("Components not bound yet"), so building an ItemStack eagerly fails there. By the callback
        // both sides have components bound, and entries added after registration still resolve.
        final Object iconValue = icon;
        // NeoForge adds a no-arg overload of CreativeModeTab.builder() (default row and column); vanilla only
        // has the two-arg (Row,int) form, and fabric reaches the same result through
        // FabricCreativeModeTab.builder() from the creative-tab api
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
