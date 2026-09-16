package com.tkisor.nekojs.api.inject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体持久化数据（{@code NekoJSPersistentData} 子键）在各平台持久化容器中的存取桥。
 *
 * <p>存取入口有两层：<b>实体引用面</b>（{@code get/set(Entity, ...)}，调用现场持有实体，
 * 平台实现直接解引用容器——{@code EntityJoinLevelEvent} 窗口内实体尚未进入 level 实体
 * 索引，按 id 反查会静默丢弃写入，票 03 §3-3 登记的缺口由此闭合）与 <b>id 面</b>
 * （{@code get/set(int, ...)}，同步/镜像层按 id 记账，测试无需构造真实实体）。
 * 平台启动时 {@link #install}，{@code EntityExtension#neko$pdata()} 只依赖本桥。
 */
public final class EntityPDataStore {

    /**
     * 存取语义：读出该实体持久化容器中 NekoJS 的子键（缺省空 tag 拷贝）；写回（空 tag = 移除子键）。
     *
     * <p>平台实现应 override 实体引用面：调用现场本来就有实体引用，直接解引用容器，
     * 不依赖「id → level.getEntity(id)」反查（joinLevel 窗口内反查必空 → 写静默 no-op）。
     * id 面默认实现仍按 id 记账，供同步/镜像层与测试使用。
     */
    public interface Access {
        CompoundTag get(int entityId, String key);

        void set(int entityId, String key, CompoundTag tag);

        /** 实体引用面（缺省委托 id 面）：joinLevel 窗口写入语义由平台 override 修复。 */
        default CompoundTag get(Entity entity, String key) {
            return get(entity.getId(), key);
        }

        /** 实体引用面（缺省委托 id 面）：joinLevel 窗口写入语义由平台 override 修复。 */
        default void set(Entity entity, String key, CompoundTag tag) {
            set(entity.getId(), key, tag);
        }
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

    /** 脚本侧入口：走实体引用面（joinLevel 窗口不依赖 id 反查）。 */
    public static CompoundTag getPDataTag(Entity entity, String key) {
        return current.get(entity, key);
    }

    /** 脚本侧入口：走实体引用面（joinLevel 窗口不依赖 id 反查）。 */
    public static void setPDataTag(Entity entity, String key, CompoundTag tag) {
        current.set(entity, key, tag);
    }

}
