package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.Type;

/**
 * A type slot: holds the original Java {@link Type} and the language-neutral {@link ApiTypeRef}.
 *
 * <p>Why both:
 * <ul>
 *   <li><b>Default TS rendering</b> (slot not edited): uses {@code TypeConverter.toTypeScript(sourceType)},
 *       which produces the same text as {@code ClassDeclGenerator}, so TS output does not change.</li>
 *   <li><b>Python rendering, and after a modify_type edit</b>: uses {@link #ref} ({@link ApiTypeRef}),
 *       which is language-neutral and can be changed by the event.</li>
 * </ul>
 * When {@link #overridden} is true (modify_type changed this slot), the renderer renders {@link #ref};
 * otherwise it renders {@link #sourceType}. A synthetic member (modify_type addMethod and friends)
 * has a null {@code sourceType} and must go through {@code ref}.
 */
public final class TypeSlot {
    public final Type sourceType;   // Original Java type; null for synthetic members
    public ApiTypeRef ref;          // Language-neutral type, never null
    /** Explicit input metadata for the TypeScript backend. */
    public AdapterInputShape inputShape;
    /** Explicit TypeScript output expression; other backends retain the Java type. */
    public String outputTypeScript;
    public boolean overridden;      // modify_type has changed ref

    public TypeSlot(Type sourceType, ApiTypeRef ref) {
        this.sourceType = sourceType;
        this.ref = ref;
    }

    public static TypeSlot of(Type sourceType, ApiTypeRef ref) {
        return new TypeSlot(sourceType, ref);
    }
}
