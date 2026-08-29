package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NekoJsxAttributesTest {

    @Test
    void evaluatesBooleanExpressionAndSpreadAttributesInSourceOrder() {
        String out = NekoJsxCompiler.compileJsx(Path.of("attributes.jsx"), """
            const base = { first: 1, overridden: 'base' };
            globalThis.result = <Widget enabled value={1 + 2} {...base} overridden="explicit"/>;
            """).code();
        String runtime = "globalThis.Widget = 'Widget';\n"
            + "globalThis.__nekoJsxFactory = (type, props) => ({ type, props });\n";

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(
            runtime + out + "\nJSON.stringify(globalThis.result)")) {
            assertEquals(
                "{\"type\":\"Widget\",\"props\":{\"enabled\":true,\"value\":3,\"first\":1,\"overridden\":\"explicit\"}}",
                evaluation.value().asString()
            );
        }
    }

    @Test
    void evaluatesInterleavedAttributesAndSpreadsFromLeftToRight() {
        String out = NekoJsxCompiler.compileJsx(Path.of("attribute-order.jsx"), """
            const trace = [];
            const mark = (name, value) => { trace.push(name); return value; };
            globalThis.result = <Widget x={mark('first', 'explicit')} {...mark('spread-one', { x: 'one', y: 1 })} y={mark('middle', 2)} {...mark('spread-two', { y: 3, z: 4 })}/>;
            globalThis.trace = trace.join(',');
            """).code();
        String runtime = "globalThis.Widget = 'Widget';\n"
            + "globalThis.__nekoJsxFactory = (type, props) => ({ type, props });\n";

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(
            runtime + out + "\nJSON.stringify({ result: globalThis.result, trace: globalThis.trace })")) {
            assertEquals(
                "{\"result\":{\"type\":\"Widget\",\"props\":{\"x\":\"one\",\"y\":3,\"z\":4}},\"trace\":\"first,spread-one,middle,spread-two\"}",
                evaluation.value().asString()
            );
        }
    }

    @Test
    void lowersNestedJsxInsideAttributeExpression() {
        String out = NekoJsxCompiler.compileJsx(Path.of("nested-attribute.jsx"),
            "globalThis.result = <Wrapper child={<span>text</span>}/>;").code();
        String runtime = "globalThis.Wrapper = 'Wrapper';\n"
            + "globalThis.__nekoJsxFactory = (type, props, ...children) => ({ type, props, children });\n";

        try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(
            runtime + out + "\nJSON.stringify(globalThis.result)")) {
            assertEquals(
                "{\"type\":\"Wrapper\",\"props\":{\"child\":{\"type\":\"span\",\"props\":null,\"children\":[\"text\"]}},\"children\":[]}",
                evaluation.value().asString()
            );
        }
    }
}
