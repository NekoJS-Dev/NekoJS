//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.client.KeyBindEvents;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 26 AC4 贯穿 fixture（26.x 版本树测试树）：{@code ClientEvents.hudRender(...)} 注册的
 * 常驻渲染器走候选 generation 生命周期 —— 候选期对生产路由不可见、commit 点整批换装
 * 恰好一次、候选失败/取消时旧 active 渲染器继续服务。
 *
 * <p>断言只走公开行为：{@link Ticket26ClientInputHudHarness#dispatchHud(String)} 返回
 * 「本帧真正被调用的渲染器 id」（渲染上下文探针），以及 {@code KeyBindEvents.PRESSED}
 * 的按键定向集合；不断言私有注册表快照或回调对象身份。
 *
 * <p>候选失败由<b>同批的 peer 领域计划</b>在 STATE_PLAN 联合预检抛出制造（与
 * {@code PostEffectDeclarationLifecycleTest#aCandidatePreflightThatPassesDoesNotPublishAnObservation}
 * 同款），不为了造失败去改生产代码；peer 收集器同时充当「候选期生产路由长什么样」的观察点。
 */
class Ticket26ClientInputHudLifecycleTest {

    private Ticket26ClientInputHudHarness harness;

    @BeforeAll
    static void initPlatform() {
        Ticket26ClientInputHudHarness.ensurePlatformInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        Ticket26ClientInputHudHarness.clearClientScripts();
        Ticket26ClientInputHudHarness.resetRegistry();
        harness = new Ticket26ClientInputHudHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        Ticket26ClientInputHudHarness.resetRegistry();
        Ticket26ClientInputHudHarness.clearClientScripts();
    }

    private static String hudScript(String id) {
        return "ClientEvents.hudRender('" + id + "', { layer: 'normal' }, ctx => ctx.add('" + id + "'))\n";
    }

    /**
     * peer 收集器：在 DOMAIN_PLAN 阶段记录「此刻生产路由长什么样」，并可选择让整批候选
     * 在 STATE_PLAN 预检失败一次（{@code rejectOnce}）。观察走只读公开面。
     *
     * <p>拒绝刻意只发生一次：收集器按注册序在每一轮候选都被调用，若每次都拒绝，
     * 「下一轮能正常提交」的恢复路径就不可测（那是测试自己制造的持续故障，不是候选语义）。
     */
    private List<List<String>> observeDuringCandidate(boolean rejectOnce) {
        List<List<String>> seen = new ArrayList<>();
        boolean[] rejected = {false};
        harness.root.registerDomainCollector(new CandidateDomainCollector() {
            @Override public String domain() { return "ticket26-observation-peer"; }

            @Override public ScriptType scriptType() { return ScriptType.CLIENT; }

            @Override public void collect(Handle handle) {
                seen.add(Ticket26ClientInputHudHarness.dispatchHud("normal"));
                if (rejectOnce && !rejected[0]) {
                    rejected[0] = true;
                    handle.registerPlan(new CandidateStatePlan() {
                        @Override public String domain() { return "ticket26-failing-peer"; }
                        @Override public void preflight() { throw new IllegalStateException("peer domain rejected"); }
                        @Override public void publish() { }
                    });
                }
            }
        });
        return seen;
    }

    @Test
    void candidateRegistrationsAreInvisibleUntilCommitThenSwapExactlyOnce() throws Exception {
        harness.writeClientScript("hud.js", hudScript("first"));
        harness.loadClientScripts();

        assertEquals(List.of("first"), Ticket26ClientInputHudHarness.dispatchHud("normal"),
                "the initial (non-transactional) load installs the renderer as the active generation");

        List<List<String>> seenDuringCandidate = observeDuringCandidate(false);

        harness.writeClientScript("hud.js", hudScript("second"));
        harness.reloadClientScripts();

        assertEquals(List.of(List.of("first")), seenDuringCandidate,
                "candidate registrations must not reach the production route (the old generation"
                        + " is what a frame renders while the candidate is being built)");
        assertEquals(List.of("second"), Ticket26ClientInputHudHarness.dispatchHud("normal"),
                "the commit point swaps the generation in one step: the new renderer runs exactly"
                        + " once and the previous generation's renderer is gone");
        assertTrue(Ticket26ClientInputHudHarness.hasHud("normal"),
                "the committed renderer is visible to the per-frame fast path");
    }

    @Test
    void aRejectedCandidateKeepsServingThePreviousActiveRenderer() throws Exception {
        harness.writeClientScript("hud.js", hudScript("first"));
        harness.loadClientScripts();
        assertEquals(List.of("first"), Ticket26ClientInputHudHarness.dispatchHud("normal"));

        // 同批：渲染器已进候选批次（inert），peer 领域计划在 STATE_PLAN 联合预检拒绝 → 整批失败。
        observeDuringCandidate(true);
        harness.writeClientScript("hud.js", hudScript("second"));
        NekoReloadException failure = assertThrows(NekoReloadException.class, harness::reloadClientScripts);
        assertTrue(failure.report().domain() != null && failure.report().domain().contains("ticket26-failing-peer"),
                "the rejected candidate is attributed to the failing peer plan: " + failure.report().domain());

        assertEquals(List.of("first"), Ticket26ClientInputHudHarness.dispatchHud("normal"),
                "a rejected candidate must not disturb the previous active generation (AC4)");
        assertTrue(Ticket26ClientInputHudHarness.hasHud("normal"),
                "the previous generation's renderer is still installed, so the frame keeps drawing it");

        // 候选资源随失败丢弃：下一轮正常 reload 仍能提交（无挂起批次泄漏）。
        harness.writeClientScript("hud.js", hudScript("third"));
        harness.reloadClientScripts();
        assertEquals(List.of("third"), Ticket26ClientInputHudHarness.dispatchHud("normal"),
                "a later candidate commits normally (no pending batch leak)");
    }

    @Test
    void aGenerationThatRegistersNothingRetiresThePreviousRenderers() throws Exception {
        // 注册入口是「调用即注册」：新 generation 没有脚本再注册时批次为空，commit 点换装后
        // 旧渲染器退役，而不是作为陈旧残留继续每帧回调。
        harness.writeClientScript("hud.js", hudScript("first"));
        harness.loadClientScripts();
        assertEquals(List.of("first"), Ticket26ClientInputHudHarness.dispatchHud("normal"));

        harness.writeClientScript("hud.js", "global.noHud = true\n");
        harness.reloadClientScripts();

        // 空批次也参与 commit（ClientRenderDomainOwner 无条件注册计划），因此生产表真的被
        // 换成空表：两个口径必须一致——dispatch 不再调用任何渲染器，hasHud 也不再认为该层
        // 有存活渲染器。修复前这里会「dispatch 空 / hasHud true」（旧条目靠已关闭 Context 的
        // 吞错伪装成空 dispatch），那条断言是假绿，本用例把它变成真断言。
        assertTrue(Ticket26ClientInputHudHarness.dispatchHud("normal").isEmpty(),
                "an empty committed batch retires the previous generation's renderer");
        assertTrue(!Ticket26ClientInputHudHarness.hasHud("normal"),
                "the fast path agrees: the layer has no renderer left");
    }

    @Test
    void keyBindListenersFollowTheSameCandidateBoundaryWithoutDuplication() throws Exception {
        // KeyBindEvents 的绑定注册语义（同 id 幂等、跨 reload 存活）本票不改；这里钉住的是
        // 它们的事件监听面与渲染器走同一条候选边界：候选期不挂生产总线，commit 后恰好一条，
        // 再 reload 仍是一条（不重复注册）。
        harness.writeClientScript("keys.js", """
                KeyBindEvents.pressed('ticket26:key_a', event => { global.pressedId = event.id })
                KeyBindEvents.register('ticket26:key_a', 'key.keyboard.g')
                """);
        harness.loadClientScripts();

        assertEquals(Set.of("ticket26:key_a"), KeyBindEvents.PRESSED.registeredKeys(),
                "the initial load installs exactly one keyed listener for the declared binding id");

        List<List<String>> seenDuringCandidate = new ArrayList<>();
        harness.root.registerDomainCollector(new CandidateDomainCollector() {
            @Override public String domain() { return "ticket26-keybind-observation-peer"; }

            @Override public ScriptType scriptType() { return ScriptType.CLIENT; }

            @Override public void collect(Handle handle) {
                seenDuringCandidate.add(List.copyOf(KeyBindEvents.PRESSED.registeredKeys()));
            }
        });

        harness.writeClientScript("keys.js", """
                KeyBindEvents.pressed('ticket26:key_a', event => { global.pressedId = event.id })
                KeyBindEvents.register('ticket26:key_a', 'key.keyboard.g')
                """);
        harness.reloadClientScripts();

        assertEquals(List.of(List.of("ticket26:key_a")), seenDuringCandidate,
                "during DOMAIN_PLAN the production route still serves the old generation, so the"
                        + " id is present exactly once: the candidate listener is pending, not installed");
        assertEquals(Set.of("ticket26:key_a"), KeyBindEvents.PRESSED.registeredKeys(),
                "the commit point activates exactly one listener for the id (no duplicate)");
    }
}
//?}
//?}
