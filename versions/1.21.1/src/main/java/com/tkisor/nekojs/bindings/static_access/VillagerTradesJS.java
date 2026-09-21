// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
//
// 差异说明（票 22）：本节点没有共享层的 VillagerTradeDomainState 实例——1.21.1 的交易
// 注册表形状（VillagerTrades.TRADES 静态池）与 26.x 的 ReloadableRegistries 不同，域状态
// 由 versions/1.21.1 的 VillagerTradeDomainOwner 注入。状态尚未注入时返回 NOT_INITIALIZED，
// 这是「本节点当前没有可用快照」的显式结果，不是空快照冒充成功。
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.villager.VillagerTradeDomainState;
import com.tkisor.nekojs.core.villager.VillagerTradeQuerySurface;
import com.tkisor.nekojs.core.villager.VillagerTradesFacade;
import graal.graalvm.polyglot.Context;

/**
 * Static binding {@code VillagerTrades}, first version (ticket 22): a <b>read-only query</b>
 * over the villager / wandering trader declarations the last committed generation applied.
 *
 * <p>{@code add} moved to the existing {@code ServerEvents} data sub-event
 * ({@code ServerEvents.tradeDeclaration}); the {@code pendingCount} staging counter is gone
 * with the old staged queue.
 */
@Doc("Static binding 'VillagerTrades': read-only query over the trades the last committed generation declared. Declare trades with ServerEvents.tradeDeclaration.")
public class VillagerTradesJS {

    /**
     * Read-only snapshot of the committed villager trade declarations.
     *
     * <p>Example: {@code const result = VillagerTrades.query();
     * if (result.status === 'ACTIVE') console.info(result.total, result.tradeSetIds)}</p>
     */
    @Doc("Returns a read-only, generation-bound snapshot: status ('ACTIVE' / 'STALE' / 'NOT_INITIALIZED'), generation, adapterId, total, tradeSetIds, unrestoredListingKeys and retiredListingKeys. A stale token answers with empty values instead of another generation's data.")
    @Return("read-only query result (never a live registry view)")
    public VillagerTradeQuerySurface query() {
        VillagerTradeDomainState state = VillagerTradesFacade.stateOrNull();
        if (state == null) {
            return VillagerTradeQuerySurface.unavailable(VillagerTradesFacade.unavailableReason());
        }
        return state.query(Context.getCurrent());
    }

    /** Developer-facing summary of {@link #query()} (diagnostics; no structured members needed). */
    @Doc("Developer-facing one-line summary of query(), for diagnostics and logs.")
    @Return("human-readable summary line")
    public String describe() {
        return query().describe();
    }
}
