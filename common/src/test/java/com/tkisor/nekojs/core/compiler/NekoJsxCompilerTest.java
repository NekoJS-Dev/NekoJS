package com.tkisor.nekojs.core.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link NekoJsxCompiler#compileJsx} lowers JSX correctly. Covers the shape of the
 * classic runtime output, HTML entity decoding, namespaced tags, generic components and child
 * handling. Whether the result is valid at runtime (the factory calls can be evaluated by graaljs)
 * is verified end to end in game.
 */
class NekoJsxCompilerTest {

    private static String compile(String src) {
        return NekoJsxCompiler.compileJsx(Path.of("test.jsx"), src).code();
    }

    @Test
    void lowersBasicElementToFactoryCall() {
        String out = compile("const el = <div class=\"x\">hi</div>");
        assertTrue(out.contains("globalThis.__nekoJsxFactory("), "须 lowering 成 factory 调用: " + out);
        assertTrue(out.contains("'div'"), "小写标签须作字符串: " + out);
        assertTrue(out.contains("class: "), "属性键须保留: " + out);
        assertTrue(out.contains("hi"), "文本子节点须保留: " + out);
    }

    @Test
    void lowersSelfClosingAndFragment() {
        String out1 = compile("const a = <br/>");
        assertTrue(out1.contains("globalThis.__nekoJsxFactory(") && out1.contains("'br'"), "自闭合须正常: " + out1);
        String out2 = compile("const f = <>a<b/>c</>");
        assertTrue(out2.contains("globalThis.__nekoJsxFragment("), "fragment 须用 fragment factory: " + out2);
    }

    @Test
    void decodesHtmlEntitiesInText() {
        // HTML entities in JSX text must be decoded (&amp; to &, &lt; to <, &gt; to >, &#39; to
        // ', &quot; to ")
        String out = compile("const el = <p>A &amp; B &lt; C &gt; D</p>");
        // After decoding, the text should be "A & B < C > D"
        assertTrue(out.contains("A & B"), "&amp; 须解码为 &: " + out);
        assertTrue(out.contains("B < C"), "&lt; 须解码为 <: " + out);
        assertTrue(out.contains("C > D"), "&gt; 须解码为 >: " + out);
        assertFalse(out.contains("&amp;"), "不应残留 &amp;: " + out);
        assertFalse(out.contains("&lt;"), "不应残留 &lt;: " + out);
    }

    @Test
    void decodesNumericAndQuoteEntities() {
        String out = compile("const el = <p>it&#39;s &quot;ok&quot;</p>");
        // &#39; decodes to ', which is escaped as \' because the whole text is wrapped in a
        // single-quoted string
        assertFalse(out.contains("&#39;"), "&#39; 须解码（不再残留实体）: " + out);
        assertTrue(out.contains("\\'s "), "&#39; 解码为 '（字符串内转义为 \\') : " + out);
        assertTrue(out.contains("\"ok\""), "&quot; 须解码为 \": " + out);
    }

    @Test
    void decodesEntitiesInStringAttributeValues() {
        // Matches React/TS JSX semantics: entities inside a string attribute value must be decoded.
        String out = compile("const el = <a title=\"A &amp; B\"/>");
        assertFalse(out.contains("&amp;"), "&amp; 须解码为 &: " + out);
        assertTrue(out.contains("'A & B'"), "解码后的值须以转义后的 JS 字符串字面量出现: " + out);
    }

    @Test
    void multiLineStringAttributeProducesValidJsLiteral() {
        // Multi-line attribute value: a newline must be escaped as \n, since copying it verbatim yields an invalid JS string literal
        String out = compile("const el = <a title=\"line1\nline2\"/>");
        assertTrue(out.contains("line1\\nline2"), "换行须转义: " + out);
        CompilerExecutionAssertions.parse(out);
    }

    @Test
    void keepsSingleSpaceBetweenElementsOnSameLine() {
        // React rule: a space between elements on the same line is real content.
        String out = compile("const el = <p><b>Hi</b> <i>There</i></p>");
        assertTrue(out.contains(", ' ', "), "元素间单空格须保留为文本子节点: " + out);
    }

    @Test
    void removesWhitespaceRunsThatSpanNewlines() {
        // React rule: a whitespace run spanning a newline (a newline next to a tag) is removed; a newline between text on the same line collapses to one space
        String out = compile("const el = <p>\n  hello\n  world\n</p>");
        assertTrue(out.contains("'hello world'"), "行间换行折叠为单空格: " + out);
    }

    @Test
    void automaticRuntimePassesKeyAsThirdArgument() {
        String out = NekoJsxCompiler.compileJsx(Path.of("key.jsx"),
                "const a = <Item key={id}/>;\nconst b = <List key=\"k\">{a}{a}</List>", true).code();
        assertTrue(out.contains("jsx(Item, null, (id))"), "key 表达式须作为第三实参: " + out);
        assertTrue(out.contains("jsxs(List, {children: [(a), (a)]}, 'k')"), "字符串 key 须解码并作为第三实参: " + out);
        assertFalse(out.contains("key: id"), "key 不得留在 props: " + out);
    }

    @Test
    void automaticRuntimeKeepsKeyInPropsForClassic() {
        String out = NekoJsxCompiler.compileJsx(Path.of("key-classic.jsx"), "const a = <Item key={id}/>", false).code();
        assertTrue(out.contains("key: (id)"), "classic runtime 的 key 留在 props: " + out);
    }

    @Test
    void preservesNamespaceTagNames() {
        // Namespace tag <svg:rect/>: the whole name goes to the factory as one string.
        String out = compile("const el = <svg:rect xlink:href=\"#shape\"/>");
        assertTrue(out.contains("'svg:rect'"), "命名空间标签须作字符串字面量: " + out);
        assertTrue(out.contains("'xlink:href': '#shape'"), "命名空间属性须使用合法属性键（转义字面量）: " + out);

        String componentOut = compile("const el = <Foo.Bar/>");
        assertTrue(componentOut.contains("__nekoJsxFactory(Foo.Bar, null)"),
            "成员组件表达式仍须作为表达式透传: " + componentOut);
    }

    @Test
    void preservesGenericComponentSyntaxInTsx() {
        // Generic component <Foo<number>/> in TSX: the JSX layer passes Foo through and the TS erasure stage handles <number>
        // This tests the full compileTsx output: Foo stays as the component reference and <number> is erased
        String src = "const el = <Foo<number>/>";
        String out = NekoJsxCompiler.compileTsx(Path.of("test.tsx"), src).code();
        assertTrue(out.contains("Foo"), "组件名 Foo 须保留: " + out);
        assertFalse(out.contains("<number>"), "泛型 <number> 须被 TS 擦除: " + out);
    }

    @Test
    void expressionChildrenAndSpreadAttributes() {
        String out = compile("const el = <ul {...{class: 'x'}}>{items.map(i => <li>{i}</li>)}</ul>");
        assertTrue(out.contains("...{class: 'x'}"), "spread 属性须保留: " + out);
        assertTrue(out.contains("items.map"), "表达式子节点须保留: " + out);
    }

    @Test
    void tsxGenericComponentWithObjectTypePassesComponentValueToFactory() {
        String source = "const Foo = () => 'component'; const value = <Foo<{x: number}>/>; globalThis.result = value";
        String out = NekoJsxCompiler.compileTsx(Path.of("generic-component.tsx"), source).code();
        String runtime = "globalThis.__nekoJsxFactory = (type, props) => typeof type;\n";

        try (CompilerExecutionAssertions.Evaluation evaluation =
                 CompilerExecutionAssertions.eval(runtime + out + "\nglobalThis.result")) {
            assertEquals("function", evaluation.value().asString(),
                "generic component must reach factory as Foo value, not a string: " + out);
        }
    }

    @Test
    void tsxGenericArrowsAreNotParsedAsJsx() {
        String[] sources = {
            "const id = <T>(x: T): T => x; globalThis.result = id(1)",
            "const id = <T,>(x: T) => x; globalThis.result = id(1)"
        };
        for (String source : sources) {
            String out = NekoJsxCompiler.compileTsx(Path.of("generic-arrow.tsx"), source).code();
            try (CompilerExecutionAssertions.Evaluation evaluation = CompilerExecutionAssertions.eval(out + "\nglobalThis.result")) {
                assertEquals(1, evaluation.value().asInt(), "generic arrow must compile as TypeScript, not JSX: " + out);
            }
        }
    }

    @Test
    void expressionChildRegexCharacterClassesDoNotCloseJsxBrace() {
        String[] sources = {
            "globalThis.result = <div>{/[}]/.test('}')}</div>",
            "globalThis.result = <div>{/}/.test('}')}</div>"
        };
        for (String source : sources) {
            String out = compile(source);
            assertTrue(out.contains("/"),
                "regex expression child must retain its closing brace in compiled output: " + out);
            assertTrue(out.contains(".test("),
                "regex .test() call must survive JSX lowering: " + out);
        }
    }

    @Test
    void expressionChildRegexAfterControlParenDoesNotCloseJsxBrace() {
        String source = "globalThis.result = <div>{(() => { if (true) /}/.test('}'); return true; })()}</div>";
        String out = compile(source);
        assertTrue(out.contains("/}/"),
            "regex following control-condition parenthesis must retain its closing brace in compiled output: " + out);
    }

    @Test
    void expressionChildRegexAfterControlKeywordCommentDoesNotCloseJsxBrace() {
        String source = "globalThis.result = <div>{(() => { if /*comment*/ (true) /}/.test('}'); return true; })()}</div>";
        String out = compile(source);
        assertTrue(out.contains("/}/"),
            "regex following commented control keyword must retain its closing brace in compiled output: " + out);
    }

    @Test
    void expressionChildDivisionAndNestedJsxClosingRemainIntact() {
        String division = compile("globalThis.result = <div>{10 / 2}</div>");
        String nested = compile("globalThis.result = <div>{<span>{10 / 2}</span>}</div>");
        String runtime = "globalThis.__nekoJsxFactory = (type, props, child) => child;\n";

        try (CompilerExecutionAssertions.Evaluation evaluation =
                 CompilerExecutionAssertions.eval(runtime + division + "\nglobalThis.result")) {
            assertEquals(5, evaluation.value().asInt(),
                "division must not be classified as a regex: " + division);
        }
        assertTrue(nested.contains("__nekoJsxFactory('span'"),
            "nested JSX must still lower and consume its closing tag: " + nested);
    }

    @Test
    void automaticRuntimeImportLeavesGeneratedLineZeroUnmappedAndShiftsOriginalMappings() {
        String automaticMap = NekoJsxCompiler.compileJsx(Path.of("automatic.jsx"), "const el = <div/>", true).sourceMap();
        String classicMap = NekoJsxCompiler.compileJsx(Path.of("classic.jsx"), "const el = <div/>").sourceMap();

        String[] automaticLines = JsonParser.parseString(automaticMap).getAsJsonObject()
            .get("mappings").getAsString().split(";", -1);
        assertEquals("", automaticLines[0],
            "runtime import line must have no original-source mapping: " + automaticMap);
        assertFalse(automaticLines[1].isEmpty(),
            "first original-source mapping must start after the injected import: " + automaticMap);

        String[] classicLines = JsonParser.parseString(classicMap).getAsJsonObject()
            .get("mappings").getAsString().split(";", -1);
        assertFalse(classicLines[0].isEmpty(),
            "classic output must keep its first original-source mapping on generated line zero: " + classicMap);
    }

    @Test
    void automaticRuntimeImportsOnceForNestedTransformAndNotForNonJsxSource() {
        String nested = NekoJsxCompiler.compileJsx(
            Path.of("nested.jsx"), "const el = <div>{<span/>}</div>", true).code();
        assertEquals(1, nested.split("from 'nekojs/jsx-runtime'", -1).length - 1,
            "nested JSX lowering must emit only the outer runtime import: " + nested);

        String nonJsx = NekoJsxCompiler.compileJsx(Path.of("plain.jsx"), "const value = 1;", true).code();
        assertFalse(nonJsx.contains("nekojs/jsx-runtime"),
            "automatic runtime must not inject an import when no JSX was lowered: " + nonJsx);
    }

    @Test
    void automaticRuntimeEmitsJsxCallAndImport() {
        // Automatic runtime: emits jsx(type, props, key) calls and injects import { jsx, Fragment } at the top of the file
        String src = "const el = <div class=\"x\">hi</div>";
        String out = NekoJsxCompiler.compileJsx(Path.of("test.jsx"), src, true).code();
        assertTrue(out.contains("import { jsx"), "自动 runtime 须注入 jsx import: " + out);
        assertTrue(out.contains("from 'nekojs/jsx-runtime'"), "须从 nekojs/jsx-runtime 导入: " + out);
        assertTrue(out.contains("jsx("), "须用 jsx() 调用而非 __nekoJsxFactory: " + out);
        assertFalse(out.contains("__nekoJsxFactory"), "自动 runtime 不应再用 classic factory: " + out);
    }

    @Test
    void automaticRuntimeFragmentUsesFragmentImport() {
        // Under the automatic runtime Fragment is the type value: jsx(Fragment, { children: ... })
        String out = NekoJsxCompiler.compileJsx(Path.of("test.jsx"), "const f = <>x</>", true).code();
        assertTrue(out.contains("import {"), "须有 import: " + out);
        assertTrue(out.contains("Fragment"), "Fragment 须在 import 中: " + out);
        assertTrue(out.contains("jsx(Fragment,"), "fragment 须把 Fragment 作为 type 传给 jsx: " + out);
        assertFalse(out.contains("Fragment("), "Fragment 不能当函数调用: " + out);
    }

    @Test
    void classicRuntimeRemainsDefault() {
        // Without automatic (false by default) it stays the classic __nekoJsxFactory
        String out = compile("const el = <div/>");
        assertTrue(out.contains("__nekoJsxFactory("), "默认仍是 classic runtime: " + out);
        assertFalse(out.contains("import { jsx"), "默认不应注入 jsx import: " + out);
    }

    @Test
    void namespaceTagExecutesWithStringType() {
        String out = compile("globalThis.result = <svg:rect/>");
        String runtime = "globalThis.__nekoJsxFactory = (type, props) => ({ type, props });\n";

        try (CompilerExecutionAssertions.Evaluation evaluation =
                 CompilerExecutionAssertions.eval(runtime + out + "\nglobalThis.result")) {
            assertEquals("svg:rect", evaluation.value().getMember("type").asString(),
                "namespace JSX tag must reach the factory as a string: " + out);
        }
    }

    @Test
    void automaticRuntimeSingleChildUsesPropsChildren() {
        assertAutomaticTree(
            "globalThis.result = <div>one</div>",
            "{\"kind\":\"jsx\",\"type\":\"div\",\"children\":\"one\"}"
        );
    }

    @Test
    void automaticRuntimeMultipleChildrenUsesJsxsAndPropsChildrenArray() {
        assertAutomaticTree(
            "globalThis.result = <div><span/>two</div>",
            "{\"kind\":\"jsxs\",\"type\":\"div\",\"children\":[{\"kind\":\"jsx\",\"type\":\"span\",\"children\":null},\"two\"]}"
        );
    }

    @Test
    void automaticRuntimeFragmentUsesFragmentTypeAndPropsChildren() {
        assertAutomaticTree(
            "globalThis.result = <><span/>two</>",
            "{\"kind\":\"jsxs\",\"type\":\"Fragment\",\"children\":[{\"kind\":\"jsx\",\"type\":\"span\",\"children\":null},\"two\"]}"
        );
    }

    private static void assertAutomaticTree(String source, String expectedJson) {
        String out = NekoJsxCompiler.compileJsx(Path.of("test.jsx"), source, true).code();
        String runtime = """
            const Fragment = Symbol('Fragment');
            const node = (kind, type, props) => ({
              kind,
              type: type === Fragment ? 'Fragment' : type,
              children: props?.children ?? null
            });
            const jsx = (type, props) => node('jsx', type, props);
            const jsxs = (type, props) => node('jsxs', type, props);
            """;

        try (CompilerExecutionAssertions.Evaluation evaluation =
                 CompilerExecutionAssertions.evalAutomatic(out + "\nJSON.stringify(globalThis.result)", runtime)) {
            assertEquals(expectedJson, evaluation.value().asString(), "automatic runtime tree: " + out);
        }
    }
}
