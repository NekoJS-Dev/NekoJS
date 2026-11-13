// Fabric copy of the shared tree's KeyBindEvents class, guarded file-wide by
// `//? if neoforge && >=26` on the NeoForge side (the RegisterKeyMappingsEvent window, mod-bus
// subscription and FML runtime probes). The Fabric equivalent is simpler:
// KeyMappingHelper.registerKeyMapping can be called at any time, with no event window and no
// pending retry, so state polling hangs off ClientTickEvents.END_CLIENT_TICK (the edge-detect
// logic is line for line the same as the NeoForge version). Key resolution and categorization
// reuse KeyBindIds from the shared tree (guarded for >=26, and Fabric 26 keeps it). The shared
// tree version merges back into a single copy once its payload and RegisterBus are neutral.
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
 * Script-friendly key bindings (Fabric form; the script surface matches the NeoForge version).
 * {@code register(id, key[, category])} registers a binding through KeyMappingHelper, and the
 * {@code pressed} / {@code released} / {@code tick} buses edge-detect {@code KeyMapping.isDown()}
 * on the client tick. Visible only in client_scripts.
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

    /** {@code KeyBindEvents.register(id, key[, category])}: registers a binding and returns the
     * KeyMapping handle. */
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

    /** Called at client init: hooks state polling on END_CLIENT_TICK, the same place as
     * ClientTickEvent.Post in the NeoForge version. */
    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(client -> dispatchStateTransitions());
    }

    /** Registers a binding, or returns the existing one: the same id is idempotent, so a CLIENT
     * script reload does not register it twice. */
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
     * Hands the binding to the game. CLIENT scripts load at CLIENT_STARTED, after Options is
     * built, where fabric-api's KeyMappingHelper hits the 26.x GameOptions double-initialization
     * guard ("GameOptions has already been initialised"). So this appends to the
     * options.keyMappings array directly instead; the array is mutable through the access widener
     * and this is the same replacement RegisterKeyMappingsEvent performs. On failure the binding
     * stays in BINDINGS, so scripts can still hold the handle, but it does not appear in the
     * controls screen.
     */
    private static void installIntoGame(KeyMapping mapping, Identifier id) {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft == null || minecraft.options == null) {
                return;   // stay pending and retry on the next tick (dispatchStateTransitions flushes it along the way)
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

    /** Custom category: on 26.x the only registration point is Category.register, the same fallback
     * the NeoForge version uses outside the event window. */
    @SuppressWarnings("deprecation")
    private static void installCategory(KeyMapping.Category category) {
        if (!KeyBindIds.INSTALLED_CATEGORIES.add(category.id())) {
            return;
        }
        try {
            KeyMapping.Category.register(category.id());
        } catch (IllegalArgumentException alreadyRegistered) {
            // A category with an equal value is already registered, so there is nothing to do.
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

    /** Entry point for {@code KeyBindEvents.register(...)}: it registers a binding instead of
     * attaching a listener, the same shape as the NeoForge version. */
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

    /** Event object for pressed / released / tick (same shape as the NeoForge version). */
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
