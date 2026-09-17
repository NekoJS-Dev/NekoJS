package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.modification.CandidateDomainCollector;
import com.tkisor.nekojs.core.modification.ModificationApplier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 39 snapshot ownership / 旧路径收口的结构性 guard（AC5/AC13/AC14 的旁路证据；
 * 无 vanilla 注册表依赖 → 本机实测 26.1.2 / 26.1.2-fabric / 1.21.1 / 26.2.0 / 26.2.0-fabric 真跑）。
 *
 * <p>断言的是「旧路径已经不在了」这类可机检事实，而不是行为：进程级 static
 * {@code SNAPSHOTS}、{@code fire()} restore-all-then-replay 入口、以及视图上的
 * public live-mutation 接缝都必须消失——snapshot/restore 状态只能由 root 授权
 * domain owner 的实例字段持有，应用只发生在 Adapter。
 *
 * <p>删除门禁（AC14）：本测试只是替代路径 parity 与无消费者证据的一部分；旧公开路径的
 * 正式删除仍需维护者 sign-off，见票 39 REPORT 的 AC14 章节。
 */
class Ticket39ModificationOwnershipTest {

    /** 视图/载荷不得公开接受这些 live 平台类型（否则脚本可绕过候选计划直接改 live 对象）。 */
    private static final Set<String> LIVE_PLATFORM_TYPES = Set.of(
            "net.minecraft.world.item.Item",
            "net.minecraft.world.level.block.Block",
            "net.minecraft.world.level.block.state.BlockState",
            "net.minecraft.core.component.DataComponentMap");

    @Test
    void payloadsAndOwnerHoldNoProcessLevelStaticSnapshotState() {
        assertNoStaticSnapshotState(ItemModificationEventJS.class);
        assertNoStaticSnapshotState(ModificationDomainOwner.class);
//? if >=26 {
        assertNoStaticSnapshotState(BlockModificationEventJS.class);
//?}
    }

    private static void assertNoStaticSnapshotState(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertFalse(Map.class.isAssignableFrom(field.getType())
                            || Collection.class.isAssignableFrom(field.getType()),
                    type.getSimpleName() + "." + field.getName()
                            + " is process-level static snapshot state; snapshots must be instance state "
                            + "owned by the root-authorized domain owner (ticket 39 AC5)");
        }
    }

    @Test
    void restoreAllThenReplayEntryPointsAreGone() {
        for (Class<?> type : List.of(ItemModificationEventJS.class, ModificationDomainOwner.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                assertFalse(name.equals("fire") || name.equals("restoreAll") || name.equals("restore"),
                        type.getSimpleName() + "." + name
                                + " is a legacy restore-all/replay entry point; collection+commit is the only path "
                                + "(ticket 39 AC3/AC14)");
            }
        }
        // 调用者 Interface → Adapter 的可追溯归属（AC13）：domain owner 同时是收集挂载点、
        // 应用接缝与 root close 清理对象，不是新的公开 Modification Runtime。
        assertTrue(CandidateDomainCollector.class.isAssignableFrom(ModificationDomainOwner.class),
                "the domain owner is the root-owned collection mount point");
        assertTrue(ModificationApplier.class.isAssignableFrom(ModificationDomainOwner.class),
                "the domain owner is the platform Adapter (preflight/apply seam)");
        assertTrue(AutoCloseable.class.isAssignableFrom(ModificationDomainOwner.class),
                "the root-owned domain owner must release its baselines on root close (AC5)");
    }

    @Test
    void modifyKeepsThePreRefactorFunctionalCallbackSignature() throws Exception {
        Method itemModify = ItemModificationEventJS.class
                .getDeclaredMethod("modify", String.class, Consumer.class);
        assertEquals(void.class, itemModify.getReturnType(),
                "modify only records a declaration; it applies nothing to live objects");

//? if >=26 {
        Method blockModify = BlockModificationEventJS.class
                .getDeclaredMethod("modify", String.class, Consumer.class);
        assertEquals(void.class, blockModify.getReturnType());
//?}
    }

    @Test
    void viewsExposeNoLiveMutationSeam() {
        assertNoPublicLiveMutationSeam(ItemModificationJS.class);
//? if >=26 {
        assertNoPublicLiveMutationSeam(BlockModificationJS.class);
//?}
    }

    private static void assertNoPublicLiveMutationSeam(Class<?> viewType) {
        for (Method method : viewType.getMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(LIVE_PLATFORM_TYPES.contains(parameter.getName()),
                        viewType.getSimpleName() + "." + method.getName() + "(" + parameter.getSimpleName()
                                + ") exposes a live-platform mutation seam to the script surface; writes must go "
                                + "through the normalized declaration plan (ticket 39 AC2)");
            }
        }
    }

    /**
     * AC10 的可机检边界：modification 类不得出现网络/包符号。本票不声明任何客户端自动同步
     * 能力（自动同步 = unsupported；relog / chunk resync 需平台侧动作），写入面只碰服务端
     * 对象图——一旦有人给这条路径加 packet/sendTo，本 guard 立即失败，能力记录必须先更新。
     */
    @Test
    void modificationPathReferencesNoNetworkSymbols() {
        assertNoNetworkReference(ItemModificationEventJS.class);
        assertNoNetworkReference(ItemModificationJS.class);
        assertNoNetworkReference(ModificationDomainOwner.class);
        assertNoNetworkReference(com.tkisor.nekojs.core.modification.ModificationCandidatePlan.class);
        assertNoNetworkReference(com.tkisor.nekojs.core.modification.ModificationDeclaration.class);
//? if >=26 {
        assertNoNetworkReference(BlockModificationEventJS.class);
        assertNoNetworkReference(BlockModificationJS.class);
//?}
    }

    private static void assertNoNetworkReference(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            assertNotNetwork(type, method.getReturnType().getName());
            for (Class<?> parameter : method.getParameterTypes()) {
                assertNotNetwork(type, parameter.getName());
            }
            for (Class<?> exception : method.getExceptionTypes()) {
                assertNotNetwork(type, exception.getName());
            }
        }
        for (Field field : type.getDeclaredFields()) {
            assertNotNetwork(type, field.getType().getName());
        }
    }

    private static void assertNotNetwork(Class<?> type, String referencedTypeName) {
        assertFalse(referencedTypeName.startsWith("net.minecraft.network")
                        || referencedTypeName.contains(".network."),
                type.getSimpleName() + " references " + referencedTypeName
                        + ": the modification path declares no automatic client-sync capability "
                        + "(ticket 39 AC10). Adding a sync path must update the capability record in the "
                        + "baseline REPORT first.");
    }
}
