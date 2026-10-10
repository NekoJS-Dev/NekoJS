package com.tkisor.nekojs.probe.backend.python;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Allocates module-local imports without replacing public declarations or another imported type. */
final class PythonImports {
    private record Symbol(String fqn, String exportedName) {}

    private final Set<String> reserved;
    private final ApiTypeRefPyRenderer types;
    private final Map<Symbol, String> names = new LinkedHashMap<>();
    private final Map<String, Symbol> imports = new TreeMap<>();
    private final Map<String, String> typeNames = new LinkedHashMap<>();

    PythonImports(Set<String> reserved, ApiTypeRefPyRenderer types) {
        this.reserved = new LinkedHashSet<>(reserved);
        this.types = types;
    }

    String type(String fqn) {
        String name = symbol(fqn, types.symbolName(fqn));
        typeNames.put(fqn, name);
        return name;
    }

    String symbol(String fqn, String exportedName) {
        Symbol symbol = new Symbol(fqn, exportedName);
        String known = names.get(symbol);
        if (known != null) return known;
        String name = exportedName;
        if (reserved.contains(name)) {
            name = "_NekoImport_" + exportedName;
            for (int suffix = 1; reserved.contains(name); suffix++) name = "_NekoImport_" + exportedName + suffix;
        }
        reserved.add(name);
        names.put(symbol, name);
        imports.put(name, symbol);
        return name;
    }

    Set<String> localNames() { return Set.copyOf(imports.keySet()); }

    Map<String, String> typeNames() { return Map.copyOf(typeNames); }

    void appendTo(StringBuilder output) {
        for (var entry : imports.entrySet()) {
            Symbol symbol = entry.getValue();
            int dot = symbol.fqn().lastIndexOf('.');
            output.append("from nekojs._java.").append(symbol.fqn(), 0, dot).append(" import ")
                    .append(symbol.exportedName());
            if (!symbol.exportedName().equals(entry.getKey())) output.append(" as ").append(entry.getKey());
            output.append('\n');
        }
    }
}
