package com.tkisor.nekojs.api.catalog;

public record JavaModuleImportPolicy(
        String modulePrefix,
        boolean packageModulesOnly,
        boolean namedClassImports,
        boolean classModules
) {
    public static JavaModuleImportPolicy nekoDefault() {
        return new JavaModuleImportPolicy("java:", false, true, true);
    }
}
