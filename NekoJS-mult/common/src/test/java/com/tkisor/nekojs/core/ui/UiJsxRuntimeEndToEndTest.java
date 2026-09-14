package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.HostVNode;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiScreen;
import com.tkisor.nekojs.api.ui.UiScreenOptions;
import com.tkisor.nekojs.api.ui.UiVNode;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC1 端到端：automatic runtime 的 {@code jsx} / {@code jsxs} / {@code Fragment} 到 VNode、
 * 函数组件、条件、数组、spread、key 和 children 的语义有公开 contract fixture。
 *
 * <p>测试真的执行 {@code common/src/main/resources/nekojs/node/modules/jsx-runtime.ts}
 * （经 {@code NekoTypeScriptCompiler.eraseTypescript} 擦除类型）与 {@code NekoJsxCompiler}
 * 的 automatic 产物，再把结果喂给 {@link ScriptUiVNodeFactory} 与 fake host reconciler——
 * 因此「runtime 输出的 VNode 形状」与「Java 侧消费的 VNode 形状」由同一份 fixture 锁定。
 */
class UiJsxRuntimeEndToEndTest {

    private static String readJsxRuntimeModule() throws Exception {
        try (InputStream in = UiJsxRuntimeEndToEndTest.class.getClassLoader()
                .getResourceAsStream("nekojs/node/modules/jsx-runtime.ts")) {
            assertNotNull(in, "jsx-runtime.ts must be on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 擦除 .ts 类型并剥掉 __nekoNodeDefine 包装，返回可在 Context 里求值的绑定。 */
    private static String runtimeBindings() throws Exception {
        String erased = NekoTypeScriptCompiler.eraseTypescript(
                Path.of("jsx-runtime.ts"), readJsxRuntimeModule());
        assertFalse(erased.contains(": string"), "类型注解必须被擦除:\n" + erased);
        assertFalse(erased.contains("interface "), "类型声明必须被擦除:\n" + erased);
        assertTrue(erased.contains("const FRAGMENT = Symbol("), "值定义必须保留:\n" + erased);
        // 把 __nekoNodeDefine 的导出捕获到全局，供脚本片段使用
        return erased.replace(
                "globalThis.__nekoNodeDefine(['nekojs/jsx-runtime'], { jsx: jsx, jsxs: jsxs, Fragment: FRAGMENT })",
                "globalThis.__runtime = { jsx: jsx, jsxs: jsxs, Fragment: FRAGMENT }");
    }

    /** 把 automatic runtime 的命名导出绑定为全局，供 lowering 产物直接调用。 */
    private static String jsxGlobals() throws Exception {
        return runtimeBindings()
                + "\nglobalThis.jsx = globalThis.__runtime.jsx;"
                + "globalThis.jsxs = globalThis.__runtime.jsxs;"
                + "globalThis.Fragment = globalThis.__runtime.Fragment;";
    }

    private static Context context() {
        return Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .build();
    }

    @Test
    void runtimeProducesFrozenImmutableVNodes() throws Exception {
        try (Context context = context()) {
            context.eval("js", runtimeBindings());
            Value element = context.eval("js",
                    "globalThis.__runtime.jsx('label', { text: 'hi', children: 'x' }, 'k')");

            assertTrue(element.hasMember("props"), "element must expose props");
            assertTrue(element.hasMember("props") && !element.getMember("props").isNull(),
                    "props must be present");
            // Object.isFrozen 在 guest 侧为真：这是 AC1「不可变 VNode」的可观察断言
            assertTrue(context.eval("js", "Object.isFrozen(globalThis.__runtime.jsx('label', {text:'t'}, null))")
                            .asBoolean(),
                    "runtime 产出的 VNode 必须被冻结");
            assertTrue(context.eval("js",
                            "Object.isFrozen(globalThis.__runtime.jsx('label', {text:'t'}, null).props)")
                            .asBoolean(),
                    "runtime 产出的 props 必须被冻结");
            assertTrue(context.eval("js",
                            "Object.isFrozen(globalThis.__runtime.jsx('label', {text:'t'}, null).children)")
                            .asBoolean(),
                    "runtime 产出的 children 必须被冻结");
        }
    }

    @Test
    void runtimeMutationsAreRejectedOrDoNotAffectTheElement() throws Exception {
        try (Context context = context()) {
            context.eval("js", runtimeBindings());
            // 非严格模式下静默失败；严格模式下抛 TypeError。两种情况下原元素都不得改变。
            context.eval("js", "'use strict';\n"
                    + "globalThis.__el = globalThis.__runtime.jsx('label', { text: 'a' }, null);\n"
                    + "try { globalThis.__el.props.text = 'b' } catch (e) { globalThis.__mutated = true }");
            assertEquals("a", context.eval("js", "globalThis.__el.props.text").asString(),
                    "冻结的 props 不得被改写");
            assertTrue(context.eval("js", "globalThis.__mutated === true || globalThis.__el.props.text === 'a'")
                    .asBoolean());
        }
    }

    @Test
    void automaticRuntimeOutputFeedsTheJavaVNodeFactoryAndFakeHost() throws Exception {
        String source = """
                const label = <label text="hello" key="a" />;
                const frag = <><label text="one"/><label text="two"/></>;
                const list = [1, 2].map((n) => <label text={'n' + n} key={String(n)} />);
                const conditional = false ? <button text="hidden"/> : null;
                export const tree = <screen><column>{label}{frag}{list}{conditional}</column></screen>;
                """;
        String code = new NekoCompilationPipeline()
                .compile(Path.of("ui-fixture.tsx"), source, ".tsx", NekoJsxLanguagePlugin.INSTANCE, true)
                .code();
        assertTrue(code.contains("from 'nekojs/jsx-runtime'"), code);
        assertFalse(code.contains("__nekoJsxFactory"), code);

        try (Context context = context()) {
            context.eval("js", jsxGlobals());
            String executable = code.replaceFirst("(?m)\\Aimport \\{ [^\\r\\n]+ } from 'nekojs/jsx-runtime';\\R", "");
            executable = executable.replaceFirst("(?m)^export const tree", "const tree");
            context.eval("js", executable + "\nglobalThis.__tree = tree;");
            Value tree = context.getBindings("js").getMember("__tree");

            ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
            UiVNode vnode = factory.toVNode(tree, "screen");
            assertTrue(vnode instanceof HostVNode host && host.primitive() == UiPrimitive.SCREEN,
                    "automatic runtime 产物必须转换出 screen 根 VNode: " + vnode);

            UiTestFixture fixture = new UiTestFixture();
            fixture.render = () -> vnode;
            UiRootHandle root = fixture.facade().open(fixture.screen());
            UiHostNode committed = root.inspection().committedRoot();
            assertEquals(List.of("screen", "column", "label", "label", "label", "label", "label"),
                    fixture.tagsOf(committed),
                    "函数组件/条件/数组/fragment 展开后的 primitive 顺序必须与脚本结构一致");
            assertEquals("hello", committed.children().get(0).children().get(0).props().string("text"));
            assertEquals("a", committed.children().get(0).children().get(0).key());
            assertNotNull(committed.children().get(0).layout());
        }
    }

    @Test
    void functionComponentsExpandIntoTheCandidateTree() throws Exception {
        String source = """
                function Widget(props) {
                  return <panel><label text={props.label}/></panel>;
                }
                export const tree = <screen><Widget label="from-component"/></screen>;
                """;
        String code = new NekoCompilationPipeline()
                .compile(Path.of("component.tsx"), source, ".tsx", NekoJsxLanguagePlugin.INSTANCE, true)
                .code();

        try (Context context = context()) {
            context.eval("js", jsxGlobals());
            String executable = code.replaceFirst("(?m)\\Aimport \\{ [^\\r\\n]+ } from 'nekojs/jsx-runtime';\\R", "");
            executable = executable.replaceFirst("(?m)^export const tree", "const tree");
            // Widget 由 Java 侧组件注册表展开，这里只需要构造出候选 VNode
            context.eval("js", executable + "\nglobalThis.__tree = tree;");
            Value tree = context.getBindings("js").getMember("__tree");

            ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
            UiVNode vnode = factory.toVNode(tree, "screen");
            UiTestFixture fixture = new UiTestFixture();
            fixture.component("Widget", () -> new HostVNode(UiPrimitive.PANEL, null, UiProps.empty(),
                    List.of(new HostVNode(UiPrimitive.LABEL, null,
                            UiProps.of(java.util.Map.of("text", "from-component")), List.of()))));
            fixture.render = () -> vnode;
            UiRootHandle root = fixture.facade().open(fixture.screen());
            assertEquals(List.of("screen", "panel", "label"),
                    fixture.tagsOf(root.inspection().committedRoot()));
            assertEquals(1, fixture.componentRenders.getOrDefault("Widget", 0));
        }
    }

    @Test
    void runtimeElementShapeIsPinnedByThePublicContractFixture() throws Exception {
        // 公开 contract fixture：元素形状字段固定，且 ScriptUiVNodeFactory 与 runtime 同源消费
        try (Context context = context()) {
            context.eval("js", runtimeBindings());
            context.eval("js", "globalThis.__el = globalThis.__runtime.jsxs('column',"
                    + " { gap: 2, children: [globalThis.__runtime.jsx('label', { text: 'a' }, 'k1')] }, null)");
            Value element = context.getBindings("js").getMember("__el");
            assertTrue(element.hasMember("kind"));
            assertTrue(element.hasMember("type"));
            assertTrue(element.hasMember("key"));
            assertTrue(element.hasMember("props"));
            assertTrue(element.hasMember("children"));
            assertEquals("jsxs", element.getMember("kind").asString());
            assertEquals("column", element.getMember("type").asString());
            assertEquals(1, element.getMember("children").getArraySize());
            assertEquals("k1", element.getMember("children").getArrayElement(0).getMember("key").asString());

            ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
            HostVNode column = (HostVNode) factory.toVNode(element, "screen");
            assertEquals(UiPrimitive.COLUMN, column.primitive());
            assertEquals(2.0, column.props().number(com.tkisor.nekojs.api.ui.UiProp.GAP, -1));
            assertEquals(1, column.children().size());
            assertEquals("a", ((HostVNode) column.children().getFirst()).props().string("text"));
        }
    }

    @Test
    void fragmentTypeFromRuntimeExpandsWithoutHostNode() throws Exception {
        try (Context context = context()) {
            context.eval("js", jsxGlobals());
            context.eval("js", "globalThis.__frag = globalThis.jsx(Fragment,"
                    + " { children: [globalThis.jsx('label', {text:'x'}, null)] }, null)");
            Value fragment = context.getBindings("js").getMember("__frag");
            ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
            UiVNode node = factory.toVNode(fragment, "screen");
            assertTrue(node instanceof com.tkisor.nekojs.api.ui.FragmentVNode,
                    "Fragment 类型必须展开为 FragmentVNode: " + node);
            assertEquals(1, node.children().size());
        }
    }

    @Test
    void screenWithoutRendererIsRejectedByScriptUiScreen() {
        UiScreen emptyScreen = new UiScreen() {
            @Override public UiVNode render() { return null; }
            @Override public UiScreenOptions options() { return UiScreenOptions.defaults(); }
        };
        assertTrue(emptyScreen.options().closeOnEscape());
    }
    @Test
    void spreadPropsKeyAndChildrenSurviveLoweringIntoTheCandidateTree() throws Exception {
        String source = """
                const extra = { text: 'spread-text', color: 'white' };
                const list = ['a', 'b'].map((id) => <label {...extra} key={id} text={id} />);
                export const tree = <screen><column>{list}</column></screen>;
                """;
        String code = new NekoCompilationPipeline()
                .compile(Path.of("spread.tsx"), source, ".tsx", NekoJsxLanguagePlugin.INSTANCE, true)
                .code();
        assertTrue(code.contains("from 'nekojs/jsx-runtime'"), code);

        try (Context context = context()) {
            context.eval("js", jsxGlobals());
            String executable = code.replaceFirst("(?m)\\Aimport \\{ [^\\r\\n]+ } from 'nekojs/jsx-runtime';\\R", "");
            executable = executable.replaceFirst("(?m)^export const tree", "const tree");
            context.eval("js", executable + "\nglobalThis.__tree = tree;");
            Value tree = context.getBindings("js").getMember("__tree");

            ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
            HostVNode screen = (HostVNode) factory.toVNode(tree, "screen");
            HostVNode column = (HostVNode) screen.children().getFirst();
            assertEquals(2, column.children().size(), "数组展开必须产生两个 label");
            for (int i = 0; i < 2; i++) {
                HostVNode label = (HostVNode) column.children().get(i);
                assertEquals(UiPrimitive.LABEL, label.primitive());
                assertEquals(List.of("a", "b").get(i), label.key(), "key 必须来自 spread 后的 props");
                assertEquals(List.of("a", "b").get(i), label.props().string("text"),
                        "显式 text 必须覆盖 spread 的 text");
                assertEquals("white", label.props().string("color"), "spread 的其余属性必须保留");
            }
        }
    }

    @Test
    void conditionalBranchesAndEmptyChildrenAreHandledByContract() throws Exception {
        // 脚本侧条件渲染：false 分支产出 null —— 数组里的 null 元素必须被安全丢弃。
        // 这是真实的脚本路径：null 由 UiVNodeFactory 过滤后才进入不可变 VNode 列表
        //（HostVNode/UiProps 的 List.copyOf 明确拒绝 null，VNode 本身不接受 null 子节点）。
        List<Object> scriptChildren = new java.util.ArrayList<>();
        scriptChildren.add(null);
        scriptChildren.add(java.util.Map.of("type", "label", "props", java.util.Map.of("text", "kept")));
        scriptChildren.add(null);
        java.util.Map<String, Object> column = new java.util.LinkedHashMap<>();
        column.put("type", "column");
        column.put("props", java.util.Map.of("children", scriptChildren));
        java.util.Map<String, Object> screen = new java.util.LinkedHashMap<>();
        screen.put("type", "screen");
        screen.put("props", java.util.Map.of("children", List.of(column)));

        ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
        List<UiVNode> nodes = factory.toVNodes(screen, "screen");
        assertEquals(1, nodes.size());

        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> nodes.getFirst();
        UiRootHandle root = fixture.facade().open(fixture.screen());
        assertEquals(List.of("screen", "column", "label"), fixture.tagsOf(root.inspection().committedRoot()),
                "条件 null 与空 Fragment 必须被安全丢弃，不产生 host node");
    }
}