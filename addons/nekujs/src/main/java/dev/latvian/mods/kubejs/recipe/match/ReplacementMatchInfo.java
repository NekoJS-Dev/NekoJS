package dev.latvian.mods.kubejs.recipe.match;

import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import java.util.Objects;
import java.util.Optional;

public record ReplacementMatchInfo(ReplacementMatch match, boolean exact, Optional<RecipeComponent<?>> componentType) {
    public ReplacementMatchInfo { Objects.requireNonNull(match); Objects.requireNonNull(componentType); }
}
