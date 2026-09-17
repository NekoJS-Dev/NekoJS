package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicBuilderSurface;
import com.tkisor.nekojs.core.dynamic.plan.DynamicItemBuilder;
import com.tkisor.nekojs.core.dynamic.plan.DynamicMobEffectBuilder;
import com.tkisor.nekojs.core.dynamic.plan.DynamicSoundEventBuilder;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态注册收集事件的载荷（ticket 16，AC2/AC3）：成员目录<b>冻结</b>为三个类型直达入口
 * {@code item} / {@code soundEvent} / {@code mobEffect}，每个入口接收
 * {@code (id, callbackBuilder => {...})}（callback 可省略＝全默认值）。
 *
 * <p><b>没有通用 type catalog</b>：不存在 {@code event.register(typeName, ...)} 之类的
 * 通用成员，未知 type 字符串在成员解析期即被拒绝（错误列出三个冻结入口），不会被转换
 * 成注册能力；候选范围因此结构上封闭在 Item、SoundEvent、MobEffect 内。
 * 回调收到的 builder 是 {@link DynamicBuilderSurface} 包装的 typed callback Builder
 * （property 写入与显式 setter 同一路径）。
 *
 * <p>成员执行的任何失败（id 非法、builder 校验拒绝、callback 抛错、同批同 key 定义
 * 变化）都会记为该批计划的 collection error（<b>毒化整批</b>：preflight 必失败、批次
 * 不发布）后向调用方传播——总线 post 会吞掉监听器异常，所以毒化标记是「失败不另起
 * 写入」的承载机制。
 */
public final class DynamicRegistryEventJS implements ProxyObject {

    private final DynamicCandidateRegistryPlan plan;
    private final Map<String, ProxyExecutable> members;

    /** @param plan 本批收集目标（每批一个 payload 实例）。 */
    public DynamicRegistryEventJS(DynamicCandidateRegistryPlan plan) {
        this.plan = plan;
        Map<String, ProxyExecutable> built = new LinkedHashMap<>();
        built.put(DynamicDefinitionType.ITEM.apiName(), new TypedEntryMember(DynamicDefinitionType.ITEM));
        built.put(DynamicDefinitionType.SOUND_EVENT.apiName(), new TypedEntryMember(DynamicDefinitionType.SOUND_EVENT));
        built.put(DynamicDefinitionType.MOB_EFFECT.apiName(), new TypedEntryMember(DynamicDefinitionType.MOB_EFFECT));
        this.members = built;
    }

    // ---- ProxyObject：成员目录 ----

    @Override
    public Object getMember(String name) {
        ProxyExecutable member = members.get(name);
        if (member == null) {
            throw new IllegalArgumentException(
                    "DynamicRegistryEvent has no member '" + name + "'; the candidate surface is closed to"
                            + " the verified type entries " + DynamicDefinitionType.apiNameDirectory()
                            + " — there is no generic type catalog, and unknown type names are not"
                            + " registration capability");
        }
        return member;
    }

    @Override
    public boolean hasMember(String name) {
        return members.containsKey(name);
    }

    /** 成员目录（probe / 补全用）：三个冻结的类型直达入口。 */
    @Override
    public Object getMemberKeys() {
        return members.keySet().toArray();
    }

    @Override
    public void putMember(String name, Value value) {
        throw new UnsupportedOperationException("DynamicRegistryEvent is read-only");
    }

    @Override
    public boolean removeMember(String name) {
        return false;
    }

    // ---- 类型直达入口 ----

    private final class TypedEntryMember implements ProxyExecutable {

        private final DynamicDefinitionType type;

        TypedEntryMember(DynamicDefinitionType type) {
            this.type = type;
        }

        @Override
        public Object execute(Value... args) {
            if (args.length != 1 && args.length != 2) {
                throw new IllegalArgumentException(type.apiName()
                        + "(id) or " + type.apiName() + "(id, builderCallback)");
            }
            try {
                newBuilder().configure(args);
                return true;
            } catch (RuntimeException e) {
                // 毒化整批：收集失败 → preflight 必失败 → 批次不发布（AC1「失败不另起写入」）
                plan.noteCollectionError(currentScriptId(), e);
                throw e;
            }
        }

        private EntryBuilder newBuilder() {
            return switch (type) {
                case ITEM -> new EntryBuilder(new DynamicItemBuilder());
                case SOUND_EVENT -> new EntryBuilder(new DynamicSoundEventBuilder());
                case MOB_EFFECT -> new EntryBuilder(new DynamicMobEffectBuilder());
            };
        }

        /** 单条入口的执行体：id 解析 → callback（可省略）→ 定义收集进计划。 */
        private final class EntryBuilder {
            private final com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionBuilder builder;

            EntryBuilder(com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionBuilder builder) {
                this.builder = builder;
            }

            void configure(Value[] args) {
                String id = idArg(args[0]);
                if (args.length == 2) {
                    Value callback = args[1];
                    if (!callback.canExecute()) {
                        throw new IllegalArgumentException(type.apiName()
                                + " expects a builder callback like b => { b.maxStackSize = 16 }");
                    }
                    callback.executeVoid(DynamicBuilderSurface.of(builder));
                }
                plan.add(type, id, builder, currentScriptId(), "event." + type.apiName());
            }
        }
    }

    private static String idArg(Value value) {
        if (!value.isString()) {
            throw new IllegalArgumentException("DynamicRegistry expects an id string like 'mymod:ruby'");
        }
        return value.asString();
    }

    private static String currentScriptId() {
        Context context = Context.getCurrent();
        return context == null ? null : com.tkisor.nekojs.script.ScriptContextRegistry.currentScriptIdOf(context);
    }
}
