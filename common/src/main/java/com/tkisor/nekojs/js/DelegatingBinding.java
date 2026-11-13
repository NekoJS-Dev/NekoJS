package com.tkisor.nekojs.js;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Global binding proxy: on member access it first checks {@code extensions} (provided by the
 * {@code helper} object); on a hit it delegates to the helper, otherwise it delegates to the
 * static members of {@code targetClass}.
 *
 * <p>Purpose: the NeoForge platform cannot inject public static methods into MC classes (Mixin
 * refuses it, and the JS Coremod path depends on a standalone Nashorn that GraalVM Java 25 does
 * not provide), so the global {@code Item} binding is this proxy: {@code Item.of(...)} goes to the
 * helper (ItemJS) and every other member delegates back to the real MC {@code Item} class.
 *
 * <p>Key implementation point: GraalJS {@code Value.asValue} is only meaningful while the
 * Polyglot Context is active. {@code DelegatingBinding} is constructed during mod registration
 * ({@code registerBinding}, before any Context exists), so <b>must not</b> call {@code asValue} in
 * the constructor. It has to wait until GraalJS calls {@code getMember} and the like, when the
 * Context is active. Otherwise the helper receives a detached Value, {@code getMember} returns
 * null for everything and {@code invokeMember} fails with {@code Unknown identifier}.
 *
 * <p>Limitation: the proxy is not a Java Class mirror, so {@code Java.type('..Item').of()} still
 * cannot see {@code of} (this is not a real injection).
 */
public final class DelegatingBinding implements ProxyObject {
    private final Object helperObj;
    private final Class<?> targetClassObj;
    private final Set<String> extensions;

    private volatile Value helperValue;
    private volatile Value targetValue;
    private volatile Context boundContext;

    public DelegatingBinding(Object helper, Class<?> targetClass, Set<String> extensions) {
        this.helperObj = helper;
        this.targetClassObj = targetClass;
        this.extensions = Set.copyOf(extensions);
    }

    public Set<String> extensions() {
        return extensions;
    }

    /** Target MC class the proxy delegates to; probe merges this class's static members into the
     * .d.ts of the global binding. */
    public Class<?> targetClass() {
        return targetClassObj;
    }

    /**
     * Wraps the raw objects into Values, but only while GraalJS is accessing the proxy and the
     * Context is active.
     *
     * <p>This proxy is a process-level object: bootstrap builds it once and reuses it across
     * Contexts, while a transactional reload closes the old Context and switches to the new
     * candidate. A cached {@code Value} belongs to the old Context, so later access throws
     * {@code The Context is already closed}. The cache is therefore keyed by the <b>current active
     * Context</b>: when the Context changes (reload switch) the wrappers are rebuilt. GraalJS calls
     * {@code getMember} and the other callbacks inside the active Context of the accessing thread,
     * so {@code Context.getCurrent()} is never null here.
     */
    private void ensure() {
        Context current = Context.getCurrent();
        if (helperValue == null || boundContext != current) {
            boundContext = current;
            helperValue = Value.asValue(helperObj);
            targetValue = Value.asValue(targetClassObj);
        }
    }

    @Override
    public Object getMember(String key) {
        ensure();
        if (extensions.contains(key)) {
            return helperValue.getMember(key);
        }
        return targetValue.getMember(key);
    }

    @Override
    public boolean hasMember(String key) {
        ensure();
        if (extensions.contains(key)) {
            return helperValue.hasMember(key);
        }
        return targetValue.hasMember(key);
    }

    @Override
    public Object getMemberKeys() {
        ensure();
        Set<String> keys = new LinkedHashSet<>(extensions);
        try {
            if (targetValue.hasMembers()) {
                targetValue.getMemberKeys().forEach(keys::add);
            }
        } catch (Throwable ignored) {
            // Some host objects cannot enumerate member keys; ignore the failure, getMember delegation
            // still works
        }
        return keys.toArray();
    }

    @Override
    public void putMember(String key, Value value) {
        // Read-only binding: scripts cannot overwrite it
    }
}
