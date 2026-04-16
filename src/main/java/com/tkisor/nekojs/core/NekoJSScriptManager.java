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
import com.tkisor.nekojs.script.ScriptTypedValue;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.*;

/**
 * NekoJS 脚本引擎核心生命周期调度器
 */
public final class NekoJSScriptManager {

    private final ScriptTypedValue<Context> contexts = ScriptTypedValue.ofNullable(this::initContext);
    private final ScriptTypedValue<List<ScriptContainer>> scripts = ScriptTypedValue.of(type -> new ArrayList<>());
    private final ScriptPropertyRegistry scriptPropertyRegistry = new ScriptPropertyRegistry.Impl();

    private static final Map<Context, ScriptType> CONTEXT_TYPE_MAP = Collections.synchronizedMap(new WeakHashMap<>());

    public NekoJSScriptManager() {
    }

    public void registerScriptProperty() {
        for (var plugin : NekoJSPluginManager.getPlugins()) {
            plugin.registerScriptProperty(scriptPropertyRegistry);
        }
    }

    /**
     * 一次性扫描并发现所有环境类型 (STARTUP, SERVER, CLIENT) 的脚本文件
     */
    public void discoverScripts() {
        for (ScriptType type : ScriptType.all()) {
            discoverScripts(type);
        }
    }

    /**
     * 发现并准备环境，但不执行
     */
    public void discoverScripts(ScriptType type) {
        List<ScriptContainer> discovered = ScriptLocator.discover(type);
        scripts.set(type, discovered);
        type.logger().info("发现了 {} 个 {} 脚本。", discovered.size(), type.name());
    }

    /**
     * 加载并顺序执行所有脚本
     */
    public void loadScripts(ScriptType type) {
        List<ScriptContainer> typeScripts = scripts.at(type);
        if (typeScripts == null || typeScripts.isEmpty()) return;

        Context ctx = contexts.at(type);

        for (ScriptContainer script : typeScripts) {
            if (!script.disabled) {
                runScript(ctx, script);
            }
        }
    }

    private Context initContext(ScriptType type) {
        Context ctx = NekoSandboxBuilder.build(type);
        CONTEXT_TYPE_MAP.put(ctx, type);

        var bindings = ctx.getBindings("js");

        var values = NekoEventGroups.all().values();
        NekoJS.LOGGER.info("正在为 {} 注册 {} 个事件组...", type.name(), values.size());
        for (var group : values) {
            bindings.putMember(group.name(), new EventGroupJS(group, type));
        }

        Map<String, Binding> environmentBindings = NekoBindings.getFor(type);

        environmentBindings.forEach((name, binding) -> {
            Object obj = binding.getObject();

            if (binding.isStaticClass()) {
                // 如果是静态类，利用 Java.type 包装暴露给 JS
                Value javaType = bindings.getMember("Java").invokeMember("type", ((Class<?>) obj).getName());
                bindings.putMember(name, javaType);
            } else {
                // 普通对象直接注入
                bindings.putMember(name, obj);
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
     */
    public void reloadScripts(ScriptType type) {
        type.logger().info("正在重载 {} 脚本...", type.name());

        for (var group : NekoEventGroups.all().values()) {
            group.clearListeners(type);
        }

        Context oldContext = contexts.set(type, null);
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
        List<ScriptContainer> typeScripts = scripts.at(type);
        return typeScripts != null && !typeScripts.isEmpty();
    }

    /**
     * 从上下文获取对应的脚本类型
     * @param context 执行上下文
     * @return 对应的脚本类型
     */
    public static ScriptType getTypeFromContext(Context context) {
        return CONTEXT_TYPE_MAP.get(context);
    }
}