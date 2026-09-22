//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 24 reload 清理 fixture（NeoForge 侧，真实 Graal 管线）：脚本在真实家族总线上注册
 * 监听器，SERVER 的多次 reload 后——无重复 dispatch、旧 generation 不再接收回调、取消结果
 * 跟随 active generation、候选失败保留旧 active 且无半清理状态。这是票 24 AC4 的主证；
 * bus 级并发 stress 由 common 的 {@code EventBusJSExternalBehaviorStressTest} 承载。
 *
 * <p>探针是 {@code List}（票 26 harness 同款）：脚本回调 {@code event.add(tag)}，Java 侧
 * post 一个新 List 后读回「真正被调用」的结果——断言不依赖私有注册表或回调对象身份。
 * 合成载荷只承载探针（无头 JVM 无真实 MC 事件实例；原生事件 → 载荷转换与平台回调的
 * source trace 见 {@code Ticket24GameplayEventPhaseTraceTest}）。
 *
 * <p>线程/时机语义：本 harness 在测试线程上串行执行 load/reload/post（与生产 owner-thread
 * 模型一致——SERVER 分发与 reload 都在 server 线程）；跨线程并发进入由 Graal 单线程约束
 * 兜底（票 07 的 {@code SyncEvalWatchdogTest} 已冻结），不在此重复。
 */
class Ticket24GameplayEventReloadLifecycleTest {

    private Ticket24GameplayEventReloadHarness harness;

    @BeforeAll
    static void initPlatform() {
        Ticket24GameplayEventReloadHarness.ensurePlatformInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        Ticket24GameplayEventReloadHarness.clearScripts(ScriptType.SERVER);
        Ticket24GameplayEventReloadHarness.clearScripts(ScriptType.STARTUP);
        harness = new Ticket24GameplayEventReloadHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        Ticket24GameplayEventReloadHarness.clearScripts(ScriptType.SERVER);
        Ticket24GameplayEventReloadHarness.clearScripts(ScriptType.STARTUP);
    }

    /** 派发一个探针 List 到指定总线（无 key 定向：脚本监听器都是全局监听）。 */
    private static List<String> post(Object bus) {
        List<String> probe = new ArrayList<>();
        @SuppressWarnings("unchecked")
        var eventBus = (com.tkisor.nekojs.api.event.EventBusJS<List<String>, ?>) bus;
        eventBus.post(probe);
        return probe;
    }

    private static List<String> postCommand() {
        List<String> probe = new ArrayList<>();
        @SuppressWarnings("unchecked")
        var bus = (com.tkisor.nekojs.api.event.EventBusJS<List<String>, ?>) (Object) CommandEvents.COMMAND;
        bus.post(probe);
        return probe;
    }

    @Test
    void multipleSuccessfulReloadsDispatchEachGenerationExactlyOnce() throws Exception {
        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('gen1'))
                """);
        harness.loadScripts(ScriptType.SERVER);
        assertEquals(List.of("gen1"), post(PlayerEvents.LOGGED_IN));

        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('gen2'))
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("gen2"), post(PlayerEvents.LOGGED_IN),
                "after a successful reload the new generation runs exactly once"
                        + " and the old generation's listener is gone (no duplicate dispatch)");

        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('gen3'))
                LevelEvents.loaded(event => event.add('level-gen3'))
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("gen3"), post(PlayerEvents.LOGGED_IN));
        assertEquals(List.of("level-gen3"), post(LevelEvents.LOADED),
                "a reload that adds listeners to another family swaps both families together");

        // 声明整体移除：下一轮没有任何监听器 → post 不再派发（清理不是「保留最后一次」）
        harness.writeScript(ScriptType.SERVER, "lifecycle.js", "// no listeners\n");
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of(), post(PlayerEvents.LOGGED_IN),
                "a generation that registers nothing retires the previous listeners");
        assertEquals(List.of(), post(LevelEvents.LOADED));
    }

    @Test
    void aFailedReloadKeepsTheOldGenerationServingWithoutDoubleRegistration() throws Exception {
        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('old'))
                """);
        harness.loadScripts(ScriptType.SERVER);
        assertEquals(List.of("old"), post(PlayerEvents.LOGGED_IN));

        // 同批 peer 领域计划在 STATE_PLAN 预检抛出 → 整批候选失败（票 26 harness 同款手法，
        // 不为造失败改生产代码）。peer 只拒绝一次，恢复路径可测。
        harness.root.registerDomainCollector(new CandidateDomainCollector() {
            private boolean rejected;

            @Override public String domain() { return "ticket24-failing-peer"; }
            @Override public ScriptType scriptType() { return ScriptType.SERVER; }
            @Override public void collect(Handle handle) {
                if (!rejected) {
                    rejected = true;
                    handle.registerPlan(new CandidateStatePlan() {
                        @Override public String domain() { return "ticket24-failing-peer-plan"; }
                        @Override public void preflight() { throw new IllegalStateException("peer domain rejected"); }
                        @Override public void publish() { }
                    });
                }
            }
        });

        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('new'))
                """);
        assertThrows(NekoReloadException.class, () -> harness.reloadScripts(ScriptType.SERVER),
                "a rejected candidate batch must fail the reload loudly");

        assertEquals(List.of("old"), post(PlayerEvents.LOGGED_IN),
                "the previous active generation keeps serving after a failed reload");
        assertFalse(post(PlayerEvents.LOGGED_IN).contains("new"),
                "the rejected candidate's listener must never dispatch");

        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("new"), post(PlayerEvents.LOGGED_IN),
                "the next reload recovers and swaps exactly once (no half-cleaned state)");
    }

    @Test
    void cancellationFollowsTheActiveGenerationAcrossReloads() throws Exception {
        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => { event.add('cancel-gen'); return true })
                """);
        harness.loadScripts(ScriptType.SERVER);
        List<String> cancelled = postCommand();
        assertEquals(List.of("cancel-gen"), cancelled);

        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => { event.add('observe-gen'); return false })
                """);
        harness.reloadScripts(ScriptType.SERVER);
        List<String> observed = postCommand();
        assertEquals(List.of("observe-gen"), observed,
                "after the swap the listener runs but does not cancel anymore");

        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => { event.add('cancel-gen2'); return true })
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("cancel-gen2"), postCommand());
    }

    @Test
    void cancellationResultIsObservableFromThePostSide() throws Exception {
        // post 侧返回值即「脚本取消」的可观察结果（平台 bridge 以它回传 setCanceled）
        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => true)
                """);
        harness.loadScripts(ScriptType.SERVER);

        List<String> probe = new ArrayList<>();
        @SuppressWarnings("unchecked")
        var bus = (com.tkisor.nekojs.api.event.EventBusJS<List<String>, ?>) (Object) CommandEvents.COMMAND;
        assertTrue(bus.post(probe), "a cancelling script listener makes the family post report true");

        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => false)
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertFalse(bus.post(probe), "a non-cancelling generation keeps the post result false");
    }

    @Test
    void entityFamilyLifecycleListenersFollowTheSameGenerationSwap() throws Exception {
        harness.writeScript(ScriptType.SERVER, "entity.js", """
                EntityEvents.joinLevel(event => event.add('join'))
                EntityEvents.death(event => event.add('death'))
                EntityEvents.drops(event => event.add('drops'))
                EntityEvents.damagePre(event => event.add('damage'))
                EntityEvents.finalizeSpawn(event => event.add('spawn'))
                EntityEvents.leaveLevel(event => event.add('leave'))
                """);
        harness.loadScripts(ScriptType.SERVER);
        assertEquals(List.of("join"), post(EntityEvents.JOIN_LEVEL));
        assertEquals(List.of("death"), post(EntityEvents.DEATH));
        assertEquals(List.of("drops"), post(EntityEvents.DROPS));
        assertEquals(List.of("damage"), post(EntityEvents.DAMAGE_PRE));
        assertEquals(List.of("spawn"), post(EntityEvents.FINALIZE_SPAWN));
        assertEquals(List.of("leave"), post(EntityEvents.LEAVE_LEVEL));

        harness.writeScript(ScriptType.SERVER, "entity.js", """
                EntityEvents.joinLevel(event => event.add('join-2'))
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("join-2"), post(EntityEvents.JOIN_LEVEL));
        assertEquals(List.of(), post(EntityEvents.DEATH),
                "listeners the new generation no longer declares stop dispatching");
        assertEquals(List.of(), post(EntityEvents.DROPS));
        assertEquals(List.of(), post(EntityEvents.DAMAGE_PRE));
        assertEquals(List.of(), post(EntityEvents.FINALIZE_SPAWN));
        assertEquals(List.of(), post(EntityEvents.LEAVE_LEVEL));
    }

    @Test
    void goalStartupFamilyRoundTripsThroughTheProductionPostingSite() throws Exception {
        harness.writeScript(ScriptType.STARTUP, "goals.js", """
                GoalEvents.register(event => event.add('goal'))
                """);
        harness.loadScripts(ScriptType.STARTUP);

        List<String> first = new ArrayList<>();
        @SuppressWarnings("unchecked")
        var bus = (com.tkisor.nekojs.api.event.EventBusJS<List<String>, ?>) (Object) GoalEvents.REGISTER;
        bus.post(first);
        assertEquals(List.of("goal"), first,
                "the startup family delivers the script listener through the script registration path");

        // 生产 posting site（NekoJSMod.initializeScripts / NekoJSFabricMod.initializeScripts 同点）
        // 每次 post 恰好派发一代监听器各一次
        bus.post(first);
        assertEquals(List.of("goal", "goal"), first);
    }
}
//?}
