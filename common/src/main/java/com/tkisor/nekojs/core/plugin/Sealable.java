package com.tkisor.nekojs.core.plugin;

/**
 * Implemented by an accumulator that can be sealed: after finish, further collection is rejected.
 *
 * <p>Once the finisher of an extension point returns, bootstrap calls {@link #seal()} if the
 * accumulator implements this interface. From then on any further collection (a late register
 * call, a reference that leaked out) throws {@link IllegalStateException}. The finisher's output
 * is an immutable snapshot; sealing exists so that writes after finish fail loudly instead of
 * being silently dropped.
 */
public interface Sealable {

    /** Seals the accumulator: after this call every collection call must throw {@link IllegalStateException}. */
    void seal();
}
