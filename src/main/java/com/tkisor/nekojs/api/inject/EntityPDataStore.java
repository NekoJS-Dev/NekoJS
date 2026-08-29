package com.tkisor.nekojs.api.inject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体持久化数据（{@code NekoJSPersistentData} 子键）在各平台持久化容器中的存取桥。
 *
 * <p>键为<b>实体 id</b>（{@code Entity#getId()}）而非实体引用：同步/镜像层本来就按 id 记账，
 * 测试也无需构造真实实体。NeoForge 的容器是 {@code Entity#getPersistentData()}（NeoForge 加的
 * API）；fabric 的容器是 {@code NekoEntityPDataMixin} 加的字段（随实体存档读写）。平台启动时
 * {@link #install}，{@code EntityExtension#neko$pdata()} 只依赖本桥。
 */
public final class EntityPDataStore {

    /** 存取语义：读出该实体持久化容器中 NekoJS 的子键（缺省空 tag 拷贝）；写回（空 tag = 移除子键）。 */
    public interface Access {
        CompoundTag get(int entityId, String key);

        void set(int entityId, String key, CompoundTag tag);
    }

    private static volatile Access current = fallback();

    private EntityPDataStore() {}

    /** 未装配时的兜底：按实体 id 的内存态存取（测试环境 / 专用服务器早期），不随存档持久化。 */
    private static Access fallback() {
        Map<Integer, CompoundTag> memory = new ConcurrentHashMap<>();
        return new Access() {
            @Override
            public CompoundTag get(int entityId, String key) {
                CompoundTag container = memory.get(entityId);
                if (container == null) return new CompoundTag();
//? if >=26 {
                return container.getCompound(key).orElseGet(CompoundTag::new).copy();
//?} else {
/*                return container.getCompound(key).copy();
*///?}
            }

            @Override
            public void set(int entityId, String key, CompoundTag tag) {
                if (tag.isEmpty()) {
                    CompoundTag container = memory.get(entityId);
                    if (container != null) {
                        container.remove(key);
                        if (container.isEmpty()) {
                            memory.remove(entityId);
                        }
                    }
                } else {
                    memory.computeIfAbsent(entityId, ignored -> new CompoundTag()).put(key, tag.copy());
                }
            }
        };
    }

    public static void install(Access access) {
        if (access != null) {
            current = access;
        }
    }

    public static Access get() {
        return current;
    }

    public static CompoundTag getPDataTag(Entity entity, String key) {
        return current.get(entity.getId(), key);
    }

    public static void setPDataTag(Entity entity, String key, CompoundTag tag) {
        current.set(entity.getId(), key, tag);
    }

}
