package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.capability.CapabilityStatus;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentScope;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A normalized API contract document, held in memory as the record side of the contract JSON.
 *
 * <p>It describes the symbols, capabilities, modules and events one owner exposes at a given
 * version. The container fields are copied into immutable lists on construction;
 * {@link #schemaVersion} identifies the schema version of the document.
 *
 * @param schemaVersion contract schema version
 * @param identity      contract identity
 * @param docs          contract documentation
 * @param symbols       top-level API symbols
 * @param capabilities  declared capabilities
 * @param modules       modules
 * @param events        events
 */
public record NormativeApiContract(
        int schemaVersion,
        ContractIdentity identity,
        String docs,
        List<ApiSymbol> symbols,
        List<ContractCapability> capabilities,
        List<ContractModule> modules,
        List<ContractEvent> events
) {
    public NormativeApiContract {
        Objects.requireNonNull(identity, "identity");
        symbols = List.copyOf(symbols == null ? List.of() : symbols);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        modules = List.copyOf(modules == null ? List.of() : modules);
        events = List.copyOf(events == null ? List.of() : events);
    }

    /** Convenience constructor for a contract with no events. */
    public NormativeApiContract(
            int schemaVersion,
            ContractIdentity identity,
            String docs,
            List<ApiSymbol> symbols,
            List<ContractCapability> capabilities,
            List<ContractModule> modules) {
        this(schemaVersion, identity, docs, symbols, capabilities, modules, List.of());
    }

    /** The identity of the contract. */
    public record ContractIdentity(String owner, ApiContractKind kind, String contractId, ApiVersion version) {
        public ContractIdentity {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(contractId, "contractId");
            Objects.requireNonNull(version, "version");
        }
    }

    /**
     * A capability declaration: the capability id, the range of contract versions it supports, its
     * three-state result ({@link CapabilityStatus}), the conditions under which it applies
     * (loader, version, runtime context; {@link EnvironmentScope}, where {@code null} means
     * unconditional) and its documentation.
     *
     * <p>The conditions really apply during surface resolution: {@code JsApiSurfaceResolver}
     * passes them into {@code CapabilityDefinition}, and {@code CapabilityResolver} decides
     * activation or explicit unavailability through EnvironmentScope.matches. They are not
     * documentation-only fields.
     */
    public record ContractCapability(
            String id,
            String contractVersionRange,
            com.tkisor.nekojs.api.capability.CapabilityStatus status,
            EnvironmentScope conditions,
            String docs) {
        public ContractCapability {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(contractVersionRange, "contractVersionRange");
            status = status == null ? CapabilityStatus.SUPPORTED : status;
        }

        /** Compatibility constructor taking only id, version range and docs: SUPPORTED and unconditional. */
        public ContractCapability(String id, String contractVersionRange, String docs) {
            this(id, contractVersionRange,
                    CapabilityStatus.SUPPORTED, null, docs);
        }
    }

    /** A module declaration: module id, script type, contract version, revision, docs, symbols and dependencies. */
    public record ContractModule(
            String id,
            com.tkisor.nekojs.api.surface.ApiTier tier,
            ApiVersion contractVersion,
            int moduleRevision,
            String docs,
            List<ApiSymbol> symbols,
            List<ContractModuleDependency> dependencies
    ) {
        public ContractModule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(tier, "tier");
            symbols = List.copyOf(symbols == null ? List.of() : symbols);
            dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        }
    }

    /** A module dependency: the target module id, version range and target script tier. */
    public record ContractModuleDependency(
            String moduleId,
            String versionRange,
            com.tkisor.nekojs.api.surface.ApiTier targetTier
    ) {
        public ContractModuleDependency {
            Objects.requireNonNull(moduleId, "moduleId");
        }
    }

    /**
     * An event contract entry: it fixes the stable semantics of one event on supported platforms
     * (existence, dispatch, cancellability and the portable payload view).
     *
     * @param group event group name (such as {@code "ServerEvents"}, as given to {@code EventGroup.of(...)})
     * @param name event name (such as {@code "started"}; scripts call {@code ServerEvents.started(...)})
     * @param tier script type: STARTUP (startup scripts) / SERVER (server scripts) / CLIENT (client scripts)
     * @param dispatch dispatch semantics: PLAIN (no dispatch key) / BY_ID (dispatch by string id)
     * @param dispatchKeyType key type for BY_ID (always {@code "string"}: script keys are string ids)
     * @param cancellable three-state: {@code true}/{@code false} promises; {@code null} means platforms differ
     * @param payload portable payload view (field names follow the script-side property form; may be empty)
     * @param docs            documentation
     */
    public record ContractEvent(
            String group,
            String name,
            EventTier tier,
            Dispatch dispatch,
            String dispatchKeyType,
            Boolean cancellable,
            List<ContractEventField> payload,
            String docs
    ) {
        public ContractEvent {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(dispatch, "dispatch");
            if (dispatch == Dispatch.BY_ID && (dispatchKeyType == null || dispatchKeyType.isBlank())) {
                throw new IllegalArgumentException("BY_ID event requires dispatchKeyType: " + group + "." + name);
            }
            if (dispatch == Dispatch.PLAIN && dispatchKeyType != null) {
                throw new IllegalArgumentException("PLAIN event must not have dispatchKeyType: " + group + "." + name);
            }
            payload = List.copyOf(payload == null ? List.of() : payload);
        }
    }

    /** Script type of the event. */
    public enum EventTier {
        STARTUP, SERVER, CLIENT
    }

    /** Dispatch semantics of the event. */
    public enum Dispatch {
        PLAIN, BY_ID
    }

    /**
     * An event payload field: {@link #kind() PORTABLE} means the field value has a cross-platform
     * portable type described by {@link #portType()}; {@link #kind() NATIVE} means the field name is
     * stable across platforms while its value is a platform-native MC object, so the type is not
     * promised.
     */
    public record ContractEventField(
            String name,
            FieldKind kind,
            ApiTypeRef portType,
            String docs
    ) {
        public ContractEventField {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            if (kind == FieldKind.PORTABLE && portType == null) {
                throw new IllegalArgumentException("PORTABLE field requires portType: " + name);
            }
            if (kind == FieldKind.NATIVE && portType != null) {
                throw new IllegalArgumentException("NATIVE field must not have portType: " + name);
            }
        }
    }

    public enum FieldKind {
        PORTABLE, NATIVE
    }
}
