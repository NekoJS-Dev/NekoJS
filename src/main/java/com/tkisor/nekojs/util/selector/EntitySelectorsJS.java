package com.tkisor.nekojs.util.selector;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;

/**
 * Global {@code EntitySelectors} binding: the factory and query entry point for
 * {@link EntitySelectorBuilderJS}.
 *
 * <p>A query binding: the factory, builder and query only read, they never fire events and have no
 * lifecycle Point. Queries need a {@code ServerLevel}, so they are limited to the server and test
 * side. Invalid input throws an ordinary error that carries the domain and the call entry point
 * (the source position comes from the script stack through the shared error pipeline); it carries
 * no fix hint.
 *
 * <p><b>Scope base</b>: {@link #builder()} and {@link #create} start from a <b>player set</b>
 * ({@code includesEntities=false}, equivalent to {@code @a}); {@link #allEntities()} /
 * {@link #nearestEntity()} / {@link #randomEntity()} start from an <b>entity set</b>.
 * {@code type(...)} and {@code typeTag(...)} do <b>not</b> change the scope (only a player type
 * adjusts that bit), so finding a non-player entity requires an entity base preset.
 *
 * <p>Script example:
 * <pre>{@code
 * // Five cows within 32 blocks, sorted by distance: entity base + type filter
 * const cows = EntitySelectors.find(level,
 *     EntitySelectors.allEntities().type('minecraft:cow').distance(0, 32).limit(5).create());
 * // The nearest player
 * const players = EntitySelectors.find(level, EntitySelectors.nearestPlayer().create(),
 *     player.x, player.y, player.z);
 * }</pre>
 *
 * <p>The {@code find} anchor (x/y/z) is both the origin for distance measurement and the anchor of
 * the volume selection; when it is not passed it defaults to {@code (0, 0, 0)}. The builder's
 * {@code x()/y()/z()} override the matching component of the anchor (vanilla selector semantics).
 * Always pass the anchor explicitly for ordering selectors (nearest/furthest/random).
 */
public class EntitySelectorsJS {

    /** Creates a selector from a builder config callback, e.g. {@code create(b => b.type('minecraft:cow'))}. */
    public EntitySelector create(Consumer<EntitySelectorBuilderJS> config) {
        if (config == null) {
            throw new IllegalArgumentException("EntitySelectors.create: config must not be null");
        }
        var builder = new EntitySelectorBuilderJS();
        config.accept(builder);
        return builder.create();
    }

    /** Returns an empty builder for chained configuration. */
    public EntitySelectorBuilderJS builder() {
        return new EntitySelectorBuilderJS();
    }

    /** All players ({@code @a}). */
    public EntitySelectorBuilderJS allPlayers() {
        return EntitySelectorBuilderJS.allPlayers();
    }

    /** All entities, players included ({@code @e}). */
    public EntitySelectorBuilderJS allEntities() {
        return EntitySelectorBuilderJS.allEntities();
    }

    /** The nearest player ({@code @p}). */
    public EntitySelectorBuilderJS nearestPlayer() {
        return EntitySelectorBuilderJS.nearestPlayer();
    }

    /** The nearest entity, players included. */
    public EntitySelectorBuilderJS nearestEntity() {
        return EntitySelectorBuilderJS.nearestEntity();
    }

    /** A random player ({@code @r}). */
    public EntitySelectorBuilderJS randomPlayer() {
        return EntitySelectorBuilderJS.randomPlayer();
    }

    /** A random entity, players included. */
    public EntitySelectorBuilderJS randomEntity() {
        return EntitySelectorBuilderJS.randomEntity();
    }

    /**
     * Runs a selector in the given level and returns the matching entities, players included.
     * A selector built with {@code includesEntities=false} only finds players. The anchor
     * defaults to {@code (0, 0, 0)}.
     */
    public List<? extends Entity> find(ServerLevel level, EntitySelector selector) {
        return find(level, selector, 0, 0, 0);
    }

    /** Runs a selector in the given level with anchor {@code (x, y, z)}, used for distance and
     * volume selection. */
    public List<? extends Entity> find(ServerLevel level, EntitySelector selector, double x, double y, double z) {
        if (level == null) {
            throw new IllegalArgumentException("EntitySelectors.find: level must not be null");
        }
        if (selector == null) {
            throw new IllegalArgumentException("EntitySelectors.find: selector must not be null");
        }
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(new Vec3(x, y, z));
        try {
            return selector.findEntities(source);
        } catch (Exception e) {
            // findEntities declares CommandSyntaxException, but a selector built by a script
            // (usesSelector=false) never takes the syntax or permission path; this catch is a
            // defensive fallback that turns the failure into a runtime exception
            throw new IllegalStateException("EntitySelectors.find: entity selector query failed", e);
        }
    }

    /**
     * Resolves an entity type tag id (such as {@code 'minecraft:skeletons'}) to a {@link TagKey}.
     *
     * <p>This method does <b>not</b> check that the current data pack declares the tag. An
     * undeclared tag resolves to a {@link TagKey} with no members, so any filter that uses it is
     * always false and the query always returns nothing (a silent no-op).
     *
     * <p>That silent no-op is a known gap. Throwing a normal error that names the domain and the
     * entry point would fix it, but that turns a silently empty result into an error, which is a
     * public behavior change, so it needs a maintainer decision instead of being done here.
     *
     * <p>The only input validation kept is {@code null}: {@code tagId == null} throws a normal
     * error that names the domain and the entry point, the same prefix used by the
     * {@code distance} and limit messages, instead of letting {@code Identifier.parse(null)}
     * throw a bare NPE. This changes no observable behavior, since a null id was already an
     * error path; only the error type and its readability change. The behavior is covered by
     * {@code EntitySelectorsQueryBindingTest.typeTagNullErrorCarriesDomainAndEntry}.
     */
    static TagKey<EntityType<?>> resolveEntityTypeTag(String tagId) {
        if (tagId == null) {
            throw new IllegalArgumentException("EntitySelectors.typeTag: tag must not be null");
        }
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(tagId));
    }
}
