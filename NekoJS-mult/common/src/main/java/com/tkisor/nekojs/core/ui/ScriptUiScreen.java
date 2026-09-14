package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiScreen;
import com.tkisor.nekojs.api.ui.UiScreenOptions;
import com.tkisor.nekojs.api.ui.UiScriptScreen;
import com.tkisor.nekojs.api.ui.UiVNode;
import com.tkisor.nekojs.api.ui.UiVNodeFactory;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Objects;

/**
 * 由脚本 render function 驱动的 {@link UiScreen}（jsx-ui-spec「root 输入」）。
 *
 * <p>每次 reconcile 只调用一次 render function，并把返回值经 {@link UiVNodeFactory}
 * 转换为候选 VNode。组件展开、条件、数组与 Fragment 的处理在转换器与 reconciler 内，
 * 不在本类。
 */
public final class ScriptUiScreen implements UiScriptScreen {

    private final Value renderFunction;
    private final UiVNodeFactory vnodeFactory;
    private final UiScreenOptions options;
    private final String label;

    public ScriptUiScreen(Value renderFunction, UiVNodeFactory vnodeFactory, UiScreenOptions options, String label) {
        this.renderFunction = Objects.requireNonNull(renderFunction, "renderFunction");
        this.vnodeFactory = Objects.requireNonNull(vnodeFactory, "vnodeFactory");
        this.options = options == null ? UiScreenOptions.defaults() : options;
        this.label = label == null ? "script" : label;
    }

    @Override
    public UiVNode render() {
        if (!renderFunction.canExecute()) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                    "render function for " + label + " is not executable", null);
        }
        Value produced;
        try {
            produced = renderFunction.execute();
        } catch (RuntimeException failure) {
            throw new UiFailure(UiDiagnosticStage.RENDER, "RENDER_FAILURE",
                    "render function for " + label + " failed: " + failure.getMessage(), null, failure);
        }
        List<UiVNode> nodes = vnodeFactory.toVNodes(produced, "screen");
        if (nodes.isEmpty()) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_ROOT,
                    "render function for " + label + " returned no node", "screen");
        }
        if (nodes.size() == 1) {
            return nodes.getFirst();
        }
        return new com.tkisor.nekojs.api.ui.FragmentVNode(null, nodes);
    }

    @Override
    public UiScreenOptions options() {
        return options;
    }

    @Override
    public Object renderFunction() {
        return renderFunction;
    }
}
