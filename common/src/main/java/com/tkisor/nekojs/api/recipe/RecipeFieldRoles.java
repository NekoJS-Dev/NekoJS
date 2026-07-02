package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;

import java.util.Set;

/**
 * Recipe field input/output name conventions (vanilla + common mod practice).
 *
 * <p>Single source of truth for input/output key names. Exposes two granularities:
 * <ul>
 *   <li>{@link #isInputName}/{@link #isOutputName} — the narrow classic-ingredient-slot set, used
 *       as the {@code replaceInput}/{@code replaceOutput} hardcoded fallback so behaviour is
 *       unchanged when a recipe has no schema;</li>
 *   <li>{@link #roleOfName} — the broad set used by schema-scan role inference and the JSON
 *       loader default, which additionally recognises smithing {@code template/base/addition}
 *       and shaped {@code pattern/key} as inputs.</li>
 * </ul>
 */
public final class RecipeFieldRoles {

    private static final Set<String> INPUT_NAMES =
            Set.of("ingredient", "ingredients", "input", "inputs", "key");

    private static final Set<String> OUTPUT_NAMES =
            Set.of("result", "results", "output", "outputs");

    public static boolean isInputName(String name) {
        return INPUT_NAMES.contains(name);
    }

    public static boolean isOutputName(String name) {
        return OUTPUT_NAMES.contains(name);
    }

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
