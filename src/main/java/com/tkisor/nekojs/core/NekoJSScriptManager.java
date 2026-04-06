package com.tkisor.nekojs.core;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.NekoBindings;
import com.tkisor.nekojs.api.event.NekoEventGroups;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NekoJS 脚本引擎核心生命周期调度器
 */
public final class NekoJSScriptManager {

    private final Map<ScriptType, Context> contexts = new ConcurrentHashMap<>();
    private final Map<ScriptType, List<ScriptContainer>> scripts = new ConcurrentHashMap<>();

    public NekoJSScriptManager() {
        for (ScriptType type : ScriptType.values()) {
            scripts.put(type, new ArrayList<>());
        }
    }

    /**
     * 一次性扫描并发现所有环境类型 (STARTUP, SERVER, CLIENT, COMMON) 的脚本文件
     */
    public void discoverScripts() {
        for (ScriptType type : ScriptType.values()) {
            discoverScripts(type);
        }
    }

    /**
     * 发现并准备环境，但不执行
     */
    public void discoverScripts(ScriptType type) {
        List<ScriptContainer> discovered = ScriptLocator.discover(type);
        scripts.put(type, discovered);
        type.logger().info("发现了 {} 个 {} 脚本。", discovered.size(), type.name());
    }

    /**
     * 加载并顺序执行所有脚本
     */
    public void loadScripts(ScriptType type) {
        List<ScriptContainer> typeScripts = scripts.get(type);
        if (typeScripts == null || typeScripts.isEmpty()) return;

        Context ctx = contexts.computeIfAbsent(type, this::initContext);

        for (ScriptContainer script : typeScripts) {
            if (!script.disabled) {
                runScript(ctx, script);
            }
        }
    }

    private Context initContext(ScriptType type) {
        Context ctx = NekoSandboxBuilder.build(type);

        var bindings = ctx.getBindings("js");

        var values = NekoEventGroups.all().values();
        NekoJS.LOGGER.info("正在为 {} 注册 {} 个事件组...", type.name(), values.size());
        for (var group : values) {
            bindings.putMember(group.name(), new EventGroupJS(group, type));
        }

        Map<String, Binding> allBindings = NekoBindings.all();

        allBindings.forEach((name, binding) -> {
            if (binding.isValidFor(type)) {

                Object obj = binding.getObject();

                if (binding.isStaticClass()) {
                    Value javaType = bindings.getMember("Java").invokeMember("type", ((Class<?>) obj).getName());
                    bindings.putMember(name, javaType);
                } else {
                    bindings.putMember(name, obj);
                }

            }
        });

        return ctx;
    }

    private void runScript(Context ctx, ScriptContainer script) {
        try {
            Path relativePath = NekoJSPaths.ROOT.relativize(script.path);
            String requirePath = "./" + relativePath.toString().replace("\\", "/");

            ctx.eval("js", "require").execute(requirePath);

            NekoErrorTracker.clear(script.id);
            script.disabled = false;
            script.lastError = null;

        } catch (Throwable t) {
            script.disabled = true;
            script.lastError = t;

            NekoErrorTracker.record(script, t);

            script.type.logger().error("脚本执行失败: {}", script.id.toString(), t);
        }
    }

    /**
     * 重载指定类型的脚本
     * <p>
     * 该方法会清理指定脚本类型的事件总线，关闭旧的执行上下文，
     * 然后重新发现并加载该类型的所有脚本
     * </p>
     * 
     * @param type 要重载的脚本类型
     */
    public void reloadScripts(ScriptType type) {
        type.logger().info("正在重载 {} 脚本...", type.name());

        for (var group : NekoEventGroups.all().values()) {
            // 清理全部会导致某些情况其他类型的监听器被清除
            group.clearListeners(type);
        }

        Context oldContext = contexts.remove(type);
        if (oldContext != null) {
            try {
                oldContext.close();
            } catch (Exception e) {
                type.logger().warn("关闭旧上下文时发生异常", e);
            }
        }

        discoverScripts(type);
        loadScripts(type);

        type.logger().info("{} 脚本重载完毕。", type.name());
    }

    public boolean hasScripts(ScriptType type) {
        return !scripts.get(type).isEmpty();
    }
}