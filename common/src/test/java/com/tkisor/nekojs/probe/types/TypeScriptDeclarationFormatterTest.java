package com.tkisor.nekojs.probe.types;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TypeScriptDeclarationFormatterTest {
    @Test
    void shortDeclarationsRemainExactlyOneLine() {
        assertEquals("        static create<T>(value: T, fallback?: T): Box<T>;\n",
                format(8, "static create<T>", List.of("value: T", "fallback?: T"), ": Box<T>;"));
    }

    @Test
    void defaultWidthIncludesIndentAndWrapsOnlyAfterTheLimit() {
        String parameter = "value: " + "T".repeat(100);
        String exact = format(4, "run", List.of(parameter), ": R;");
        assertEquals(120, exact.stripTrailing().length());
        assertEquals(1, exact.lines().count());
        assertEquals("    run(\n        " + parameter + "T\n    ): R;\n",
                format(4, "run", List.of(parameter + "T"), ": R;"));
    }

    @Test
    void longSignaturesUseFourAdditionalSpacesAndRetainOptionalRestAndReturnFragments() {
        var parameters = List.of("payload: " + "Payload".repeat(18), "group?: string", "...values: (A | B)[]");
        assertEquals("            execute(\n                " + parameters.getFirst() + ",\n"
                        + "                group?: string,\n                ...values: (A | B)[]\n"
                        + "            ): Result | undefined;\n",
                format(12, "execute", parameters, ": Result | undefined;"));
    }

    @Test
    void genericConstructorAndFunctionHeadsAreOpaque() {
        String parameter = "factory: (key: string, data: Map<string, List<Item>>) => " + "Output".repeat(18);
        assertEquals("    new <T extends Item>(\n        " + parameter + "\n    ): T;\n",
                format(4, "new <T extends Item>", List.of(parameter), ": T;"));
        assertEquals("    const build: (\n        " + parameter + "\n    ) => Item;\n",
                format(4, "const build: ", List.of(parameter), " => Item;"));
    }

    @Test
    void embeddedCommasAndQuotedKeysAreNeverSplitIntoExtraParameters() {
        String parameter = "options: { \"a,b\": [A, B], mapper: (a: A, b: B) => " + "Result".repeat(18) + " }";
        String result = format(4, "convert", List.of(parameter, "last: C"), ": D;");
        assertEquals(List.of("    convert(", "        " + parameter + ",", "        last: C", "    ): D;"), result.lines().toList());
    }

    @Test
    void zeroParameterDeclarationsAreNotArtificiallyExpanded() {
        String suffix = ": " + "LongReturn".repeat(20) + ";";
        assertEquals("    create()" + suffix + "\n", format(4, "create", List.of(), suffix));
    }

    private static String format(int indent, String head, List<String> parameters, String suffix) {
        var output = new StringBuilder();
        TypeScriptDeclarationFormatter.appendCallable(output, indent, head, parameters, suffix);
        return output.toString();
    }
}
