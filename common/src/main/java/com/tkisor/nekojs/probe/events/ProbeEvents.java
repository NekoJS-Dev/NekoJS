package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

/**
 * probe.* 事件组。
 *
 * <p>所有事件均为 {@code ScriptType.SERVER}：probe 命令（{@code /nekojs probe}）在服务端线程执行，
 * 故监听器须在 {@code server_scripts} 注册。STARTUP 的 Graal Context 在内置客户端归客户端线程，
 * 从服务端线程 post 会触发 GraalJS 单线程访问异常——故不用 STARTUP。
 */
public interface ProbeEvents {
    EventGroup GROUP = EventGroup.of("ProbeEvents");

    /**
     * 在 probe 渲染类型声明**之前**触发。脚本通过 {@link ProbeModifyTypeEventJS#forClass(String)}
     * 拿到 {@link ClassEditor}，对反射产出的 {@code TypeDecl} IR 做参数级编辑（改名/改类型/隐藏/
     * 加参数/文档…）。被触及的类随后经 {@code TypeScriptClassRenderer} 重新渲染并覆盖声明缓存；
     * 未触及的类渲染结果与触及前一致（TypeReflector/TypeScriptClassRenderer 镜像旧渲染契约）。
     *
     * <p>典型用法（server_scripts）：
     * <pre>{@code
     * ProbeEvents.modifyType.listen(event => {
     *     event.forClass("net.minecraft.world.entity.player.Player")
     *          .renameMethod("getXxx", "getCustom")
     *          .changeParamType("addItem", 0, "net.minecraft.world.item.ItemStack");
     * });
     * }</pre>
     */
    EventBusJS<ProbeModifyTypeEventJS, Void> MODIFY_TYPE =
            GROUP.server("modifyType", ProbeModifyTypeEventJS.class);

    /**
     * 全局类型重定向：把某 Java 全限定名处处改写为自定义类型。在 IR 构建后应用（替换 SYMBOL 类型槽），
     * 对 TS（重渲染被触及类）与 Python 均生效。需要 IR（监听器使 coordinator 构建 IR）。
     */
    EventBusJS<ProbeAssignTypeEventJS, Void> ASSIGN_TYPE =
            GROUP.server("assignType", ProbeAssignTypeEventJS.class);

    /**
     * 登记额外全局声明（名字+类型）。各 backend 在自己语言里渲染（TS→{@code @manual/globals.d.ts}、
     * Python→{@code nekojs/__init__.pyi}）。
     */
    EventBusJS<ProbeAddGlobalEventJS, Void> ADD_GLOBAL =
            GROUP.server("addGlobal", ProbeAddGlobalEventJS.class);

    /**
     * 登记编辑器片段（VSCode {@code .code-snippets}）。当前仅 TS backend 消费
     * （合并进 {@code nekojs/.vscode/nekojs.code-snippets}）。
     */
    EventBusJS<ProbeSnippetEventJS, Void> SNIPPETS =
            GROUP.server("snippets", ProbeSnippetEventJS.class);
}
