// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对；Identifier→ResourceLocation 与
// bindComponents→反射 components 字段是本节点的真实差异）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * Server-side item property modification event payload（{@code ItemEvents.modification}）。
 * 26.x 侧同名文件是版本树 src/ 的守卫形态；ticket 39 收集语义见那份文件的 javadoc。
 *
 * <h2>Internal (1.21.1)</h2>
 * {@code Item} 仍自持组件 Map（26.x 委托 builtInRegistryHolder），发布走反射写
 * {@code Item#components} 私有 final 字段。
 */
public class ItemModificationEventJS {

    private final ModificationCandidatePlan plan;
    private int declaredCount;

    private static final Field COMPONENTS_FIELD = componentsField();

    /** @param plan 收集目标计划（由 1.21.1 侧 {@code ModificationDomainOwner} 创建）。 */
    public ItemModificationEventJS(ModificationCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Records a modification declaration for the item with the given id（收集语义与
     * 26.x 同：回调异常向上传播、声明进 inert 计划、MC 应用只在 Adapter）。
     *
     * <p>回调参数类型保持改造前的函数式接口签名（{@code Consumer<ItemModificationJS>}）：
     * Java 侧直接传 lambda；脚本侧的 Graal 函数由沙盒 HostAccess
     * （{@code allowAllImplementations}）实现该接口。
     *
     * @param itemId item id, e.g. {@code 'minecraft:diamond'} (namespace optional)
     * @param modifier property callback（脚本函数或 Java {@code Consumer}）
     */
    public void modify(String itemId, Consumer<ItemModificationJS> modifier) {
        ResourceLocation id = parseItemId(itemId);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }
        if (modifier == null) {
            throw new IllegalArgumentException("Modifier must not be null");
        }
        ItemModificationJS view = new ItemModificationJS();
        modifier.accept(view);
        plan.add(new ModificationDeclaration("item", id.toString(), view.normalizedProperties(), null));
        declaredCount++;
    }

    /** Number of declarations recorded so far during this event. */
    public int getModifiedCount() {
        return declaredCount;
    }

    static ResourceLocation parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Item id must not be empty");
        }
        String id = itemId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid item id: " + itemId);
        }
        return location;
    }

    static void applyComponents(Item item, DataComponentMap components) {
        try {
            COMPONENTS_FIELD.set(item, components);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to write item components of " + BuiltInRegistries.ITEM.getKey(item), e);
        }
    }

    private static Field componentsField() {
        try {
            Field field = Item.class.getDeclaredField("components");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
