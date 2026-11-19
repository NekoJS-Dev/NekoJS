package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Declarative reload event collecting custom stage definitions before atomic commit.
 */
@Doc("Declarative reload event collecting custom stage definitions before atomic commit.")
public final class StageCustomizeEvent {
    private final List<StageDefinitionBuilder> builders = new ArrayList<>();

    /**
     * Creates an empty stage customization collector for a reload pass.
     */
    public StageCustomizeEvent() {
    }

    /**
     * Begins customizing a permanent stage definition.
     *
     * @param stage non-blank stage identifier
     * @return fluent stage definition builder
     */
    @Doc("Begins customizing a permanent stage definition.")
    @Param(name = "stage", value = "non-blank stage identifier")
    @Return("fluent stage definition builder")
    public StageDefinitionBuilder stage(String stage) {
        StageDefinitionBuilder builder = new StageDefinitionBuilder(stage, false, 0L);
        builders.add(builder);
        return builder;
    }

    /**
     * Begins customizing a temporary stage definition with a positive default tick duration.
     *
     * @param stage        non-blank stage identifier
     * @param defaultTicks positive default duration in ticks
     * @return fluent stage definition builder
     */
    @Doc("Begins customizing a temporary stage definition with a positive default tick duration.")
    @Param(name = "stage", value = "non-blank stage identifier")
    @Param(name = "defaultTicks", value = "positive default duration in ticks")
    @Return("fluent stage definition builder")
    public StageDefinitionBuilder temporaryStage(String stage, long defaultTicks) {
        StageDefinitionBuilder builder = new StageDefinitionBuilder(stage, true, defaultTicks);
        builders.add(builder);
        return builder;
    }

    /**
     * Materializes and validates all declared {@link StageDefinition} entries, rejecting duplicate stage keys.
     *
     * @return immutable list of stage definitions in declaration order
     * @throws IllegalArgumentException if the same stage identifier was customized more than once
     */
    @HideFromJS
    public List<StageDefinition> buildDefinitions() {
        List<StageDefinition> definitions = new ArrayList<>(builders.size());
        Set<String> seen = new HashSet<>(builders.size());
        for (StageDefinitionBuilder builder : builders) {
            StageDefinition def = builder.toDefinition();
            if (!seen.add(def.stage())) {
                throw new IllegalArgumentException("[NEKO-4071] Duplicate stage customization for stage: " + def.stage());
            }
            definitions.add(def);
        }
        return List.copyOf(definitions);
    }
}
