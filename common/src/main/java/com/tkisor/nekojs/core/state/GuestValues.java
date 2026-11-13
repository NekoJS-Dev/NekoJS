package com.tkisor.nekojs.core.state;

import graal.graalvm.polyglot.Value;

/**
 * Decides whether a value is a guest value.
 *
 * <p><b>How it decides (measured on GraalMC 25.1.3.7)</b>: when a script stores an object, array
 * or function in a host Map, the Graal side hands over a Truffle internal proxy, one of
 * {@code com.oracle.truffle.polyglot.PolyglotMap}, {@code PolyglotList} or
 * {@code PolyglotMapAndFunction}. Host values (String, Integer, host objects) always keep their
 * host class. A value whose class is a Truffle internal proxy or a {@link Value} is therefore a
 * guest value, deterministically.
 *
 * <p><b>Why {@code Value.asValue(v).getContext() != null} does not work</b>: the same measurement
 * shows that on a thread inside an <b>entered</b> Context this method also returns the current
 * Context for host String and Integer values. The view's put is called through interop on the
 * script evaluation thread, so using it would classify every script write as a guest value, and
 * those values would be swept on generation close, breaking the "survive a reload" rule.
 *
 * <p>Guest values are bound to the generation lifetime: they become invalid when the Context is
 * destroyed (see {@link GlobalStore#evictGuestOwned}), while host values are unaffected. The
 * check runs <b>at write time</b>, while the writing Context is alive, and the invalidation
 * sweep only reads the flag recorded then; it never touches a leftover proxy after the Context
 * closed, because calling asValue on a leftover proxy of a destroyed Context throws.
 */
final class GuestValues {

    private GuestValues() {}

    /** Whether the value is bound to some guest Context: a guest function, a guest object or a Value. */
    static boolean isGuest(Object value) {
        if (value == null) return false;
        if (value instanceof Value) return true;
        // Truffle internal proxies are not relocated; relocation only covers the
        // graal.graalvm.polyglot API package. The other two prefixes are accepted as well, so a
        // future change of the relocation policy cannot silently miss a guest value.
        String className = value.getClass().getName();
        return className.startsWith("com.oracle.truffle.polyglot.")
                || className.startsWith("org.graalvm.polyglot.")
                || className.startsWith("graal.graalvm.polyglot.proxy.");
    }
}
