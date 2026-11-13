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
 * @param builderName    simple name of the builder (such as {@code ItemBuilder}), also the type name on both sides
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
     * the three-argument constructor retains global builder names for existing contributors.
     */
    public record EventPayload(Class<?> eventType, String name, List<Member> members,
                               String typescriptBuilderNamespace) {
        public EventPayload(Class<?> eventType, String name, List<Member> members) {
            this(eventType, name, members, null);
        }

        public EventPayload {
            members = List.copyOf(members == null ? List.of() : members);
        }
    }
}
