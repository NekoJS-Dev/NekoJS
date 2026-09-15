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
 * 启动期注册 Runtime（ticket 15 垂直收口的 owner）：
 * STARTUP 脚本经唯一 {@link RegistryEvents#REGISTER} 调用者 Interface 声明，
 * 本类负责<b>收集（恰好一次）、Supplier 校验（返回值/实际类型）、definition
 * fingerprint、逐注册表 pass 抽干与连带派生投递</b>；节点 Adapter（NeoForge
 * {@code RegistryEventAdapter} / Fabric {@code FabricRegistryAdapter}）只在正确的
 * 注册 pass 调 {@link #drainFor}，把对象创建留给平台注册动作。
 *
 * <p><b>epoch 语义</b>（AC2/AC5）：每轮游戏启动一个 epoch（{@code beginBoot}），
 * 失败或上一轮的残留不会污染下一轮——新一轮 boot 先丢弃并诊断旧 epoch 的暂存，
 * 收集期抛错的 builder 因「先配置后入库」的顺序永不成为可 drain 的半成品（PR37 负例）。
 * 服务器运行期 Dynamic Registry（{@code com.tkisor.nekojs.dynamic} 面）走完全独立的
 * 生命周期，不经本路径（AC12）。
 *
 * <p><b>drain 结果可观察</b>（AC10）：{@link DrainResult} 携带每条注册的定义、注册表、
 * 节点、指纹与错误来源；测试/诊断读它，不读私有仓库字段。
 */
public final class StartupRegistryRuntime {

    /** 节点 Adapter 的注册落点：把 (registry, id, supplier) 交给平台注册动作。 */
    @FunctionalInterface
    public interface RegistrySink {

        /**
         * 注册一条对象。supplier 由 Runtime 包装过校验（返回值/实际类型）；NeoForge 侧
         * 可延迟执行（RegisterEvent 的 supplier 语义），Fabric 侧立即执行。
         */
        void register(ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier);
    }

    /** 一条成功的注册观察记录。 */
    public record RegistrationRecord(
            Identifier definition,
            ResourceKey<? extends Registry<?>> registry,
            String node,
            String origin,
            String fingerprint) {}

    /** 一条失败/跳过的观察记录：错误来源标注阶段（collection/drain/additional-target）。 */
    public record ErrorRecord(
            Identifier definition,
            ResourceKey<? extends Registry<?>> registry,
            String node,
            String source,
            String message) {}

    /** 单个 pass 的 drain 结果（不可变快照）。 */
    public record DrainResult(List<RegistrationRecord> registered, List<ErrorRecord> errors) {
        public DrainResult(List<RegistrationRecord> registered, List<ErrorRecord> errors) {
            this.registered = List.copyOf(registered == null ? List.of() : registered);
            this.errors = List.copyOf(errors == null ? List.of() : errors);
        }
    }

    /** 已知注册表的元素类型令牌（裸 Supplier 实际类型校验用；未列出的注册表不校验类型）。 */
    private static final Map<ResourceKey<? extends Registry<?>>, Class<?>> ELEMENT_TOKENS = elementTokens();

    private final RegistryRepository repository;
    private final Set<ResourceKey<? extends Registry<?>>> passed = new HashSet<>();
    private final String node;
    private boolean collected;

    /** @param node 节点标签（如 {@code neoforge:26.1.2} / {@code fabric:26.1.2}），进入观察记录与错误信息。 */
    public StartupRegistryRuntime(String node) {
        this(node, new RegistryRepository());
    }

    /** 仓库注入形态（adapter 持有跨 pass 仓库 / 测试构造非 bootstrap 输入时用）。 */
    public StartupRegistryRuntime(String node, RegistryRepository repository) {
        this.node = node;
        this.repository = repository;
    }

    /** 收集仓库（RegistryEventJS 的写入目标）。 */
    public RegistryRepository repository() {
        return repository;
    }

    /** 节点标签。 */
    public String node() {
        return node;
    }

    /**
     * 首个注册表 pass 前调用：恰好一次投递收集事件（AC1）。重复调用 no-op；
     * 监听器异常由 {@code EventBusJS.post} 捕获记录，不中断其余监听器。
     */
    public void collectOnce() {
        collectOnce(RegistryEventJS.create(repository, node));
    }

    /**
     * 可注入事件载荷的收集入口（adapter / 端到端测试 seam）：payload 仍是唯一
     * {@link RegistryEvents#REGISTER} 调用者 Interface 的对象——测试用它注入非
     * bootstrap 的 infos/types 输入，不绕过总线。
     */
    public void collectOnce(RegistryEventJS event) {
        if (collected) {
            return;
        }
        collected = true;
        RegistryEvents.REGISTER.post(event);
    }

    /** 收集事件是否已投递（本轮 epoch 内）。 */
    public boolean isCollected() {
        return collected;
    }

    /**
     * 抽干某注册表的主对象与连带派生条目（每 pass 一次；AC2）。主对象先入 sink，
     * 其 {@code handleAdditionalObjects} 投递的派生条目进入仓库、等目标注册表自己的 pass；
     * 目标 pass 已过的派生条目记错误并跳过（loader 注册序不可回溯，KubeJS 同款语义）。
     *
     * <p>平台注册动作（sink）的失败按条隔离：记入 {@link DrainResult#errors()}
     * （source={@code platform-register}，含定义/注册表/节点/错误来源）后继续，不留下
     * 静默丢失；条目本身已从仓库 drain（无未交付残留可污染后续 pass）。
     */
    public DrainResult drainFor(ResourceKey<? extends Registry<?>> registry, RegistrySink sink) {
        List<RegistrationRecord> registered = new ArrayList<>();
        List<ErrorRecord> errors = new ArrayList<>();
        for (RegistryRepository.Entry entry : repository.drain(registry)) {
            RegistryObjectBuilder<?> builder = entry.builder();
            registered.add(new RegistrationRecord(
                    builder.id, registry, node, entry.origin(), definitionFingerprint(builder)));
            try {
                sink.register(registry, builder.id, validatedSupplier(builder.id, registry, builder));
            } catch (RuntimeException e) {
                errors.add(new ErrorRecord(builder.id, registry, node, "platform-register", e.getMessage()));
            }
            collectAdditionalOf(builder, errors);
        }
        for (RegistryRepository.Additional additional : repository.drainAdditional(registry)) {
            registered.add(new RegistrationRecord(
                    additional.id(), registry, node, "additional:" + additional.source(),
                    "additional:" + additional.source()));
            try {
                sink.register(registry, additional.id(), validatedSupplier(additional.id(), registry, additional.supplier()));
            } catch (RuntimeException e) {
                errors.add(new ErrorRecord(additional.id(), registry, node, "platform-register", e.getMessage()));
            }
        }
        // 本 pass 结束后才计入 passed：builder 派生条目指向本注册表时仍是合法的当轮注册
        passed.add(registry);
        return new DrainResult(registered, errors);
    }

    /** 抽干期回调：builder 的连带派生条目入仓库，等目标注册表自己的 pass 投递。 */
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
     * 裸 supplier 的校验包装（AC5）：返回值非空、实际类型匹配已知令牌时放行；
     * 不承诺任意 Supplier 副作用的指纹或回滚（不据此开放 Dynamic Registry）。
     * 对未登记令牌的注册表只做非空校验。
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
     * definition fingerprint（AC3）：注册表 + id + 类型名 + 全部可写属性的<b>规范化</b>
     * 读数（{@link RegistryBuilderContract#normalizedPropertyReadings}，字典序）。
     * 显式 setter 与 property 赋值经同一 setter 后指纹必然一致；连带派生对象
     * 懒构建、指纹只覆盖可表达的启动声明（裸 Supplier 不参与指纹）。
     */
    public String definitionFingerprint(RegistryObjectBuilder<?> builder) {
        StringBuilder canonical = new StringBuilder("v1|");
        canonical.append(builder.id).append('|');
        RegistryBuilderContract.of(builderClassOf(builder)).normalizedPropertyReadings(builder)
                .forEach(reading -> canonical.append(reading).append(';'));
        return sha256(canonical.toString());
    }

    /**
     * 只读 live 视图：当前尚未抽干的主对象/派生条目（诊断用）。
     * <b>不是冻结结果</b>（PR37：只读 live view 不冒充冻结结果）——返回防御性副本，
     * 后续 drain 会改变真实状态；已 drain 的结果以 {@link DrainResult} 快照为准。
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

    /** load-complete 诊断：收集了却未被任何 pass 消化的内容（注册表名写错 / 目标 pass 先于来源）。 */
    public void reportUndelivered(Consumer<String> logger) {
        repository.undrained().forEach((registry, entries) -> entries.forEach(entry -> logger.accept(
                "Registry '" + registry.identifier() + "' (node " + node + ") collected '" + entry.builder().id
                        + "' (from " + entry.origin() + ") but its registration pass never fired; content NOT registered")));
        repository.undeliveredAdditional().forEach((registry, additionals) -> additionals.forEach(additional -> logger.accept(
                "Registry '" + registry.identifier() + "' (node " + node + ") has undelivered additional '"
                        + additional.id() + "' of '" + additional.source() + "'; content NOT registered")));
    }

    /** 本轮 epoch 是否已全部清空（无未交付残留）。 */
    public boolean isFullyDrained() {
        return repository.isEmpty();
    }

    /**
     * 尚有未抽干内容（主对象或连带派生）的注册表键快照（fabric 单批直注用：
     * 先拍键集再逐个 drain，避免 drain 改结构）。
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
