package dev.latvian.mods.rhino.type;

import net.minecraft.util.StringRepresentable;

/** Native enum metadata used by legacy recipe codecs. */
public final class EnumTypeInfo implements TypeInfo {
    private final Class<?> type;
    public EnumTypeInfo(Class<?> type) { this.type = type; }
    @Override public Class<?> asClass() { return type; }
    public Object[] enumConstants() { return type.getEnumConstants().clone(); }
    public static String getName(Object value) {
        return value instanceof StringRepresentable named ? named.getSerializedName() : ((Enum<?>) value).name();
    }
}
