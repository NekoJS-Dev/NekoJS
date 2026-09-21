//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.bindings.event.client.KeyBindEvents;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import com.tkisor.nekojs.wrapper.client.PainterJS;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 26 输入 / HUD 脚本面 fixture：成员名、成员类型与「注册入口 vs 事件监听」的区分，
 * 全部经<b>生产注册路径</b>（{@link NekoJSCorePlugin#registerEvents} +
 * {@code registerClientEvents}）构造真实 {@link EventGroupRegistry}，再读真实总线 metadata。
 *
 * <p>为什么需要它：{@code KeyBindEvents.register(...)} 与 {@code ClientEvents.hudRender(...)}
 * 挂在事件组上但语义是「调用即注册」（直接创建 KeyMapping / 按 id 注册常驻渲染器），
 * 与同级监听总线只有一字之差。若哪天有人把它们改成普通监听（或反之），脚本写法会静默变化
 * ——本 fixture 把这条差异钉成断言（AC3）。
 *
 * <p>断言只走公开面：组成员名、总线实例 identity、{@code eventType()}、{@code canDispatch()}、
 * 运行期类型；不复制一份平行的成员表。
 */
class Ticket26ClientInputHudSurfaceTest {

    /** 脚本可见成员名必须是 lowerCamelCase（Java 大写常量只是内部定位符）。 */
    private static final Pattern LOWER_CAMEL = Pattern.compile("[a-z][A-Za-z0-9]*");

    private static Map<String, EventGroup> productionGroups() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        new NekoJSCorePlugin().registerEvents(registry);
        new NekoJSCorePlugin().registerClientEvents(registry);
        return registry.view();
    }

    @Test
    void scriptVisibleMemberNamesAreLowerCamelCase() {
        EventGroup keyBinds = productionGroups().get("KeyBindEvents");
        EventGroup client = productionGroups().get("ClientEvents");
        assertTrue(keyBinds != null && client != null,
                "both input/HUD groups must be registered by the production client registration path");

        Map<String, String> checked = new LinkedHashMap<>();
        for (EventGroup group : new EventGroup[] {keyBinds, client}) {
            for (String member : group.viewBuses().keySet()) {
                assertTrue(LOWER_CAMEL.matcher(member).matches(),
                        group.name() + "." + member + " is not a lowerCamelCase script member name"
                                + " (the Java field name is an internal locator, not the script name)");
                checked.put(group.name(), checked.getOrDefault(group.name(), "") + member + ",");
            }
        }
        assertTrue(checked.get("KeyBindEvents").contains("pressed")
                        && checked.get("KeyBindEvents").contains("register"),
                "the input group must keep its script members: " + checked);
    }

    @Test
    void registrationEntriesAreNotListenerBuses() {
        // register / hudRender：挂在组上的 EventBusJS 子类，覆写 execute 走注册语义。
        assertTrue(KeyBindEvents.REGISTER instanceof KeyBindEvents.RegisterBus,
                "KeyBindEvents.register must stay the direct binding-creation entry, not a listener bus");
        assertTrue(ClientEvents.HUD_RENDER instanceof RenderRegistrationBusJS,
                "ClientEvents.hudRender must stay the id-keyed renderer registration entry");
        assertFalse(KeyBindEvents.REGISTER.canDispatch(),
                "a registration entry is not key-dispatched");
        assertFalse(ClientEvents.HUD_RENDER.canDispatch(),
                "a registration entry is not key-dispatched");

        // 同级监听总线保持普通监听形态：hud 是 PainterJS 的每帧监听，不是注册入口。
        assertFalse(RenderRegistrationBusJS.class.isInstance(ClientEvents.HUD),
                "ClientEvents.hud is the per-frame HUD listener bus, not the renderer registration entry");
        assertEquals(PainterJS.class, ClientEvents.HUD.eventType(),
                "ClientEvents.hud still delivers the documented PainterJS payload");
        assertFalse(ClientEvents.HUD.canDispatch(),
                "ClientEvents.hud is a plain (non-dispatched) listener bus");

        // pressed / released / tick 仍是按绑定 id 定向分发的监听总线。
        for (EventBusJS<KeyBindEvents.KeyBindEventJS, String> bus
                : java.util.List.of(KeyBindEvents.PRESSED, KeyBindEvents.RELEASED, KeyBindEvents.TICK)) {
            assertTrue(bus.canDispatch(),
                    "KeyBindEvents." + bus.eventName() + " must stay a dispatch bus (id-keyed subscriptions)");
            assertEquals(KeyBindEvents.KeyBindEventJS.class, bus.eventType(),
                    "the trigger buses deliver the KeyBindEventJS payload");
            assertEquals("KeyBindEvents", bus.groupName());
        }
    }

    @Test
    void productionRegistrationExposesTheDocumentedMembersOnce() {
        Map<String, EventGroup> groups = productionGroups();

        assertEquals(Set.of("pressed", "released", "tick", "register"),
                new TreeSet<>(groups.get("KeyBindEvents").viewBuses().keySet()),
                "the input group's script-visible member set changed");

        Set<String> clientMembers = groups.get("ClientEvents").viewBuses().keySet();
        assertTrue(clientMembers.containsAll(Set.of("hud", "hudRender", "registerKeyMappings")),
                "this ticket's HUD / key-mapping members must stay in ClientEvents: " + clientMembers);

        // 目录里的成员就是字段单例（不是复制的一份总线）。
        assertSame(ClientEvents.HUD,
                groups.get("ClientEvents").getBusHolder("hud").getBus(ScriptType.CLIENT));
        assertSame(ClientEvents.HUD_RENDER,
                groups.get("ClientEvents").getBusHolder("hudRender").getBus(ScriptType.CLIENT));
        assertSame(KeyBindEvents.REGISTER,
                groups.get("KeyBindEvents").getBusHolder("register").getBus(ScriptType.CLIENT));
        assertSame(ClientEvents.REGISTER_KEY_MAPPINGS,
                groups.get("ClientEvents").getBusHolder("registerKeyMappings").getBus(ScriptType.CLIENT));

        // client-only：本票成员只对 client_scripts 可见，且没有 server 侧形态。
        for (EventBusJS<?, ?> bus : java.util.List.of(ClientEvents.HUD, ClientEvents.HUD_RENDER,
                ClientEvents.REGISTER_KEY_MAPPINGS, KeyBindEvents.REGISTER, KeyBindEvents.PRESSED)) {
            assertEquals(ScriptType.CLIENT, bus.scriptType(),
                    bus.groupName() + "." + bus.eventName() + " must stay client_scripts only");
        }
    }

    @Test
    void inputHudSurfaceDoesNotDuplicateOtherDomains() {
        Map<String, EventGroup> groups = productionGroups();

        // 每条总线在全部组里只出现一次（identity）：本票成员不得被复制进第二个组/目录。
        Set<EventBusJS<?, ?>> identities = new java.util.HashSet<>();
        for (EventGroup group : groups.values()) {
            for (var holder : group.viewBuses().values()) {
                EventBusJS<?, ?> bus = holder.getBus(ScriptType.CLIENT);
                if (bus == null) bus = holder.getBus(ScriptType.SERVER);
                if (bus != null) {
                    assertTrue(identities.add(bus),
                            "bus instance shared across groups (duplicate declaration): " + group.name());
                }
            }
        }
        assertTrue(identities.contains(ClientEvents.HUD_RENDER) && identities.contains(KeyBindEvents.REGISTER),
                "this ticket's entries participate in the single registration surface");

        // 不声明第二套 PostEffects / Assets 域（票 28/29 的 owner 不被本票复制）。
        assertFalse(groups.containsKey("PostEffectEvents"),
                "post-effect declarations stay in ClientEvents.postEffects (ticket 28 owner)");
        assertFalse(groups.containsKey("AssetsEvents"),
                "asset declarations stay in ClientEvents.generateAssets/lang (ticket 29 owner)");
        for (String groupName : groups.keySet()) {
            assertFalse(groupName.startsWith("PostEffect"),
                    "no second post-effect group: " + groupName);
        }
    }
}
//?}
//?}
