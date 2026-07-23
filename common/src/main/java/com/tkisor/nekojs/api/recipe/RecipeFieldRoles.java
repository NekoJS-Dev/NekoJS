package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;

import java.util.Set;

/**
 * Recipe field input/output name conventions (vanilla + common mod practice).
 *
 * <p>Broad role inference used by schema-scan role inference and the JSON
 * loader default, which recognises smithing {@code template/base/addition}
 * and shaped {@code pattern/key} as inputs, plus classic ingredient/result slots.</p>
 */
public final class RecipeFieldRoles {

    /**
     * Broad role inference: {@code OUTPUT} for result/output (incl. result-prefixed),
     * {@code INPUT} for classic ingredient slots plus smithing template/base/addition and shaped
     * pattern/key, else {@code OTHER}.
     */
    public static RecipeFieldRole roleOfName(String name) {
        if (name == null) {
            return RecipeFieldRole.OTHER;
        }
        if (name.equals("result") || name.equals("results") || name.equals("output") || name.equals("outputs")
                || name.startsWith("result")) {
            return RecipeFieldRole.OUTPUT;
        }
        if (name.equals("ingredient") || name.equals("ingredients") || name.equals("input") || name.equals("inputs")
                || name.equals("key") || name.equals("pattern")
                || name.equals("template") || name.equals("base") || name.equals("addition")
                || name.startsWith("ingredient")) {
            return RecipeFieldRole.INPUT;
        }
        return RecipeFieldRole.OTHER;
    }

    private RecipeFieldRoles() {
    }
}
