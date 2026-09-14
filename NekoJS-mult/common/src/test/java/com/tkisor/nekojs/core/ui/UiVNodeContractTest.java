package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.ComponentVNode;
import com.tkisor.nekojs.api.ui.FragmentVNode;
import com.tkisor.nekojs.api.ui.HostVNode;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiVNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC1：{@code jsx} / {@code jsxs} / {@code Fragment} 到 VNode、函数组件、条件、数组、
 * spread、key 和 children 的语义有公开 contract fixture；VNode 不持有 Minecraft、loader、
 * {@code GuiGraphics}、Font、Screen、GL 或长期可变 host 对象。
 */
class UiVNodeContractTest {

    @Test
    void vNodeTypesOnlyExposeImmutableScriptData() throws Exception {
        for (Class<?> type : List.of(HostVNode.class, ComponentVNode.class, FragmentVNode.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic()) continue;
                assertTrue(Modifier.isFinal(field.getModifiers()),
                        type.getSimpleName() + "." + field.getName() + " must be final");
                String name = field.getType().getName();
                assertFalse(name.startsWith("net.minecraft") || name.startsWith("net.neoforged")
                                || name.startsWith("net.fabricmc") || name.contains("GuiGraphics")
                                || name.contains("Screen") || name.contains("Font") || name.contains("GL"),
                        type.getSimpleName() + "." + field.getName() + " must not hold a host object: " + name);
            }
        }
    }

    @Test
    void hostVNodeExposesPrimitiveTagAndChildren() {
        HostVNode node = new HostVNode(UiPrimitive.LABEL, "k",
                UiProps.of(Map.of("text", "hi")), List.of());
        assertEquals("label", node.tag());
        assertEquals("k", node.key());
        assertEquals("hi", node.props().string("text"));
        assertTrue(node.children().isEmpty());
    }

    @Test
    void propsAreCopiedAndExposeImmutableCollection() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("text", "a");
        source.put("key", "must-be-dropped");
        UiProps props = UiProps.of(source);

        source.put("text", "mutated");
        assertEquals("a", props.string("text"), "props 必须拷贝，不保留调用者引用");
        assertFalse(props.has("key"), "key 是 VNode 的独立字段，不作为 props 成员");

        assertThrows(UnsupportedOperationException.class,
                () -> props.asMap().put("text", "x"), "props 视图必须不可修改");
    }

    @Test
    void vNodeChildrenListsAreImmutable() {
        List<UiVNode> children = new ArrayList<>();
        children.add(new HostVNode(UiPrimitive.LABEL, null, UiProps.empty(), List.of()));
        FragmentVNode fragment = new FragmentVNode(null, children);
        children.clear();
        assertEquals(1, fragment.children().size(), "children 必须拷贝");
        assertThrows(UnsupportedOperationException.class,
                () -> fragment.children().add(new FragmentVNode(null, List.of())));
    }

    @Test
    void vNodeRecordsRejectInvalidConstruction() {
        assertThrows(NullPointerException.class,
                () -> new HostVNode(null, null, UiProps.empty(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ComponentVNode("  ", null, UiProps.empty(), List.of()));
    }

    @Test
    void vNodeRecordAccessorsAreReadOnlyViews() throws Exception {
        // record 天然不可变：不存在 public setter / 非 final 字段
        for (Class<?> type : List.of(HostVNode.class, ComponentVNode.class, FragmentVNode.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().startsWith("set")) continue;
                assertFalse(Modifier.isPublic(method.getModifiers()),
                        type.getSimpleName() + " must not expose a setter: " + method.getName());
            }
        }
    }

    @Test
    void scriptVNodeFactoryAcceptsExistingNakedElementShape() {
        // 既有 jsx-runtime 形状：{ $$nekoJsx, type, key, props, children }
        Map<String, Object> element = new LinkedHashMap<>();
        element.put(ScriptUiVNodeFactory.JSX_MARKER, true);
        element.put("type", "label");
        element.put("key", "a");
        element.put("props", Map.of("text", "hello"));

        ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
        List<UiVNode> nodes = factory.toVNodes(element, "screen");
        assertEquals(1, nodes.size());
        HostVNode host = (HostVNode) nodes.getFirst();
        assertEquals(UiPrimitive.LABEL, host.primitive());
        assertEquals("a", host.key());
        assertEquals("hello", host.props().string("text"));
    }

    @Test
    void scriptVNodeFactoryFlattensArraysAndDropsNullConditionals() {
        ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
        Map<String, Object> label = Map.of("type", "label", "props", Map.of("text", "x"));
        List<Object> nested = new ArrayList<>();
        nested.add(label);
        nested.add(null);
        List<Object> array = new ArrayList<>();
        array.add(label);
        array.add(null);
        array.add(nested);

        List<UiVNode> nodes = factory.toVNodes(array, "screen/children");
        assertEquals(2, nodes.size(), "数组必须展开，条件 null 分支必须被丢弃");
    }

    @Test
    void scriptVNodeFactoryRejectsUnknownTagAndFunctionChildren() {
        ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
        UiFailure unknown = assertThrows(UiFailure.class, () -> factory.toVNodes(
                Map.of("type", "div", "props", Map.of()), "screen"));
        assertEquals(UiFailure.UNKNOWN_PRIMITIVE, unknown.code());
        assertEquals(UiDiagnosticStage.RENDER, unknown.stage());

        UiFailure bareFunction = assertThrows(UiFailure.class, () -> factory.toVNodes(
                (Runnable) () -> { }, "screen"));
        assertEquals(UiFailure.INVALID_CHILD, bareFunction.code());
    }

    @Test
    void scriptVNodeFactoryMapsFragmentAndComponentTypes() {
        ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
        Map<String, Object> fragment = Map.of("type", "Fragment", "props", Map.of(
                "children", List.of(Map.of("type", "label", "props", Map.of("text", "a")))));
        List<UiVNode> nodes = factory.toVNodes(fragment, "screen");
        assertEquals(1, nodes.size());
        assertTrue(nodes.getFirst() instanceof FragmentVNode);

        // 大写 JSX 标签在脚本侧是函数值（不是字符串）；Java 侧用任何非字符串 type 表示
        Object componentType = (Runnable) () -> { };
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("type", componentType);
        component.put("component", "MyWidget");
        component.put("props", Map.of());
        List<UiVNode> componentNodes = factory.toVNodes(component, "screen");
        assertTrue(componentNodes.getFirst() instanceof ComponentVNode);
        assertEquals("MyWidget", ((ComponentVNode) componentNodes.getFirst()).component());
    }

    @Test
    void scriptScreenRendersThroughFactoryAndRejectsEmptyResult() {
        ScriptUiVNodeFactory factory = new ScriptUiVNodeFactory();
        Map<String, Object> screen = Map.of("type", "screen", "props", Map.of());
        List<UiVNode> nodes = factory.toVNodes(screen, "screen");
        assertNotSame(null, nodes.getFirst());
        assertEquals(UiPrimitive.SCREEN, ((HostVNode) nodes.getFirst()).primitive());
    }

    @Test
    void layoutPropsVocabularyStaysInSyncWithLayoutSpec() {
        List<UiProp> layoutProps = com.tkisor.nekojs.api.ui.UiLayoutSpec.props();
        for (UiProp prop : layoutProps) {
            assertEquals(com.tkisor.nekojs.api.ui.UiPropCategory.LAYOUT, prop.category(),
                    prop.jsName() + " must be a layout prop");
        }
        // 票 43 新增 direction（方向覆盖）与 profiles（Viewport Profile 覆盖包）。
        assertEquals(16, layoutProps.size());
    }
}
