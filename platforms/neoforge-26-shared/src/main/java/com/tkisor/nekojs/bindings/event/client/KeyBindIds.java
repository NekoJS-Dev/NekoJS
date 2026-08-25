package com.tkisor.nekojs.bindings.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tkisor.nekojs.NekoJS;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link KeyBindEvents} 的纯解析逻辑（无 EventGroup/桥接触）：单独成类是为了裸 JUnit 可测
 * ——KeyBindEvents 的静态初始化会注册事件组并探测可取消性，那需要 FML 运行时；本类只触碰
 * 裸环境可初始化的 vanilla 常量（{@code KeyMapping.Category} 等）。
 */
final class KeyBindIds {

    private KeyBindIds() {}

    /** Custom (non-vanilla) categories created by scripts, single instance per id. */
    static final Map<Identifier, KeyMapping.Category> CUSTOM_CATEGORIES = new ConcurrentHashMap<>();
    /** Custom categories already handed to the game (event or sort order). */
    static final Set<Identifier> INSTALLED_CATEGORIES = ConcurrentHashMap.newKeySet();

    /** Vanilla category spellings accepted by {@code register(...)}: 26.x dropped the string categories. */
    private static final Map<String, KeyMapping.Category> VANILLA_CATEGORIES = Map.ofEntries(
            Map.entry("key.categories.movement", KeyMapping.Category.MOVEMENT),
            Map.entry("movement", KeyMapping.Category.MOVEMENT),
            Map.entry("key.categories.misc", KeyMapping.Category.MISC),
            Map.entry("misc", KeyMapping.Category.MISC),
            Map.entry("key.categories.multiplayer", KeyMapping.Category.MULTIPLAYER),
            Map.entry("multiplayer", KeyMapping.Category.MULTIPLAYER),
            Map.entry("key.categories.gameplay", KeyMapping.Category.GAMEPLAY),
            Map.entry("gameplay", KeyMapping.Category.GAMEPLAY),
            Map.entry("key.categories.inventory", KeyMapping.Category.INVENTORY),
            Map.entry("inventory", KeyMapping.Category.INVENTORY),
            Map.entry("key.categories.creative", KeyMapping.Category.CREATIVE),
            Map.entry("creative", KeyMapping.Category.CREATIVE),
            Map.entry("key.categories.spectator", KeyMapping.Category.SPECTATOR),
            Map.entry("spectator", KeyMapping.Category.SPECTATOR),
            Map.entry("key.categories.debug", KeyMapping.Category.DEBUG),
            Map.entry("debug", KeyMapping.Category.DEBUG),
            // legacy 1.21 spelling; 26.x has no UI category — degrade to misc
            Map.entry("key.categories.ui", KeyMapping.Category.MISC),
            Map.entry("ui", KeyMapping.Category.MISC));

    /**
     * Parses a binding/category id: {@code 'namespace:path'} or a bare {@code 'path'} (default
     * namespace {@code nekojs}), mirroring the {@code IdentifierAdapter} conventions.
     */
    static Identifier parseIdentifier(String raw) {
        String trimmed = raw.trim();
        if (trimmed.contains(":")) {
            Identifier parsed = Identifier.tryParse(trimmed);
            if (parsed == null) {
                throw new IllegalArgumentException(
                        "invalid id '" + trimmed + "': expected 'namespace:path' or 'path'");
            }
            return parsed;
        }
        try {
            return Identifier.fromNamespaceAndPath(NekoJS.MODID, trimmed);
        } catch (RuntimeException e) {
            // 裸路径分支的非法字符（空格/!等）原版抛 IdentifierException，统一成可操作报错
            throw new IllegalArgumentException(
                    "invalid id '" + trimmed + "': expected 'namespace:path' or 'path'", e);
        }
    }

    /**
     * Parses a key name ({@code 'key.keyboard.g'}, {@code 'key.mouse.left'}, ...). A null/blank
     * argument yields the {@code UNKNOWN} key (unbound binding).
     */
    static InputConstants.Key parseKey(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            return InputConstants.UNKNOWN;
        }
        String trimmed = keyName.trim();
        try {
            return InputConstants.getKey(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "unknown key '" + trimmed + "': expected a name like 'key.keyboard.g' or 'key.mouse.left'", e);
        }
    }

    /**
     * Resolves a category argument: vanilla spellings ({@code 'key.categories.movement'} or the
     * bare {@code 'movement'}) map to the 26.x {@link KeyMapping.Category} constants, anything
     * else is parsed as an id ({@code 'mymod:my_keys'} → translation key
     * {@code 'key.category.mymod.my_keys'}). Null/blank defaults to {@code misc}.
     */
    static KeyMapping.Category resolveCategory(String category) {
        if (category == null || category.isBlank()) {
            return KeyMapping.Category.MISC;
        }
        String trimmed = category.trim();
        KeyMapping.Category vanilla = VANILLA_CATEGORIES.get(trimmed);
        if (vanilla != null) {
            return vanilla;
        }
        return CUSTOM_CATEGORIES.computeIfAbsent(parseIdentifier(trimmed), KeyMapping.Category::new);
    }

    /** Translation name of a binding: {@code 'key.<namespace>.<path>'}. */
    static String bindingName(Identifier id) {
        return "key." + id.getNamespace() + "." + id.getPath();
    }
}
