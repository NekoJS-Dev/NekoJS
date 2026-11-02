package com.tkisor.nekojs.probe.testfixture.input;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptInputShapeProvider;
import com.tkisor.nekojs.api.annotation.ScriptInput;

import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

public final class ScriptInputFixtures {
    private ScriptInputFixtures() {}

    public static final class OptionsShape implements ScriptInputShapeProvider {
        public OptionsShape() {}

        @Override
        public AdapterInputShape inputShape() {
            return object(Slot.req("format", union(literal("fast"), literal("precise"))),
                    Slot.opt("labels", raw("Record<string, string>")),
                    Slot.opt("target", host(othermod.events.EventTypes.Key.class)),
                    Slot.opt("count", number()));
        }
    }

    public static class PostBuilder {
        public PostBuilder(@ScriptInput(OptionsShape.class) Map<String, Object> options) {}

        public void configure(@ScriptInput(OptionsShape.class) Map<String, Object> options) {}

        public Map<String, Object> getOptions() { return Map.of(); }

        public void setOptions(@ScriptInput(OptionsShape.class) Map<String, Object> options) {}

        public void ordinary(Map<String, Object> options) {}
    }

    public static final class InheritedPostBuilder extends PostBuilder {
        public InheritedPostBuilder() { super(Map.of()); }
    }

    public static final class UnusedPayload {
        public boolean shouldNotAppear;
    }

    public static final class RegistryShape implements ScriptInputShapeProvider {
        public RegistryShape() {}

        @Override
        public AdapterInputShape inputShape() { return registry("Item"); }
    }

    public static final class RegistryConsumer {
        public void select(@ScriptInput(RegistryShape.class) Map<String, Object> input) {}
    }

    public static final class InvalidShape implements ScriptInputShapeProvider {
        private InvalidShape() {}

        @Override
        public AdapterInputShape inputShape() { return string(); }
    }

    public static final class InvalidConsumer {
        public void configure(@ScriptInput(InvalidShape.class) Map<String, Object> options) {}
    }
}
