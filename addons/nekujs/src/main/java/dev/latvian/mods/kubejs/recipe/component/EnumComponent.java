package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.latvian.mods.rhino.type.EnumTypeInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

public record EnumComponent<T extends Enum<T> & StringRepresentable>(RecipeComponentType<?> typeOverride, EnumTypeInfo typeInfo, Codec<T> codec) implements RecipeComponent<T> {
    public static final RecipeComponentType<?> TYPE = RecipeComponentType.dynamic(
            ResourceLocation.fromNamespaceAndPath("kubejs", "enum"),
            Codec.STRING.comapFlatMap(EnumComponent::resolveEnum, info -> info.asClass().getName())
                    .fieldOf("enum").xmap(EnumComponent::fromTypeInfo, EnumComponent::typeInfo));

    private static DataResult<EnumTypeInfo> resolveEnum(String name) {
        try {
            Class<?> type = Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            if (type.isEnum() && StringRepresentable.class.isAssignableFrom(type)) {
                return DataResult.success((EnumTypeInfo) TypeInfo.of(type));
            }
            return DataResult.error(() -> "NEKO-4234: Recipe enum must implement StringRepresentable: " + name);
        } catch (ClassNotFoundException ex) {
            return DataResult.error(() -> "NEKO-4235: Recipe enum class was not found: " + name);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EnumComponent<?> fromTypeInfo(EnumTypeInfo info) {
        Codec codec = Codec.STRING.comapFlatMap(name -> {
            for (Object constant : info.enumConstants()) {
                if (EnumTypeInfo.getName(constant).equalsIgnoreCase(name)
                        || ((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                    return DataResult.success(constant);
                }
            }
            return DataResult.error(() -> "NEKO-4236: Unknown recipe enum value: " + info.asClass().getName() + ":" + name);
        }, EnumTypeInfo::getName);
        return new EnumComponent(null, info, codec);
    }

    public static <T extends Enum<T> & StringRepresentable> RecipeComponentType<T> of(ResourceLocation id, Class<T> enumClass, Codec<T> codec) {
        return RecipeComponentType.unit(id, type -> new EnumComponent<>(type, (EnumTypeInfo) TypeInfo.of(enumClass), codec));
    }
    public static <T extends Enum<T> & StringRepresentable> RecipeComponentType<T> of(ResourceLocation id, Class<T> enumClass) {
        return of(id, enumClass, StringRepresentable.fromEnum(enumClass::getEnumConstants));
    }
    @Override public RecipeComponentType<?> type() { return typeOverride == null ? TYPE : typeOverride; }
    @Override public boolean isEmpty(T value) { return value == null; }
    @Override public void buildUniqueId(UniqueIdBuilder builder, T value) { builder.append(EnumTypeInfo.getName(value)); }
    @Override public String toString() { return typeOverride == null ? "enum<" + typeInfo.asClass().getName() + ">" : typeOverride.toString(); }
}
