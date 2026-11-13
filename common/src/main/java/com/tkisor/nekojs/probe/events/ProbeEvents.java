package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

/**
 * The probe.* event group.
 *
 * <p>Every event is {@code ScriptType.SERVER}: the probe command ({@code /nekojs probe}) runs on the server thread,
 * so listeners must be registered in {@code server_scripts}. On the built-in client the STARTUP Graal Context
 * belongs to the client thread, and posting from the server thread raises a GraalJS single-thread access error,
 * which is why STARTUP is not used.
 */
public interface ProbeEvents {
    EventGroup GROUP = EventGroup.of("ProbeEvents");

    /**
     * Fires **before** probe renders the type declarations. Through
     * {@link ProbeModifyTypeEventJS#forClass(String)} scripts get a {@link ClassEditor} and edit the
     * {@code TypeDecl} IR produced by reflection at parameter level (rename, retype, hide, add parameters,
     * docs...). Touched classes are then re-rendered by {@code TypeScriptClassRenderer}, which replaces the
     * cached declarations; untouched classes render exactly as they did before, because TypeReflector and
     * TypeScriptClassRenderer keep the previous rendering behavior.
     *
     * <p>Typical use (in server_scripts; the bus is a callable proxy, so a direct call registers the listener):
     * <pre>{@code
     * ProbeEvents.modifyType(event => {
     *     event.forClass("net.minecraft.world.entity.player.Player")
     *          .renameMethod("getXxx", "getCustom")
     *          .changeParamType("addItem", 0, "net.minecraft.world.item.ItemStack");
     * });
     * }</pre>
     */
    EventBusJS<ProbeModifyTypeEventJS, Void> MODIFY_TYPE =
            GROUP.server("modifyType", ProbeModifyTypeEventJS.class);

    /**
     * Redirects types globally: rewrites a Java fully qualified name to a custom type everywhere. Applied after the
     * IR is built (it replaces SYMBOL type slots) and takes effect for both TS (re-renders the touched classes) and
     * Python. Requires the IR (a listener makes the coordinator build it).
     */
    EventBusJS<ProbeAssignTypeEventJS, Void> ASSIGN_TYPE =
            GROUP.server("assignType", ProbeAssignTypeEventJS.class);

    /**
     * Registers extra global declarations (name plus type). Each backend renders them in its own language
     * (TS -> {@code @manual/globals.d.ts}, Python -> {@code nekojs/__init__.pyi}).
     */
    EventBusJS<ProbeAddGlobalEventJS, Void> ADD_GLOBAL =
            GROUP.server("addGlobal", ProbeAddGlobalEventJS.class);

    /**
     * Registers editor snippets (VSCode {@code .code-snippets}). Only the TS backend consumes them today; they are
     * merged into {@code nekojs/.vscode/nekojs.code-snippets}.
     */
    EventBusJS<ProbeSnippetEventJS, Void> SNIPPETS =
            GROUP.server("snippets", ProbeSnippetEventJS.class);
}
