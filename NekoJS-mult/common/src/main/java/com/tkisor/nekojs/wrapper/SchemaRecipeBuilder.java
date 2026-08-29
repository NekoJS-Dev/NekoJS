package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schema-driven fluent recipe builder exposed to GraalJS.
 *
 * <p>Wraps a {@link RecipeBuilder} (platform {@code RecipeJsonBuilder}) together with its
 * {@link RecipeTypeDefinition}, so that every schema field becomes a chainable setter
 * (e.g. {@code .experience(0.5).cookingtime(100)}) on top of the delegate's own fluent methods
 * ({@code .id()/.group()/.setPath()/.validate() ...}). This closes the gap between the
 * probe-generated {@code .d.ts} (which already declares these per-schema setters) and the runtime,
 * where they previously resolved to {@code undefined}.
 *
 * <p><b>Why this is not the EventProxy problem.</b> EventProxy wrapped a domain object that was
 * already reachable through GraalJS native host access, fully took over {@code getMember}, and
 * hand-listed only some members — silently dropping native methods like {@code getEntity()}.
 * This wrapper differs on all three counts:
 * <ol>
 *   <li>the wrapped builder is a construction-time transient; under the schema path it is
 *       reachable from JS <em>only</em> through this wrapper — there is no competing direct-
 *       exposure path whose members could be missed;</li>
 *   <li>{@code getMember} resolves over two deterministic, compile-time-enumerable key sets —
 *       the schema field table and the delegate's full reflected public method table — with no
 *       hand-written member list that could be incomplete;</li>
 *   <li>the "reflect the target's public methods once and cache them" shape is identical to the
 *       already-accepted {@code RecipeNamespaceProxy.reflectMethods(handler)}.</li>
 * </ol>
 *
 * <p>Resolution priority is <b>delegate method &gt; schema field</b>: delegate methods like
 * {@code id()}/{@code group()} carry side effects (id rehashes the recipe map); a schema field
 * shadowed by a same-named method is still reachable via {@code setPath(field.path(), value)}.
 * Name collisions are logged once at construction.
 *
 * <p>All ProxyObject methods are invoked by GraalVM dispatch, not by direct Java callers.
 */
@Doc("Schema-driven fluent recipe builder: each schema field becomes a chainable setter.")
final class SchemaRecipeBuilder implements ProxyObject {

    private final RecipeBuilder delegate;
    private final RecipeTypeDefinition def;
    private final RecipeSchemaHost host;
    private final Map<String, List<Method>> delegateMethods;
    private final Map<String, ProxyExecutable> memberCache;
    private final Set<String> allKeys;

    /**
     * Build a wrapper over {@code delegate}, applying {@code initialValues} (resolved from the
     * schema constructor/named-object call) to the delegate's JSON once, exactly as the previous
     * inline construction did.
     */
    SchemaRecipeBuilder(RecipeBuilder delegate, RecipeTypeDefinition def, RecipeSchemaHost host,
                        Map<String, Value> initialValues) {
        this.delegate = delegate;
        this.def = def;
        this.host = host;
        this.delegateMethods = RecipeReflectionUtil.reflectMethods(delegate);

        Set<String> keys = new LinkedHashSet<>(delegateMethods.keySet());
        keys.addAll(def.fields().keySet());
        this.allKeys = keys;
        this.memberCache = new LinkedHashMap<>();

        for (RecipeFieldDefinition field : def.fields().values()) {
            Value value = initialValues != null ? initialValues.get(field.name()) : null;
            if (value != null) {
                host.applyField(delegate, field, value);
            } else if (field.defaultValue() != null) {
                delegate.setPath(field.path(), new RecipeJsonValue(field.defaultValue().deepCopy()));
            } else if (field.required()) {
                throw new IllegalArgumentException("Missing required field '" + field.name() + "' for " + def.key());
            }
        }

        for (String field : def.fields().keySet()) {
            if (delegateMethods.containsKey(field)) {
                NekoJS.LOGGER.debug("Schema field '{}' of {} is shadowed by a delegate method on {}; use setPath('{}', v) to write the field",
                        field, def.key(), delegate.getClass().getName(), field);
            }
        }
    }

    // ==================== ProxyObject ====================

    /** Cached lookup: delegate methods first, then schema field setters; null when unknown. */
    @Override @CalledByDynamicCode
    @Doc("Returns a chainable executable: a forwarded delegate method, or a schema field setter.")
    @Param(name = "key", value = "delegate method name or schema field name")
    @Return("an executable returning this builder to keep the chain; null for unknown names")
    public Object getMember(String key) {
        ProxyExecutable cached = memberCache.get(key);
        if (cached != null) return cached;
        ProxyExecutable resolved = resolveMember(key);
        if (resolved != null) memberCache.put(key, resolved);
        return resolved;
    }

    /** All enumerable keys: the delegate's method names plus the schema field names. */
    @Override @CalledByDynamicCode
    @Doc("Lists delegate method names plus schema field names.")
    @Return("array of all supported member names")
    public Object getMemberKeys() {
        return allKeys.toArray(String[]::new);
    }

    @Override
    @Doc("True only for delegate method names and schema field names.")
    public boolean hasMember(String key) {
        return allKeys.contains(key);
    }

    @Override
    @Doc("Throws: the recipe builder is read-only.")
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Recipe builder is read-only");
    }

    // ==================== Resolution: delegate method first, then schema field ====================

    /** Picks the executable for a key: a delegate overload list when present, otherwise the
     *  schema field setter, otherwise null (unknown member). */
    private ProxyExecutable resolveMember(String key) {
        List<Method> methods = delegateMethods.get(key);
        if (methods != null && !methods.isEmpty()) {
            return delegateExecutable(methods);
        }
        RecipeFieldDefinition field = def.fields().get(key);
        if (field != null) {
            return fieldExecutable(field);
        }
        return null;
    }

    /** Invokes the first arity/convertibility-matching delegate overload, rebinding a
     *  self-returning delegate result to this wrapper so chains stay on the JS-visible type. */
    private ProxyExecutable delegateExecutable(List<Method> methods) {
        return args -> {
            for (Method m : methods) {
                int total = m.getParameterCount();
                int required = RecipeReflectionUtil.requiredParamCount(m);
                if (args.length >= required && args.length <= total) {
                    try {
                        Object[] converted = RecipeReflectionUtil.convertArgs(m, args, total);
                        Object result = m.invoke(delegate, converted);
                        // keep the fluent chain on the wrapper: a delegate method that returns
                        // itself (setPath/id/group/validate...) is reborn as this wrapper.
                        return result == delegate ? this : result;
                    } catch (Exception ignored) {
                        // arity matched but arg conversion failed → try next overload
                    }
                }
            }
            throw new IllegalArgumentException("No overload of '" + methods.get(0).getName()
                    + "' accepts " + args.length + " argument(s) on " + def.key());
        };
    }

    /** Builds the single-argument setter that applies a schema field through the host and
     *  returns this wrapper for chaining. */
    private ProxyExecutable fieldExecutable(RecipeFieldDefinition field) {
        return args -> {
            if (args.length != 1) {
                throw new IllegalArgumentException("Schema field '" + field.name()
                        + "' setter expects exactly 1 argument, got " + args.length);
            }
            host.applyField(delegate, field, args[0]);
            return this;
        };
    }
}
