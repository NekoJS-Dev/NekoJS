package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.ScriptType;

import java.util.Objects;

/** An owner-attributed module export collected from the ordinary binding hook. */
public record ModuleBinding(String owner, BindingExposure.Category category, ScriptType scriptType, Binding binding) {
    public ModuleBinding {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(scriptType, "scriptType");
        Objects.requireNonNull(binding, "binding");
        if (!owner.matches("[a-z0-9_.-]+") || !binding.name().matches("[A-Za-z_$][A-Za-z0-9_$]*")
                || RESERVED_EXPORTS.contains(binding.name())) {
            throw new IllegalArgumentException("[NEKO-4057] API module requires a mod owner ID and an identifier export name");
        }
    }

    private static final java.util.Set<String> RESERVED_EXPORTS = java.util.Set.of(
            "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
            "do", "else", "enum", "export", "extends", "false", "finally", "for", "function", "if", "implements",
            "import", "in", "instanceof", "interface", "let", "new", "null", "package", "private", "protected",
            "public", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof", "var",
            "void", "while", "with", "yield", "namespace", "__neko_module");

    public String moduleId() {
        return "nekojs/api/" + category.path() + (owner.equals("nekojs") ? "" : "/" + owner);
    }
}
