package dev.latvian.mods.rhino.type;

/** Unbounded metadata is distinct from an explicitly declared Object and does not acquire type arguments. */
enum UnknownTypeInfo implements TypeInfo {
    INSTANCE;

    @Override public Class<?> asClass() { return Object.class; }
    @Override public TypeInfo param(int index) { return this; }
    @Override public TypeInfo withParams(TypeInfo... parameters) { return this; }
    @Override public String toString() { return "?"; }
}
