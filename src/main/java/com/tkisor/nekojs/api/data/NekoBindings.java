package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.core.NekoJSPluginManager;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.ScriptTypedValue;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NekoBindings {
    private static final List<Binding> RAW_BINDINGS = new ArrayList<>();

    private static final ScriptTypedValue<Map<String, Binding>> ENVIRONMENT_BINDINGS =
            ScriptTypedValue.of(type -> new LinkedHashMap<>());

    private static boolean initialized = false;

    private NekoBindings() {}

    static void register(Binding binding) {
        String name = binding.getName();
        ScriptType type = binding.scriptType();

        for (Binding existing : RAW_BINDINGS) {
            if (existing.getName().equals(name)) {
                ScriptType existingType = existing.scriptType();

                if (type == ScriptType.COMMON || existingType == ScriptType.COMMON || type == existingType) {

                    String newClassPath = binding.getType().getName();
                    String existingClassPath = existing.getType().getName();

                    throw new IllegalArgumentException(
                            "Binding 变量冲突: '" + name + "'\n" +
                                    " -> 试图注册到 [" + type.name() + "] 环境 (类路径: " + newClassPath + ")\n" +
                                    " -> 但该变量名已被 [" + existingType.name() + "] 环境占用 (类路径: " + existingClassPath + ")！\n" +
                                    "请检查代码或排查是否有插件冲突。"
                    );
                }
            }
        }

        RAW_BINDINGS.add(binding);
    }

    /**
     * 获取当前环境的绑定集合
     */
    public static synchronized Map<String, Binding> getFor(ScriptType type) {
        if (!initialized) {
            initialize();
        }
        return Collections.unmodifiableMap(ENVIRONMENT_BINDINGS.at(type));
    }

    private static void initialize() {
        var plugins = NekoJSPluginManager.getPlugins();

        plugins.forEach(plugin -> plugin.registerBindings(NekoBindings::register));

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            plugins.forEach(plugin -> plugin.registerClientBindings(NekoBindings::register));
        }

        for (Binding binding : RAW_BINDINGS) {
            for (ScriptType envType : ScriptType.all()) {
                if (binding.canApplyOn(envType)) {
                    ENVIRONMENT_BINDINGS.at(envType).put(binding.getName(), binding);
                }
            }
        }

        RAW_BINDINGS.clear();
        initialized = true;
    }
}