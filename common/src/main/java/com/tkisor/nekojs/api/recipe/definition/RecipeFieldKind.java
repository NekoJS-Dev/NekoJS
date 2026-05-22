package com.tkisor.nekojs.api.recipe.definition;

import java.util.Locale;

public enum RecipeFieldKind {
    JSON,
    STRING,
    INT,
    NUMBER,
    BOOLEAN,
    INGREDIENT,
    ITEM_STACK,
    FLUID_STACK,
    FLUID_INGREDIENT,
    SIZED_FLUID_INGREDIENT;

    public static RecipeFieldKind parse(String value) {
        if (value == null || value.isBlank()) {
            return JSON;
        }
        return RecipeFieldKind.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
