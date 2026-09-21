//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 28 AC1/AC7/AC8 fixture：post-effect 声明面只经<b>既有</b>
 * {@code ClientEvents} 的资源/reload 子事件贡献，事件成员在 catalog/golden 意义上唯一，
 * 且不新增第二个 PostEffects 事件。
 *
 * <p>断言只走生产注册路径（{@link NekoJSCorePlugin#registerClientEvents}）与真实总线
 * metadata，不复制一份平行的成员表。
 */
class PostEffectDeclarationSurfaceTest {

    @Test
    void declarationBusLivesInClientEventsAndIsTheOnlyPostEffectEvent() {
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
        new NekoJSCorePlugin().registerClientEvents(registry);

        var clientEvents = registry.view().get("ClientEvents");
        assertTrue(clientEvents != null, "NekoJSCorePlugin must register the ClientEvents group");
        assertTrue(clientEvents.viewBuses().containsKey("postEffects"),
                "the declaration face is ClientEvents.postEffects (existing resource/reload sub-events)");

        // 全事件面无第二个 PostEffects 域：postEffects 只出现一次，且没有任何组叫 PostEffects*。
        Set<String> postEffectBuses = new HashSet<>();
        for (var group : registry.view().values()) {
            for (var entry : group.viewBuses().entrySet()) {
                if (entry.getKey().toLowerCase(java.util.Locale.ROOT).contains("posteffect")) {
                    postEffectBuses.add(group.name() + "." + entry.getKey());
                }
            }
            assertFalse(group.name().startsWith("PostEffect"),
                    "no second PostEffects event group: " + group.name());
        }
        assertEquals(Set.of("ClientEvents.postEffects"), postEffectBuses,
                "the post-effect declaration surface must have exactly one bus, in ClientEvents");

        var bus = ClientEvents.POST_EFFECTS;
        assertSame(bus, clientEvents.getBusHolder("postEffects").getBus(ScriptType.CLIENT),
                "the catalog member is the production bus instance (no duplicate declaration)");
        assertEquals(PostEffectEventJS.class, bus.eventType(),
                "the payload type is the declaration event object");
        assertEquals("ClientEvents", bus.groupName());
        assertEquals("postEffects", bus.eventName());
    }

    @Test
    void declarationBusIsClientOnlyAndIsNotADispatchBus() {
        var bus = ClientEvents.POST_EFFECTS;
        assertEquals(ScriptType.CLIENT, bus.scriptType(),
                "the declaration surface is client_scripts only (no server-side post effects)");
        assertFalse(bus.canDispatch(),
                "the declaration bus is not key-dispatched: candidate collection has no key context");
    }

    @Test
    void declarationEventOffersRegisterAndUnregisterOnly() {
        for (var method : PostEffectEventJS.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            assertTrue(Set.of("register", "unregister", "getDeclaredCount").contains(method.getName()),
                    "the declaration event only collects declarations, not runtime actions: " + method.getName());
        }
    }

    @Test
    void runtimeBindingKeepsItsCallerVisibleMembers() {
        // AC5：运行时 binding 的调用者可见成员在改造后仍然存在（脚本侧写法不变）。
        Set<String> names = new HashSet<>();
        for (var method : PostEffectsJS.class.getMethods()) {
            names.add(method.getName());
        }
        assertTrue(names.containsAll(Set.of("set", "clear", "toggle", "current", "isActive",
                        "hasDefinition", "installed", "activeGeneration", "isAvailable", "presets")),
                "runtime binding members changed: " + names);
        assertTrue(names.contains("register") == false,
                "the declaration entry point must not stay on the runtime binding");
        assertTrue(names.contains("unregister") == false,
                "the declaration entry point must not stay on the runtime binding");
    }
}
//?}
