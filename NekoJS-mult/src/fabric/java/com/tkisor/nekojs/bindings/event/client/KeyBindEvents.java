// fabric 节点孪生：共享树同名类整文件 `//? if neoforge && >=26` 守卫（重 NeoForge 面：
// RegisterKeyMappingsEvent 事件窗口、mod-bus 订阅、FML 运行时探测）。fabric 侧等价物更简——
// KeyMappingHelper.registerKeyMapping 任意时机可调（无事件窗口/pending 重试），状态轮询挂
// ClientTickEvents.END_CLIENT_TICK（edge-detect 逻辑与 NeoForge 版逐行同构）。
// 解析/分类逻辑复用共享树 KeyBindIds（>=26 版本守卫，fabric 26 存活）。共享树版去 NeoForge
// 化后（payload/RegisterBus 提为中立形态）合并回单副本。
package com.tkisor.nekojs.bindings.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import graal.graalvm.polyglot.Value;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Script-friendly key bindings（fabric 形态，脚本面与 NeoForge 版一致）：
 * {@code register(id, key[, category])} 经 KeyMappingHelper 注册绑定，
 * {@code pressed} / {@code released} / {@code tick} 总线在客户端 tick 上做
 * {@code KeyMapping.isDown()} 的边沿检测。仅 client_scripts 可见。
 */
public final class KeyBindEvents {

    public static final EventGroup GROUP = EventGroup.of("KeyBindEvents");

    private static final DispatchKey<KeyBindEventJS, String> KEY_DISPATCH =
            DispatchKey.of(String.class, KeyBindEventJS::getId);

    public static final EventBusJS<KeyBindEventJS, String> PRESSED =
            GROUP.client("pressed", KeyBindEventJS.class, KEY_DISPATCH);

    public static final EventBusJS<KeyBindEventJS, String> RELEASED =
            GROUP.client("released", KeyBindEventJS.class, KEY_DISPATCH);

    public static final EventBusJS<KeyBindEventJS, String> TICK =
            GROUP.client("tick", KeyBindEventJS.class, KEY_DISPATCH);

    /** {@code KeyBindEvents.register(id, key[, category])}：注册绑定，返回 KeyMapping 句柄。 */
    public static final RegisterBus REGISTER =
            GROUP.add("register", ScriptType.CLIENT, new RegisterBus());

    private static final class Binding {
        final Identifier id;
        final KeyMapping mapping;
        boolean lastDown;

        Binding(Identifier id, KeyMapping mapping) {
            this.id = id;
            this.mapping = mapping;
        }
    }

    private static final Map<String, Binding> BINDINGS = new ConcurrentHashMap<>();

    private KeyBindEvents() {}

    /** client init 调用：挂状态轮询（END_CLIENT_TICK，与 NeoForge 版 ClientTickEvent.Post 同位）。 */
    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(client -> dispatchStateTransitions());
    }

    /** 注册（或查回）绑定：同 id 幂等，CLIENT 脚本 reload 不重复注册。 */
    static KeyMapping registerBinding(Identifier id, InputConstants.Key key, KeyMapping.Category category) {
        String fullId = id.toString();
        Binding existing = BINDINGS.get(fullId);
        if (existing != null) {
            return existing.mapping;
        }
        installCategory(category);
        KeyMapping mapping = new KeyMapping(KeyBindIds.bindingName(id), key.getType(), key.getValue(), category);
        BINDINGS.put(fullId, new Binding(id, mapping));
        installIntoGame(mapping, id);
        return mapping;
    }

    /**
     * 把绑定交给游戏：CLIENT 脚本加载在 CLIENT_STARTED（晚于 Options 构建），此时
     * fabric-api 的 KeyMappingHelper 会撞 26.x GameOptions 的二次初始化守卫
     * （"GameOptions has already been initialised"）——改为直接扩 options.keyMappings
     * 数组（AW mutable；RegisterKeyMappingsEvent 本身做的就是这份替换）。失败时绑定仍
     * 记录在 BINDINGS（脚本可持句柄），只是不进控制设置界面。
     */
    private static void installIntoGame(KeyMapping mapping, Identifier id) {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft == null || minecraft.options == null) {
                return;   // 保持 pending，下一 tick 重试（dispatchStateTransitions 顺带 flush）
            }
            net.minecraft.client.KeyMapping[] current = minecraft.options.keyMappings;
            net.minecraft.client.KeyMapping[] grown = java.util.Arrays.copyOf(current, current.length + 1);
            grown[current.length] = mapping;
            minecraft.options.keyMappings = grown;
        } catch (Throwable t) {
            NekoJS.LOGGER.error(
                    "KeyBindEvents: failed to append '{}' to options.keyMappings; it stays script-pollable"
                            + " but will not appear in the controls screen", id, t);
        }
    }

    /** 自定义分类：26.x 唯一注册点是 Category.register（与 NeoForge 版事件窗口外的回退同款）。 */
    @SuppressWarnings("deprecation")
    private static void installCategory(KeyMapping.Category category) {
        if (!KeyBindIds.INSTALLED_CATEGORIES.add(category.id())) {
            return;
        }
        try {
            KeyMapping.Category.register(category.id());
        } catch (IllegalArgumentException alreadyRegistered) {
            // 值相等的分类已注册——无事可做
        } catch (Throwable t) {
            NekoJS.LOGGER.warn("KeyBindEvents: failed to register key category '{}'", category.id(), t);
        }
    }

    private static void dispatchStateTransitions() {
        if (BINDINGS.isEmpty()) {
            return;
        }
        boolean transitions = PRESSED.hasListeners() || RELEASED.hasListeners();
        boolean whileDown = TICK.hasListeners();
        if (!transitions && !whileDown) {
            return;
        }
        for (Binding binding : BINDINGS.values()) {
            boolean down = binding.mapping.isDown();
            if (transitions) {
                if (down && !binding.lastDown) {
                    PRESSED.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, true));
                } else if (!down && binding.lastDown) {
                    RELEASED.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, false));
                }
            }
            if (whileDown && down) {
                TICK.post(new KeyBindEventJS(binding.id.toString(), binding.mapping, true));
            }
            binding.lastDown = down;
        }
    }

    /** {@code KeyBindEvents.register(...)} 入口：注册绑定而非挂监听（与 NeoForge 版同形）。 */
    public static final class RegisterBus extends EventBusJS<Object, Void> {

        RegisterBus() {
            super(EventBus.create(Object.class));
        }

        @Override
        public Object execute(Value... args) {
            if (args.length < 1 || args.length > 3) {
                throw new IllegalArgumentException("register requires (id, key[, category])");
            }
            if (!args[0].isString() || args[0].asString().isBlank()) {
                throw new IllegalArgumentException("binding id must be a non-blank string like 'mymod:my_key'");
            }
            Identifier id = KeyBindIds.parseIdentifier(args[0].asString());
            InputConstants.Key key = KeyBindIds.parseKey(optionalString(args, 1, "key"));
            KeyMapping.Category category = KeyBindIds.resolveCategory(optionalString(args, 2, "category"));
            return registerBinding(id, key, category);
        }

        private static String optionalString(Value[] args, int index, String what) {
            if (index >= args.length) {
                return null;
            }
            Value value = args[index];
            if (value.isNull()) {
                return null;
            }
            if (!value.isString()) {
                throw new IllegalArgumentException(what + " must be a string (or null)");
            }
            return value.asString();
        }
    }

    /** pressed / released / tick 的事件对象（与 NeoForge 版同形）。 */
    @Doc("Key binding trigger event (KeyBindEvents.pressed / released / tick).")
    @Doc("Fires on the client thread; only visible in client_scripts.")
    public static final class KeyBindEventJS {

        private final String id;
        private final KeyMapping keyMapping;
        private final boolean down;

        KeyBindEventJS(String id, KeyMapping keyMapping, boolean down) {
            this.id = id;
            this.keyMapping = keyMapping;
            this.down = down;
        }

        @Doc("Gets the full binding id, e.g. 'mymod:my_key'.")
        @Return("the binding id; also the dispatch key of these buses")
        public String getId() {
            return id;
        }

        @Doc("Gets the underlying vanilla KeyMapping handle.")
        @Return("the KeyMapping; poll isDown()/consumeClick() or read getName() from it")
        public KeyMapping getKeyMapping() {
            return keyMapping;
        }

        @Doc("Gets whether the binding is currently down.")
        @Return("true while held; pressed/released posts reflect the transition direction")
        public boolean isDown() {
            return down;
        }
    }
}
