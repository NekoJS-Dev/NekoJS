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
 * Payload of the dynamic registration collection event: the member directory is frozen to three
 * typed entry points, {@code item} / {@code soundEvent} / {@code mobEffect}, and each accepts
 * {@code (id, callbackBuilder => {...})}. The callback may be omitted, which means all defaults.
 *
 * <p>There is no generic type catalog: no member such as {@code event.register(typeName, ...)}
 * exists, and an unknown type string is rejected while the member is resolved (the error lists the
 * three fixed entry points) instead of turning into a registration. The candidate set is therefore
 * structurally closed to Item, SoundEvent and MobEffect. The builder a callback receives is a
 * typed callback builder wrapped by {@link DynamicBuilderSurface}: property writes and explicit
 * setters take the same path.
 *
 * <p>Any failure in a member (invalid id, builder validation refusal, callback throwing, a changed
 * definition for the same key in the same batch) is recorded as a collection error on that batch's
 * plan, which poisons the whole batch: preflight must fail and the batch is not published. The
 * error is then propagated to the caller. The bus post swallows listener exceptions, so the poison
 * flag is what makes a failure start no separate write.
 */
public final class DynamicRegistryEventJS implements ProxyObject {

    private final DynamicCandidateRegistryPlan plan;
    private final Map<String, ProxyExecutable> members;

    /** @param plan the collection target for this batch; there is one payload instance per batch */
    public DynamicRegistryEventJS(DynamicCandidateRegistryPlan plan) {
        this.plan = plan;
        Map<String, ProxyExecutable> built = new LinkedHashMap<>();
        built.put(DynamicDefinitionType.ITEM.apiName(), new TypedEntryMember(DynamicDefinitionType.ITEM));
        built.put(DynamicDefinitionType.SOUND_EVENT.apiName(), new TypedEntryMember(DynamicDefinitionType.SOUND_EVENT));
        built.put(DynamicDefinitionType.MOB_EFFECT.apiName(), new TypedEntryMember(DynamicDefinitionType.MOB_EFFECT));
        this.members = built;
    }

    // ---- ProxyObject: member directory ----

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

    /** Member directory, used for probing and completion: the three fixed typed entry points. */
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

    // ---- Typed entry points ----

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
                // Poison the whole batch: a collection failure makes preflight fail, so the batch is never
                // published and a failure starts no separate write.
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

        /** Execution body of one entry: parse the id, run the optional callback, then collect the
         *  definition into the plan. */
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
