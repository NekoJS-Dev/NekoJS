//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptInputShapeProvider;
import com.tkisor.nekojs.api.ScriptOptionSchema;

/** Canonical 26.x declaration fields shared by validation, builders and Probe. */
public final class PostEffectOptions implements ScriptInputShapeProvider {
    static final ScriptOptionSchema SCHEMA = new ScriptOptionSchema(
            ScriptOptionSchema.string("chainJson"),
            ScriptOptionSchema.string("fragmentShader"),
            ScriptOptionSchema.string("fragmentShaderId"),
            ScriptOptionSchema.strings("fragmentShaders"),
            ScriptOptionSchema.strings("vertexShaders"),
            ScriptOptionSchema.string("uniformsJson"),
            ScriptOptionSchema.number("blurRadius", 0, 64),
            ScriptOptionSchema.integer("blurRounds", 1, 8));

    public PostEffectOptions() {}

    @Override
    public AdapterInputShape inputShape() { return SCHEMA.inputShape(); }
}
//?}
