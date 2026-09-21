package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;

/**
 * 脚本 HUD / 世界渲染器注册面的候选域收集器（票 26 AC4）：把「本候选 generation 注册了
 * 哪些常驻渲染器」挂上候选的联合预检/发布边界，使提交点能整体换装生产表。
 *
 * <p><b>收集无条件发生</b>：即使本代一个渲染器都没注册，也要注册一个批次——
 * 「新 generation 不再注册」正是旧渲染器退役的唯一路径，空批次不参与 commit 就永远换不掉
 * 旧表（{@link CandidateDomainCollector} 的空计划契约）。批次实例来自两处之一：脚本执行期
 * 首个 {@code hudRender}/{@code worldRender} 调用已建好（此时挂的是同一实例），或本代一个都
 * 没注册而在此**新建**（空批次）。
 *
 * <p><b>候选期不可见</b>：批次是 inert 的（只按 id 记账），渲染钩子要等 commit 点
 * {@code publish()} 才看得到新 generation；候选失败/取消时批次随候选丢弃，旧 active
 * 继续服务。这也是 {@code ClientRenderPlugin#beforeScriptsLoaded} 那套「加载前整表清空」
 * 被删除的原因：它会清掉旧 active。
 */
public final class ClientRenderDomainOwner implements CandidateDomainCollector {

    /** 收集器标识（与候选计划共用，进入结构化 reload 失败的 {@code domain-collect:<id>}）。 */
    public static final String DOMAIN = ClientRenderRegistry.CANDIDATE_DOMAIN;

    @Override
    public String domain() {
        return DOMAIN;
    }

    /** CLIENT-only：渲染器注册只在 client_scripts 可见。 */
    @Override
    public ScriptType scriptType() {
        return ScriptType.CLIENT;
    }

    @Override
    public void collect(Handle handle) {
        ClientRenderRegistry.registerCandidateBatch(handle.candidateContext(), handle);
    }
}
