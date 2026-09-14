package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFacade;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiScreen;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiStateOwner;

import java.util.List;

/**
 * 没有客户端 UI host 时的显式拒绝实现（jsx-ui-spec「平台边界」）。
 *
 * <p>非客户端运行时（dedicated server）平台不提供 {@code UiResources}；此时脚本侧
 * {@code Ui.open(...)} 必须显式失败并带上 {@code UNSUPPORTED_CAPABILITY}，
 * <b>不得</b>静默 no-op 或返回一个永远不显示的 root（capability 三态语义）。
 */
public final class UnavailableUiFacade implements UiFacade {

    private final int generation;

    public UnavailableUiFacade(int generation) {
        this.generation = generation;
    }

    @Override
    public int generation() {
        return generation;
    }

    @Override
    public UiStateOwner createStateOwner() {
        return new DefaultUiStateOwner(Thread.currentThread());
    }

    @Override
    public UiRootHandle create(UiScreen screen) {
        throw unsupported("Ui.create");
    }

    @Override
    public UiRootHandle open(UiScreen screen) {
        throw unsupported("Ui.open");
    }

    @Override
    public void invalidateGeneration(int generation) {
        // 没有 root 可失效：显式无操作（不掩盖装配缺失，仅表示当前 generation 无 UI）。
    }

    @Override
    public void closeGeneration(int generation) {
        // 没有 root 可关闭：幂等空操作。
    }

    @Override
    public List<UiRootHandle> roots() {
        return List.of();
    }

    @Override
    public void dispose() {
        // 幂等空操作。
    }

    private UiFailure unsupported(String operation) {
        return new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.UNSUPPORTED_CAPABILITY,
                operation + " is unavailable: no client UI host adapter is installed for generation "
                        + generation, null);
    }

    /** 当前逻辑视口（拒绝实现不做布局）。 */
    public UiSize viewport() {
        return new UiSize(320, 240);
    }
}
