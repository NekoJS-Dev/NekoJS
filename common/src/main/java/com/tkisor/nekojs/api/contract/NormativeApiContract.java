package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiVersion;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 规范化的 API 契约文档（契约 JSON 的内存表示）。
 *
 * <p>描述一个 owner 在指定版本下暴露的符号、能力、模块与事件。容器字段在构造时被拷贝为
 * 不可变列表；{@link #schemaVersion} 标识契约文档的 schema 版本。
 *
 * @param schemaVersion 契约 schema 版本
 * @param identity      契约身份
 * @param docs          契约说明
 * @param symbols       顶层 API 符号
 * @param capabilities  声明的能力
 * @param modules       模块
 * @param events        事件
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

    /** 便捷构造：无事件列表的契约。 */
    public NormativeApiContract(
            int schemaVersion,
            ContractIdentity identity,
            String docs,
            List<ApiSymbol> symbols,
            List<ContractCapability> capabilities,
            List<ContractModule> modules) {
        this(schemaVersion, identity, docs, symbols, capabilities, modules, List.of());
    }

    /** 契约身份。 */
    public record ContractIdentity(String owner, ApiContractKind kind, String contractId, ApiVersion version) {
        public ContractIdentity {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(contractId, "contractId");
            Objects.requireNonNull(version, "version");
        }
    }

    /** 能力声明：一个能力 id、其支持的契约版本范围与说明。 */
    public record ContractCapability(String id, String contractVersionRange, String docs) {
        public ContractCapability {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(contractVersionRange, "contractVersionRange");
        }
    }

    /** 模块声明：模块 id、脚本类型、契约版本、修订号、说明、符号与依赖。 */
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

    /** 模块依赖：目标模块 id、版本范围与目标脚本类型。 */
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
     * 事件契约条目：约定一个事件在受支持平台上的稳定语义（存在性、分发、可取消性、可移植 payload 视图）。
     *
     * @param group           事件组名（如 {@code "ServerEvents"}，对应 {@code EventGroup.of(...)} 的字符串）
     * @param name            事件名（如 {@code "started"}，脚本侧 {@code ServerEvents.started(...)}）
     * @param tier            脚本类型：STARTUP（startup 脚本）/ SERVER（server 脚本）/ CLIENT（client 脚本）
     * @param dispatch        分发语义：PLAIN（无分发键）/ BY_ID（按字符串 id 分发）
     * @param dispatchKeyType BY_ID 时的键类型（当前固定 {@code "string"}——脚本侧注册键永远是字符串 id）
     * @param cancellable     三态：{@code true}/{@code false} 为跨平台承诺，{@code null} 表示平台间不一致、不承诺
     * @param payload         可移植 payload 视图（字段名按脚本侧属性形式约定；可为空）
     * @param docs            说明
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

    /** 事件脚本类型。 */
    public enum EventTier {
        STARTUP, SERVER, CLIENT
    }

    /** 事件分发语义。 */
    public enum Dispatch {
        PLAIN, BY_ID
    }

    /**
     * 事件 payload 字段：{@link #kind() PORTABLE} 表示字段值是跨平台可移植类型（由 {@link #portType()} 描述）；
     * {@link #kind() NATIVE} 表示字段名跨平台稳定但值是平台原生 MC 对象（类型不承诺）。
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
