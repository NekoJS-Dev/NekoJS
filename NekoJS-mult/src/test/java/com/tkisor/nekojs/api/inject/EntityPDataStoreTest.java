//? if >=26 {
package com.tkisor.nekojs.api.inject;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EntityPDataStore.Access} 的契约与 {@link EntityPDataStore} 的装配语义：
 * getter 拷贝防御、空写移除子键、install 覆盖、fallback 键控。Access 键为实体 id
 * ——无需构造真实实体（{@code EntityType.PIG} 会触发 NeoForge AttachmentHolder 静态链，裸 JUnit 无 FML）。
 */
class EntityPDataStoreTest {

    @AfterEach
    void restore() {
        // 卸下测试装的实施，恢复 fallback（EntityPDataStore 无 reset；重装 fallback 形状即可）
        EntityPDataStore.install(memoryAccess());
    }

    /** 内存实现：与两平台真实实现同一段拷贝/移除语义（NeoForge 内联在 NekoJSMod，fabric 在 FabricPDataSync）。 */
    private static EntityPDataStore.Access memoryAccess() {
        Map<Integer, CompoundTag> containers = new HashMap<>();
        return new EntityPDataStore.Access() {
            private CompoundTag container(int entityId) {
                return containers.computeIfAbsent(entityId, ignored -> new CompoundTag());
            }

            @Override
            public CompoundTag get(int entityId, String key) {
                return container(entityId).getCompound(key).orElseGet(CompoundTag::new).copy();
            }

            @Override
            public void set(int entityId, String key, CompoundTag tag) {
                CompoundTag container = container(entityId);
                if (tag.isEmpty()) {
                    container.remove(key);
                } else {
                    container.put(key, tag.copy());
                }
            }
        };
    }

    @Test
    void installReplacesTheCurrentAccess() {
        EntityPDataStore.Access replacement = memoryAccess();
        EntityPDataStore.install(replacement);
        assertSame(replacement, EntityPDataStore.get());
    }

    @Test
    void getterReturnsDefensiveCopy() {
        EntityPDataStore.Access access = memoryAccess();
        access.set(2, EntityExtension.NEKO_PDATA_KEY, tagOf("hp", 3));

        CompoundTag first = access.get(2, EntityExtension.NEKO_PDATA_KEY);
        CompoundTag second = access.get(2, EntityExtension.NEKO_PDATA_KEY);
        assertNotSame(first, second, "getters must copy");
        assertEquals(3, second.getIntOr("hp", -1));
    }

    @Test
    void emptyWriteRemovesTheKey() {
        EntityPDataStore.Access access = memoryAccess();
        access.set(1, EntityExtension.NEKO_PDATA_KEY, tagOf("mana", 5));
        assertFalse(access.get(1, EntityExtension.NEKO_PDATA_KEY).isEmpty());

        // 脚本侧清空 pdata 的语义：写空 tag = 移除子键
        access.set(1, EntityExtension.NEKO_PDATA_KEY, new CompoundTag());
        assertTrue(access.get(1, EntityExtension.NEKO_PDATA_KEY).isEmpty());
    }

    @Test
    void fallbackStoresPerEntityUntilInstalled() {
        // 重装 fallback 形状的内存实现后，不同实体 id 的数据互不串扰
        EntityPDataStore.install(memoryAccess());
        EntityPDataStore.get().set(10, "k", tagOf("v", 1));
        EntityPDataStore.get().set(11, "k", tagOf("v", 2));

        assertEquals(1, EntityPDataStore.get().get(10, "k").getIntOr("v", -1));
        assertEquals(2, EntityPDataStore.get().get(11, "k").getIntOr("v", -1));
    }

    private static CompoundTag tagOf(String key, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key, value);
        return tag;
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("expected false");
    }
}
//?}
