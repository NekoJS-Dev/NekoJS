package com.tkisor.nekojs.api.catalog;

import java.util.List;

/**
 * A structured catalog entry for a typed builder surface. It is derived from reflecting the
 * builder classes against the contract in the version tree ({@code RegistryBuilderContract}), and
 * the <b>same contract input</b> drives both the TS declaration (probe TS backend) and the Python
 * declaration (probe Python backend): each backend only renders and keeps no member table of
 * its own.
 *
 * <p>Unlike {@link ManualDeclarationCatalogEntry}, which holds hand-written strings, this entry
 * carries structured facts: the member names, their kinds (writable property, read-only property,
 * method) and the type annotations on both sides all come from one reflection result. common
 * introduces no Minecraft, loader or Graal types, so the reflection input is produced in the
 * version tree and this record only carries data.
 *
 * @param builderName    builder type name on both sides (such as {@code ItemBuilder}), empty for event-only entries
 * @param registryKey    full registry key (such as {@code minecraft:item})
 * @param typeName       registry type name (such as {@code basic})
 * @param sugarName      script sugar method name (such as {@code item}); only the default type has one
 * @param members        contract members in name order; each method overload has its own entry
 * @param description    description used in probe documentation
 * @param eventPayload   optional declaration-only event association; never grants host lookup access
 * @param nestedBuilders callback-only helper surfaces derived from the same host contracts; not registry factories
 */
public record RegistryBuilderSurfaceEntry(
        String builderName,
        String registryKey,
        String typeName,
        String sugarName,
        List<Member> members,
        String description,
        EventPayload eventPayload,
        List<RegistryBuilderSurfaceEntry> nestedBuilders) {

    /** Preserves the catalog shape for contributors without nested callback builders. */
    public RegistryBuilderSurfaceEntry(String builderName, String registryKey, String typeName,
                                       String sugarName, List<Member> members, String description,
                                       EventPayload eventPayload) {
        this(builderName, registryKey, typeName, sugarName, members, description, eventPayload, List.of());
    }

    public RegistryBuilderSurfaceEntry(String builderName, String registryKey, String typeName,
                                       String sugarName, List<Member> members, String description) {
        this(builderName, registryKey, typeName, sugarName, members, description, null);
    }

    public RegistryBuilderSurfaceEntry {
        members = List.copyOf(members == null ? List.of() : members);
        nestedBuilders = List.copyOf(nestedBuilders == null ? List.of() : nestedBuilders);
    }

    /** Carries a declaration for events whose factories supply no concrete builder classes. */
    public static RegistryBuilderSurfaceEntry eventOnly(EventPayload payload) {
        return new RegistryBuilderSurfaceEntry("", "", "", null, List.of(), "", payload);
    }

    public boolean hasBuilder() {
        return !builderName.isEmpty();
    }

    /** Member kind: writable property (both setter forms), read-only property, or method (including an explicit setter). */
    public enum MemberKind { WRITABLE_PROPERTY, READ_ONLY_PROPERTY, METHOD }

    /**
     * One member of the builder.
     *
     * @param name   member name, either a property name or a method name
     * @param kind   member kind
     * @param tsType TS shape: a type annotation for a property, the full signature line for a
     *               method (such as {@code food(cb: (f: any) => void): void})
     * @param pyType Python shape: a type annotation for a property, the full signature line for a
     *               method, derived from the same reflection input as TS
     */
    public record Member(String name, MemberKind kind, String tsType, String pyType) {}

    /**
     * Associate script-only members with an event's host identity without reflecting or exposing that host.
     * Entries for the same event and payload name contribute members to one declaration in each backend.
     * A nonempty TypeScript builder namespace isolates associated types from legacy global declarations;
     * Each scoped variant maps accepted selector spellings to one declaration. The older constructors
     * describe unscoped payloads, including those used by dynamic registry events.
     */
    public record EventPayload(Class<?> eventType, String name, List<Member> members,
                               String typescriptBuilderNamespace, List<EventVariant> variants) {
        public EventPayload(Class<?> eventType, String name, List<Member> members) {
            this(eventType, name, members, null);
        }

        public EventPayload(Class<?> eventType, String name, List<Member> members, String typescriptBuilderNamespace) {
            this(eventType, name, members, typescriptBuilderNamespace, List.of());
        }

        public static EventPayload scoped(Class<?> eventType, String typescriptBuilderNamespace,
                                          List<EventVariant> variants) {
            return new EventPayload(eventType, null, List.of(), typescriptBuilderNamespace, variants);
        }

        public EventPayload {
            members = List.copyOf(members == null ? List.of() : members);
            variants = List.copyOf(variants);
        }

        public boolean selectorScoped() {
            return name == null || !variants.isEmpty();
        }

        public List<String> declarationNames() {
            return selectorScoped() ? variants.stream().map(EventVariant::typeName).toList() : List.of(name);
        }
    }

    /** One callback type and the literal selectors that select it. */
    public record EventVariant(List<String> selectors, String typeName, List<Member> members) {
        public EventVariant {
            selectors = List.copyOf(selectors);
            members = List.copyOf(members);
        }
    }
}
