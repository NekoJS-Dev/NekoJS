package com.tkisor.nekojs.wrapper.registry.gen;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * One registry object type: a <b>named builder factory</b> under a registry. Scripts reach it by name
 * with {@code event.custom(id, '<name>', cb)}. Each registry also has a default type, which
 * {@code event.<registry>(id, cb)} uses without naming a type.
 *
 * <p>Conflicts at the type level use overrideWarn: a third party may override a built-in type and
 * gets a warning. Conflicts at the object level are failFast for a duplicate id in the same registry,
 * and the event repository enforces that.
 *
 * @param name     type name, unique within the registry, such as {@code basic} or {@code tool}
 * @param registry key of the owning registry
 * @param factory  builder factory that takes the object id and returns a {@link RegistryObjectBuilder}
 * @param <B>      builder type
 */
public record RegistryObjectType<B extends RegistryObjectBuilder<?>>(String name, ResourceKey<? extends Registry<?>> registry, Function<Identifier, B> factory) {
}
