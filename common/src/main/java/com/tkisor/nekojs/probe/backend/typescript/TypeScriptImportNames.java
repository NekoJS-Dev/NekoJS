package com.tkisor.nekojs.probe.backend.typescript;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Allocates private import names by Java identity while preserving public module declarations. */
final class TypeScriptImportNames {
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u(?:\\{([0-9a-fA-F]{1,6})\\}|([0-9a-fA-F]{4}))");
    record Symbol(String fqn, String exportedName) {}

    private final Map<Symbol, String> names = new LinkedHashMap<>();
    private boolean aliases;

    TypeScriptImportNames(Set<Symbol> imports, Set<String> declarations, Set<String> extraImports) {
        Map<String, Integer> counts = new HashMap<>();
        Set<String> reserved = new HashSet<>(declarations);
        for (String statement : extraImports) reserveImportNames(statement, reserved);
        Set<String> occupied = new HashSet<>(reserved);
        for (Symbol symbol : imports) {
            counts.merge(symbol.exportedName(), 1, Integer::sum);
            reserved.add(symbol.exportedName());
        }
        for (Symbol symbol : imports.stream().sorted(Comparator.comparing(Symbol::fqn)
                .thenComparing(Symbol::exportedName)).toList()) {
            String name = symbol.exportedName();
            if (occupied.contains(name) || counts.get(name) > 1) {
                String base = "$NekoImport" + name;
                name = base;
                for (int suffix = 1; reserved.contains(name); suffix++) name = base + suffix;
                reserved.add(name);
                aliases = true;
            }
            names.put(symbol, name);
        }
    }

    private static void reserveImportNames(String statement, Set<String> reserved) {
        // Opaque imports stay unchanged. Reserving every identifier token also covers default and namespace bindings.
        String decoded = UNICODE_ESCAPE.matcher(statement).replaceAll(match -> {
            int codePoint = Integer.parseInt(match.group(1) != null ? match.group(1) : match.group(2), 16);
            return java.util.regex.Matcher.quoteReplacement(Character.isValidCodePoint(codePoint)
                    ? new String(Character.toChars(codePoint)) : match.group());
        });
        StringBuilder identifier = new StringBuilder();
        for (int codePoint : decoded.codePoints().toArray()) {
            if (codePoint == '$' || codePoint == '_' || Character.isUnicodeIdentifierPart(codePoint)) {
                identifier.appendCodePoint(codePoint);
            } else if (!identifier.isEmpty()) {
                reserved.add(identifier.toString());
                identifier.setLength(0);
            }
        }
        if (!identifier.isEmpty()) reserved.add(identifier.toString());
    }

    String reference(String fqn, String exportedName) {
        return names.getOrDefault(new Symbol(fqn, exportedName), exportedName);
    }

    boolean hasAliases() { return aliases; }

    Map<Symbol, String> bindings() { return Map.copyOf(names); }
}
