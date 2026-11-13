package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Registry runtime for startup. STARTUP scripts declare their registrations through the sole
 * {@link RegistryEvents#REGISTER} caller interface. This class handles <b>collection (exactly
 * once), supplier validation (return value and actual type), definition fingerprinting,
 * per-registry pass draining, and relayed derivation delivery</b>. The node adapters (NeoForge
 * {@code RegistryEventAdapter} / Fabric {@code FabricRegistryAdapter}) only call
 * {@link #drainFor} during the correct registry pass; object creation stays with the platform
 * registration action.
 *
 * <p><b>Epoch semantics</b>: each game startup is one epoch ({@code beginBoot}). Failures or
 * leftovers from an earlier round cannot pollute the next one; a new boot round first discards
 * and diagnoses stale entries from the previous epoch. A builder that throws during collection is
 * never drained as a semi-finished product, because objects are configured before they are
 * stored. Dynamic Registries at server runtime ({@code com.tkisor.nekojs.dynamic} side) have
 * completely independent lifecycles and bypass this path.
 *
 * <p><b>Drain results are observable</b>: {@link DrainResult} carries the definition, registry,
 * node, origin and fingerprint of each registration, plus the error sources; tests and
 * diagnostics read it instead of private repository fields.
 */
public final class StartupRegistryRuntime {

    /** Registration sink for node adapters: hands off (registry, id, supplier) to the platform registration action. */
    @FunctionalInterface
    public interface RegistrySink {

        /**
         * Registers an object. The supplier is wrapped by the Runtime for validation (return value/actual type).
         * On NeoForge, this can be deferred (RegisterEvent's supplier semantics); on Fabric, it executes immediately.
         */
        void register(ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier);
    }

    /** A record of a successful registration observation. */
    public record RegistrationRecord(
            Identifier definition,
            ResourceKey<? extends Registry<?>> registry,
            String node,
            String origin,
            String fingerprint) {}

    /** A record of a failed/skipped registration observation: indicates error source phase (collection/drain/additional-target). */
    public record ErrorRecord(
            Identifier definition,
            ResourceKey<? extends Registry<?>> registry,
            String node,
            String source,
            String message) {}

    /** Drain result for a single pass (immutable snapshot). */
    public record DrainResult(List<RegistrationRecord> registered, List<ErrorRecord> errors) {
        public DrainResult(List<RegistrationRecord> registered, List<ErrorRecord> errors) {
            this.registered = List.copyOf(registered == null ? List.of() : registered);
            this.errors = List.copyOf(errors == null ? List.of() : errors);
        }
    }

    /** Element type tokens for known registries (used for raw Supplier actual type validation; unlisted registries are not validated). */
    private static final Map<ResourceKey<? extends Registry<?>>, Class<?>> ELEMENT_TOKENS = elementTokens();

    private final RegistryRepository repository;
    private final Set<ResourceKey<? extends Registry<?>>> passed = new HashSet<>();
    private final String node;
    private boolean collected;
    private final Map<String, com.tkisor.nekojs.core.registry.ItemAssetDefinition> itemAssets = new java.util.TreeMap<>();

    /** Boot-owned immutable resource declarations survive ordinary script generations. */
    public List<com.tkisor.nekojs.core.registry.ItemAssetDefinition> itemAssetDefinitions() {
        return List.copyOf(itemAssets.values());
    }

    public boolean itemRegistrationComplete() { return passed.contains(Registries.ITEM); }

    /** @param node Node label (e.g., {@code neoforge:26.1.2} / {@code fabric:26.1.2}), included in observation records and error messages. */
    public StartupRegistryRuntime(String node) {
        this(node, new RegistryRepository());
    }

    /** Repository injection form (used by adapters holding cross-pass repositories or when constructing non-bootstrap inputs for tests). */
    public StartupRegistryRuntime(String node, RegistryRepository repository) {
        this.node = node;
        this.repository = repository;
    }

    /** Collection repository; this is where RegistryEventJS writes. */
    public RegistryRepository repository() {
        return repository;
    }

    /** Node label. */
    public String node() {
        return node;
    }

    /**
     * Delivers the collection event exactly once, before the first registry pass. A second call does
     * nothing. {@code EventBusJS.post} catches and records listener exceptions, so one failing
     * listener does not stop the others.
     */
    public void collectOnce() {
        collectOnce(RegistryEventJS.create(repository, node));
    }

    /**
     * Collection entry point that takes an injected event payload, used by adapters and by end-to-end
     * tests. The payload is still an object of the only {@link RegistryEvents#REGISTER} caller
     * interface; tests use it to inject infos/types input that does not come from bootstrap, without
     * bypassing the bus.
     */
    public void collectOnce(RegistryEventJS event) {
        if (collected) {
            return;
        }
        collected = true;
        RegistryEvents.REGISTER.post(event);
    }

    /** Whether the collection event has been delivered in this epoch. */
    public boolean isCollected() {
        return collected;
    }

    /**
     * Drains the main objects and the additional derived entries of one registry. Called once per
     * pass. The main object goes into the sink first; entries that its
     * {@code handleAdditionalObjects} delivers go into the repository and wait for the pass of their
     * target registry. An additional entry whose target pass has already run is recorded as an error
     * and skipped, because the loader registration order cannot be rewound (the same semantics as
     * KubeJS).
     *
     * <p>A failing platform registration action (sink) is isolated per entry: it is recorded in
     * {@link DrainResult#errors()} (source {@code platform-register}, with definition, registry, node
     * and error source) and draining continues, so nothing is lost silently. The entry itself has
     * already been drained from the repository, so no undelivered leftover can pollute a later pass.
     */
    public DrainResult drainFor(ResourceKey<? extends Registry<?>> registry, RegistrySink sink) {
        List<RegistrationRecord> registered = new ArrayList<>();
        List<ErrorRecord> errors = new ArrayList<>();
        for (RegistryRepository.Entry entry : repository.drain(registry)) {
            RegistryObjectBuilder<?> builder = entry.builder();
            registered.add(new RegistrationRecord(
                    builder.id, registry, node, entry.origin(), definitionFingerprint(builder)));
            try {
                var assets = builder instanceof ItemBuilder item ? item.assetDefinition() : null;
                registerWithAssets(sink, registry, builder.id, builder, assets);
            } catch (RuntimeException e) {
                errors.add(new ErrorRecord(builder.id, registry, node, "platform-register", e.getMessage()));
                continue;
            }
            collectAdditionalOf(builder, errors);
        }
        for (RegistryRepository.Additional additional : repository.drainAdditional(registry)) {
            registered.add(new RegistrationRecord(
                    additional.id(), registry, node, "additional:" + additional.source(),
                    "additional:" + additional.source()));
            try {
                var assets = additional.supplier() instanceof ItemRegistrationSupplier item ? item.assets() : null;
                registerWithAssets(sink, registry, additional.id(), additional.supplier(), assets);
            } catch (RuntimeException e) {
                errors.add(new ErrorRecord(additional.id(), registry, node, "platform-register", e.getMessage()));
            }
        }
        // A registry joins passed only after its pass ends, so an additional entry that targets this
        // registry is still a valid registration in the same round.
        passed.add(registry);
        return new DrainResult(registered, errors);
    }

    private void registerWithAssets(RegistrySink sink, ResourceKey<? extends Registry<?>> registry,
                                    Identifier id, Supplier<?> supplier,
                                    com.tkisor.nekojs.core.registry.ItemAssetDefinition assets) {
        Supplier<?> validated = validatedSupplier(id, registry, supplier);
        // A delayed supplier may run after the sink returns, so both stages must succeed.
        class Receipt implements Supplier<Object> {
            boolean accepted;
            boolean produced;
            boolean failed;
            void publish() {
                if (accepted && produced && !failed && assets != null) itemAssets.put(id.toString(), assets);
            }
            @Override public Object get() {
                Object value = validated.get();
                produced = true;
                publish();
                return value;
            }
        }
        Receipt receipt = new Receipt();
        try {
            sink.register(registry, id, receipt);
            receipt.accepted = true;
            receipt.publish();
        } catch (RuntimeException error) {
            receipt.failed = true;
            throw error;
        }
    }

    /**
     * Drain-time callback: the additional entries of a builder go into the repository and wait for
     * the pass of their target registry.
     */
    private void collectAdditionalOf(RegistryObjectBuilder<?> builder, List<ErrorRecord> errors) {
        try {
            builder.handleAdditionalObjects((registry, id, supplier) -> {
                if (passed.contains(registry)) {
                    errors.add(new ErrorRecord(id, registry, node, "additional-target",
                            "additional object of '" + builder.id + "' targets registry '" + registry.identifier()
                                    + "' whose registration already passed; NOT registered"));
                    return;
                }
                repository.addAdditional(registry, id, supplier, builder.id);
            });
        } catch (Exception e) {
            errors.add(new ErrorRecord(builder.id, null, node, "additional-collect",
                    "failed to collect additional objects: " + e.getMessage()));
        }
    }

    /**
     * Wraps a raw supplier with validation. It lets the value through when the returned value is not
     * null and its actual type matches a known token. It does not fingerprint or roll back the side
     * effects of an arbitrary supplier, and Dynamic Registries are not opened on that basis. For a
     * registry without a registered token it only checks that the value is not null.
     */
    public <T> Supplier<T> validatedSupplier(Identifier id, ResourceKey<? extends Registry<?>> registry, Supplier<T> supplier) {
        return () -> {
            T value = supplier.get();
            if (value == null) {
                throw new IllegalStateException("registry supplier for '" + id + "' in registry '"
                        + registry.identifier() + "' (node " + node + ") returned null");
            }
            Class<?> token = ELEMENT_TOKENS.get(registry);
            if (token != null && !token.isInstance(value)) {
                throw new IllegalStateException("registry supplier for '" + id + "' in registry '"
                        + registry.identifier() + "' (node " + node + ") returned " + value.getClass().getName()
                        + " but this registry holds " + token.getName());
            }
            return value;
        };
    }

    /**
     * Definition fingerprint: the registry, the id, the type name and the <b>normalized</b> readings of
     * all writable properties ({@link RegistryBuilderContract#normalizedPropertyReadings}, in
     * lexicographic order). An explicit setter and a property assignment both end in the same setter,
     * so their fingerprints match. Additional objects are built lazily, and the fingerprint only
     * covers startup declarations it can express; raw suppliers do not take part.
     */
    public String definitionFingerprint(RegistryObjectBuilder<?> builder) {
        StringBuilder canonical = new StringBuilder("v1|");
        canonical.append(builder.id).append('|');
        RegistryBuilderContract.of(builderClassOf(builder)).normalizedPropertyReadings(builder)
                .forEach(reading -> canonical.append(reading).append(';'));
        return sha256(canonical.toString());
    }

    /**
     * Read-only live view of the main objects and additional entries that are not drained yet, for
     * diagnostics. It is not a frozen result: it returns a defensive copy and later drains change the
     * real state. For content that is already drained, the {@link DrainResult} snapshot is the
     * authority.
     */
    public Map<ResourceKey<? extends Registry<?>>, List<Identifier>> undrainedLiveView() {
        Map<ResourceKey<? extends Registry<?>>, List<Identifier>> view = new LinkedHashMap<>();
        repository.undrained().forEach((registry, entries) -> {
            List<Identifier> ids = new ArrayList<>();
            entries.forEach(entry -> ids.add(entry.builder().id));
            view.put(registry, List.copyOf(ids));
        });
        return view;
    }

    /**
     * Load-complete diagnostic: reports content that was collected but no pass consumed, because the
     * registry name is wrong or the target pass ran before the source pass.
     */
    public void reportUndelivered(Consumer<String> logger) {
        repository.undrained().forEach((registry, entries) -> entries.forEach(entry -> logger.accept(
                "Registry '" + registry.identifier() + "' (node " + node + ") collected '" + entry.builder().id
                        + "' (from " + entry.origin() + ") but its registration pass never fired; content NOT registered")));
        repository.undeliveredAdditional().forEach((registry, additionals) -> additionals.forEach(additional -> logger.accept(
                "Registry '" + registry.identifier() + "' (node " + node + ") has undelivered additional '"
                        + additional.id() + "' of '" + additional.source() + "'; content NOT registered")));
    }

    /** Whether this epoch is fully drained, that is, no undelivered content remains. */
    public boolean isFullyDrained() {
        return repository.isEmpty();
    }

    /**
     * Snapshot of the registry keys that still hold undrained content, main objects or additional
     * entries. Fabric direct batch registration takes the key set first and then drains one registry
     * at a time, so draining does not change the structure being walked.
     */
    public Set<ResourceKey<? extends Registry<?>>> snapshotUndrainedRegistries() {
        Set<ResourceKey<? extends Registry<?>>> keys = new java.util.LinkedHashSet<>();
        keys.addAll(repository.undrained().keySet());
        keys.addAll(repository.undeliveredAdditional().keySet());
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends RegistryObjectBuilder<?>> builderClassOf(RegistryObjectBuilder<?> builder) {
        return (Class<? extends RegistryObjectBuilder<?>>) builder.getClass();
    }

    private static Map<ResourceKey<? extends Registry<?>>, Class<?>> elementTokens() {
        Map<ResourceKey<? extends Registry<?>>, Class<?>> tokens = new LinkedHashMap<>();
        tokens.put(Registries.ITEM, Item.class);
        tokens.put(Registries.BLOCK, Block.class);
        tokens.put(Registries.FLUID, Fluid.class);
        return tokens;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
