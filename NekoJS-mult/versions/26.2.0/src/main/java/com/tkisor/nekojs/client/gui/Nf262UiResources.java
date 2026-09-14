//? if neoforge {
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.ui.UiComponentRegistry;
import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiDiagnosticSink;
import com.tkisor.nekojs.api.ui.UiEvent;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiResources;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiTextMeasurer;

import net.minecraft.client.Minecraft;

import graal.graalvm.polyglot.Value;

/**
 * NeoForge 26.2 的 {@link UiResources} 装配：把 host Adapter、文本测量、原生 Screen 宿主、
 * 诊断汇与事件桥装进 CLIENT generation 的 JSX UI 契约（jsx-ui-spec「平台边界」）。
 *
 * <p>线程边界：本装配的 owner thread 是真实的 Minecraft 客户端线程
 * （{@code Minecraft#getRunningThread()}）。脚本状态、VNode、host tree 与事件闭包只在
 * 该线程访问；非 owner thread 的写入由 common 侧显式拒绝（jsx-ui-spec「状态线程」）。
 *
 * <p>事件桥：host 侧只把稳定 {@link UiEvent} 交给本类，本类在 owner thread 上用
 * Graal polyglot proxy 把事件转成脚本可读对象并调用该节点绑定的回调。原生
 * {@code GuiGraphics}/{@code Screen}/输入对象从不越过这条边界（AC2）。
 *
 * <p>本类不创建第二 runtime owner、不持有跨 generation 的静态 registry：
 * 实例由平台 {@code IPlatform.uiResources()} 每次装配时构造，root 释放后由 GC 回收。
 */
public final class Nf262UiResources implements UiResources {

    private final int generation;
    private final UiHostAdapterHolder holder = new UiHostAdapterHolder();
    private final java.util.List<UiDiagnostic> diagnostics = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** 装配期回退线程：只有拿不到客户端线程时才使用。 */
    private final Thread assemblyThread = Thread.currentThread();

    public Nf262UiResources(int generation) {
        this.generation = generation;
    }

    @Override
    public com.tkisor.nekojs.api.ui.UiHostAdapter adapter() {
        return holder.adapter;
    }

    @Override
    public UiTextMeasurer textMeasurer() {
        return holder.measurer();
    }

    @Override
    public UiComponentRegistry components() {
        return UiComponentRegistry.EMPTY;
    }

    @Override
    public int generation() {
        return generation;
    }

    @Override
    public UiDiagnosticSink diagnostics() {
        return diagnostics::add;
    }

    /**
     * root 的 client owner thread。
     *
     * <p><b>动态求值</b>：本装配在插件 bootstrap 期构造，那时 {@code Minecraft.gameThread}
     * 还指向 modloading worker；把当时的线程快照下来会把「谁在装配」当成「谁是客户端
     * 线程」，从而把真正的渲染线程判成 OFF_OWNER_THREAD。客户端线程的唯一权威事实源是
     * {@code Minecraft#getRunningThread()}，因此每次调用都重新读它（jsx-ui-spec
     * 「状态线程」要求 owner thread 归属，不是装配时机）。
     */
    @Override
    public Thread ownerThread() {
        try {
            Thread running = Minecraft.getInstance().getRunningThread();
            return running == null ? assemblyThread : running;
        } catch (RuntimeException | LinkageError ignored) {
            return assemblyThread;
        }
    }

    @Override
    public Invalidator invalidator() {
        // 客户端有明确调度边界：失效请求在 owner thread 上直通执行（事件回调与
        // Screen 生命周期本就在 owner thread）；非 owner thread 的请求经 Minecraft 调度。
        return action -> {
            if (Thread.currentThread() == ownerThread()) {
                action.run();
            } else {
                Minecraft.getInstance().execute(action);
            }
        };
    }

    @Override
    public UiEventSink eventSink() {
        return this::dispatch;
    }

    @Override
    public com.tkisor.nekojs.api.ui.UiScreenHost screenHost() {
        return holder.screenHost();
    }

    @Override
    public UiSize viewport() {
        try {
            return new UiSize(Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    Minecraft.getInstance().getWindow().getGuiScaledHeight());
        } catch (RuntimeException | LinkageError ignored) {
            return new UiSize(320, 240);
        }
    }

    /** 已记录诊断（证据/诊断读取面）。 */
    public java.util.List<UiDiagnostic> diagnosticsSnapshot() {
        return java.util.List.copyOf(diagnostics);
    }

    // ---- 事件桥：稳定 UiEvent -> 脚本回调 ----

    private void dispatch(UiHostNode node, UiEvent event) {
        if (node == null || node.disposed()) {
            return;
        }
        Value callback = callbackOf(node, event.type());
        if (callback == null || !callback.canExecute()) {
            return;
        }
        try {
            callback.execute(eventProxy(event));
        } catch (RuntimeException failure) {
            // 事件回调异常进入既有 CLIENT 诊断链路，不吞掉也不扩散到其它控件
            // （jsx-ui-spec「异常处理」）。
            ScriptErrorReporter.recordCallbackError(ScriptType.CLIENT, "ui." + event.type().jsName(), failure);
        }
    }

    private static Value callbackOf(UiHostNode node, UiEventType type) {
        Object raw = node.props().raw(type.prop());
        return raw instanceof Value value ? value : null;
    }

    /** 事件对象只暴露 {@link UiEvent} 的稳定字段（AC2：无原生类型、无版本特有字段）。 */
    private static graal.graalvm.polyglot.proxy.ProxyObject eventProxy(UiEvent event) {
        java.util.Map<String, Object> members = new java.util.LinkedHashMap<>();
        members.put("type", event.type().jsName());
        members.put("mouseX", event.mouseX());
        members.put("mouseY", event.mouseY());
        members.put("button", event.button());
        members.put("scrollDelta", event.scrollDelta());
        members.put("keyCode", event.keyCode());
        members.put("key", event.key());
        members.put("character", event.character());
        members.put("shift", event.shift());
        members.put("control", event.control());
        members.put("alt", event.alt());
        members.put("targetPath", event.targetPath());
        members.put("rootId", event.rootId());
        members.put("generation", event.generation());
        members.put("timestampMillis", event.timestampMillis());
        return graal.graalvm.polyglot.proxy.ProxyObject.fromMap(members);
    }

    /** 延迟构造：只有客户端装配时才创建原生对象（避免服务端误加载）。 */
    private static final class UiHostAdapterHolder {
        private final Nf262UiHostAdapter adapter = new Nf262UiHostAdapter();
        private final Nf262UiScreenHost screenHost = new Nf262UiScreenHost(adapter);

        UiTextMeasurer measurer() {
            try {
                return new Nf262UiTextMeasurer(Minecraft.getInstance().font);
            } catch (RuntimeException | LinkageError ignored) {
                return request -> UiSize.zero();
            }
        }

        Nf262UiScreenHost screenHost() {
            return screenHost;
        }
    }
}
//?}