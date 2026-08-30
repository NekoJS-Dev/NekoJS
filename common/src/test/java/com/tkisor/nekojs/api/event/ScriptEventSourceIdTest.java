package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: a {@code ScriptEvents} declaration must record the id of the script that
 * made it, so per-script STARTUP reload ({@code ScriptEventRegistry.clearDefinitions(
 * STARTUP, scriptId)}) can clear exactly that script's definitions. The fallback constant
 * {@code "nekojs:startup/script_events"} never matches a reload id.
 *
 * <p>The id is read from the entered Graal context, so the call has to come from guest
 * code — evaluating {@code event.register(...)} in JS is the shape production uses.
 */
class ScriptEventSourceIdTest {

    private static final String SCRIPT_ID = "startup_scripts/events.js";

    @Test
    void registrationFromGuestCodeCarriesTheCallingScriptId() {
        AtomicReference<String> captured = new AtomicReference<>();
        ScriptEventRegistrationEvent event = new ScriptEventRegistrationEvent(
                ScriptType.SERVER,
                (targetType, groupName, eventName, sourceScriptId) -> captured.set(sourceScriptId));

        try (Context ctx = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(ctx, ScriptType.STARTUP);
            ScriptContextRegistry.switchCurrentScriptId(ctx, SCRIPT_ID);
            ctx.getBindings("js").putMember("event", event);

            ctx.eval("js", "event.register('grp', 'server_evt')");
            assertEquals(SCRIPT_ID, captured.get(), "positional form must resolve the calling script id");

            captured.set(null);
            ctx.eval("js", "event.register({ group: 'grp', name: 'other_evt' })");
            assertEquals(SCRIPT_ID, captured.get(), "object form must resolve the calling script id");
        } finally {
            // Context 由 try-with-resources 关闭，注册表条目随 WeakHashMap 回收
        }
    }
}
