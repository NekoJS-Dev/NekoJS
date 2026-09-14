package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.HostVNode;
import com.tkisor.nekojs.api.ui.UiComponentRegistry;
import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiDiagnosticSink;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiEvent;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiFacade;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiResources;
import com.tkisor.nekojs.api.ui.UiScreen;
import com.tkisor.nekojs.api.ui.UiScreenOptions;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiTextMeasure;
import com.tkisor.nekojs.api.ui.UiTextMeasurer;
import com.tkisor.nekojs.api.ui.UiVNode;
import com.tkisor.nekojs.api.ui.ViewportContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * JSX UI common 测试夹具：把 fake host Adapter、确定性文本测量、可变 render 函数与
 * 诊断记录装配成一个可观察的 root（jsx-ui-spec Testing Decisions 的最高 Seam）。
 */
final class UiTestFixture {

    final FakeUiHostAdapter adapter = new FakeUiHostAdapter();
    final List<UiDiagnostic> diagnostics = new ArrayList<>();
    final Map<String, UiComponentRegistry.UiRenderer> components = new LinkedHashMap<>();
    final List<UiEvent> dispatched = new ArrayList<>();
    final Map<String, Integer> componentRenders = new LinkedHashMap<>();
    UiSize viewport = new UiSize(320, 240);
    ViewportContext.SafeArea safeArea = ViewportContext.SafeArea.none();
    int guiScale = 1;
    Set<ViewportContext.Capability> capabilities = Set.of();
    int generation = 1;
    Thread ownerThread = Thread.currentThread();
    boolean failEvents;
    Supplier<UiVNode> render = () -> null;

    final List<UiTextMeasure> textMeasures = new ArrayList<>();

    /** 覆盖用的 UiResources（Screen 宿主等）；null 时用 {@link #resources()}。 */
    UiResources hostResources;

    UiTestFixture() {
        viewport = new UiSize(320, 240);
    }

    UiResources resources() {
        return new UiResources() {
            @Override
            public com.tkisor.nekojs.api.ui.UiHostAdapter adapter() {
                return adapter;
            }

            @Override
            public UiTextMeasurer textMeasurer() {
                return request -> {
                    textMeasures.add(request);
                    double perChar = 6.0 * (request.fontSize() / 8.0);
                    double width = request.text().length() * perChar;
                    if (request.wrap() && request.availableWidth() > 0 && width > request.availableWidth()) {
                        int lines = (int) Math.ceil(width / request.availableWidth());
                        return new UiSize(request.availableWidth(), lines * (request.fontSize() + 2));
                    }
                    return new UiSize(width, request.fontSize() + 2);
                };
            }

            @Override
            public UiComponentRegistry components() {
                return components::get;
            }

            @Override
            public int generation() {
                return generation;
            }

            @Override
            public UiDiagnosticSink diagnostics() {
                return diagnostics::add;
            }

            @Override
            public Thread ownerThread() {
                return ownerThread;
            }

            @Override
            public Invalidator invalidator() {
                return null;
            }

            @Override
            public UiEventSink eventSink() {
                return (node, event) -> {
                    dispatched.add(event);
                    if (failEvents) {
                        throw new IllegalStateException("event callback failed for " + node.path());
                    }
                };
            }

            @Override
            public UiSize viewport() {
                return viewport;
            }

            @Override
            public ViewportContext viewportContext() {
                return new ViewportContext(viewport.width(), viewport.height(),
                        safeArea, guiScale, capabilities);
            }
        };
    }

    RootUiFacade facade() {
        return new RootUiFacade(resources());
    }

    UiScreen screen() {
        return new UiScreen() {
            @Override
            public UiVNode render() {
                return render.get();
            }

            @Override
            public UiScreenOptions options() {
                return UiScreenOptions.defaults();
            }
        };
    }

    /** 注册一个只渲染一次的组件。 */
    void component(String name, Supplier<UiVNode> body) {
        components.put(name, (props, children) -> {
            componentRenders.merge(name, 1, Integer::sum);
            return body.get();
        });
    }

    UiHostNode child(UiHostNode parent, int index) {
        return parent.children().get(index);
    }

    List<String> tagsOf(UiHostNode node) {
        List<String> tags = new ArrayList<>();
        tags.add(node.primitive().tag());
        for (UiHostNode child : node.children()) {
            tags.addAll(tagsOf(child));
        }
        return tags;
    }

    List<UiDiagnostic> diagnosticsOf(UiDiagnosticStage stage) {
        return diagnostics.stream().filter(d -> d.stage() == stage).toList();
    }

    boolean hasDiagnostic(UiDiagnosticStage stage, String code) {
        return diagnostics.stream().anyMatch(d -> d.stage() == stage && code.equals(d.code()));
    }

    // ---- VNode 构造助手 ----

    static HostVNode host(UiPrimitive primitive, String key, UiProps props, UiVNode... children) {
        return new HostVNode(primitive, key, props, List.of(children));
    }

    static UiProps props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return UiProps.of(map);
    }

    static UiEvent click(double x, double y) {
        return UiEvent.mouse(UiEventType.CLICK, x, y, 0);
    }

    static Set<UiEventType> events(UiEventType... types) {
        return Set.of(types);
    }
}
