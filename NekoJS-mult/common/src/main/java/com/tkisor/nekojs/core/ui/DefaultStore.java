package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiReadonlySignal;
import com.tkisor.nekojs.api.ui.UiStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link UiStore} 默认实现：按顶层键做依赖跟踪与失效合并。
 *
 * <p>读取某字段只登记该字段依赖；写入某字段只让读过该字段的 root 失效。
 * {@link #snapshot()} 登记整店依赖，任何字段写入都会让快照读者失效。
 */
final class DefaultStore implements UiStore {

    private final DefaultUiStateOwner owner;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, KeyRef> keyRefs = new LinkedHashMap<>();

    DefaultStore(DefaultUiStateOwner owner, Map<String, ?> initial) {
        this.owner = owner;
        if (initial != null) {
            values.putAll(initial);
        }
    }

    @Override
    public Object read(String key) {
        Objects.requireNonNull(key, "key");
        owner.link(keyRef(key));
        return values.get(key);
    }

    @Override
    public void write(String key, Object value) {
        Objects.requireNonNull(key, "key");
        owner.requireOwnerThread("store.write(" + key + ")");
        Object previous = values.get(key);
        if (Objects.equals(previous, value)) {
            return;
        }
        values.put(key, value);
        owner.markDirty(keyRef(key));
        owner.markDirty(this);
    }

    @Override
    public void merge(Map<String, ?> updates) {
        Objects.requireNonNull(updates, "updates");
        owner.requireOwnerThread("store.merge");
        boolean changed = false;
        for (Map.Entry<String, ?> entry : updates.entrySet()) {
            Object previous = values.get(entry.getKey());
            if (!Objects.equals(previous, entry.getValue())) {
                values.put(entry.getKey(), entry.getValue());
                owner.markDirty(keyRef(entry.getKey()));
                changed = true;
            }
        }
        if (changed) {
            owner.markDirty(this);
        }
    }

    @Override
    public UiReadonlySignal<Object> field(String key) {
        Objects.requireNonNull(key, "key");
        KeyRef ref = keyRef(key);
        return () -> {
            owner.link(ref);
            return values.get(key);
        };
    }

    @Override
    public Map<String, Object> snapshot() {
        owner.link(this);
        return Map.copyOf(values);
    }

    private KeyRef keyRef(String key) {
        return keyRefs.computeIfAbsent(key, KeyRef::new);
    }

    /** 单字段依赖令牌；identity 语义使其成为 keyed 依赖。 */
    static final class KeyRef {
        private final String key;

        KeyRef(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return "store[" + key + "]";
        }
    }
}
