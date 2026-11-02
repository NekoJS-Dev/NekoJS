//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptInputShapeProvider;
import com.tkisor.nekojs.api.ScriptOptionSchema;

/** Canonical 1.21.1 declaration fields shared by validation, builders and Probe. */
public final class PostEffectOptions implements ScriptInputShapeProvider {
    static final ScriptOptionSchema SCHEMA = new ScriptOptionSchema(
            ScriptOptionSchema.string("chainJson"),
            ScriptOptionSchema.string("program"));

    public PostEffectOptions() {}

    @Override
    public AdapterInputShape inputShape() { return SCHEMA.inputShape(); }
}
//?}
