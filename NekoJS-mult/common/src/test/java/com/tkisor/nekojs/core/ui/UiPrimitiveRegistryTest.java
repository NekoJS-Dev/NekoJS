package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC2：初始 primitive 集合有唯一登记表；未知 primitive、非法 props、非法布局值和
 * 重复 key 显式失败。
 */
class UiPrimitiveRegistryTest {

    @Test
    void primitiveTableContainsExactlyTheElevenRegisteredTags() {
        assertEquals(List.of("screen", "panel", "row", "column", "stack", "scroll",
                        "label", "button", "input", "image", "spacer"),
                UiPrimitiveRegistry.tags(),
                "initial primitive set must be exactly the registered eleven tags");
    }

    @Test
    void everyRegisteredPrimitiveHasANonEmptyAllowedPropSet() {
        Map<String, List<String>> snapshot = UiPrimitiveRegistry.snapshot();
        assertEquals(11, snapshot.size());
        for (Map.Entry<String, List<String>> entry : snapshot.entrySet()) {
            assertFalse(entry.getValue().isEmpty(), entry.getKey() + " must allow at least one prop");
        }
    }

    @Test
    void unknownPrimitiveTagIsRejectedExplicitly() {
        assertTrue(UiPrimitiveRegistry.byTag("div") == null, "unknown tag must not resolve to a primitive");
        UiFailure failure = assertThrows(UiFailure.class,
                () -> UiPrimitiveRegistry.allowedProps(UiPrimitiveRegistry.byTag("div")));
        assertEquals(UiFailure.UNKNOWN_PRIMITIVE, failure.code());
    }

    @Test
    void unknownPropNameIsRejectedExplicitly() {
        UiFailure failure = assertThrows(UiFailure.class, () ->
                UiPrimitiveRegistry.validateProps(UiPrimitive.LABEL,
                        UiProps.of(Map.of("className", "x")), "screen/label[0]"));
        assertEquals(UiFailure.INVALID_PROP, failure.code());
        assertEquals(UiDiagnosticStage.COMPONENT, failure.stage());
    }

    @Test
    void propNotAllowedOnPrimitiveIsRejectedExplicitly() {
        UiFailure failure = assertThrows(UiFailure.class, () ->
                UiPrimitiveRegistry.validateProps(UiPrimitive.SPACER,
                        UiProps.of(Map.of("text", "hello")), "screen/spacer[0]"));
        assertEquals(UiFailure.INVALID_PROP, failure.code());
        assertTrue(failure.getMessage().contains("not allowed"), failure.getMessage());
    }

    @Test
    void wrongPropValueTypeIsRejectedExplicitly() {
        UiFailure failure = assertThrows(UiFailure.class, () ->
                UiPrimitiveRegistry.validateProps(UiPrimitive.BUTTON,
                        UiProps.of(Map.of("disabled", "yes")), "screen/button[0]"));
        assertEquals(UiFailure.INVALID_PROP, failure.code());
    }

    @Test
    void eventValueMustBeAFunctionOrNull() {
        UiFailure failure = assertThrows(UiFailure.class, () ->
                UiPrimitiveRegistry.validateProps(UiPrimitive.BUTTON,
                        UiProps.of(Map.of("onClick", "not-a-function")), "screen/button[0]"));
        assertEquals(UiFailure.INVALID_PROP, failure.code());

        // 函数句柄与显式 null 都是合法事件值
        UiPrimitiveRegistry.validateProps(UiPrimitive.BUTTON,
                UiProps.of(Map.of("onClick", (Runnable) () -> { })), "screen/button[0]");
        Map<String, Object> explicitNull = new java.util.LinkedHashMap<>();
        explicitNull.put("onClick", null);
        UiPrimitiveRegistry.validateProps(UiPrimitive.BUTTON,
                UiProps.of(explicitNull), "screen/button[0]");
    }

    @Test
    void invalidLayoutValuesAreRejectedExplicitly() {
        UiFailure failure = assertThrows(UiFailure.class, () ->
                UiPrimitiveRegistry.validateLayoutValues(
                        UiProps.of(Map.of("align", "diagonal")), "screen/row[0]"));
        assertEquals(UiFailure.INVALID_LAYOUT, failure.code());
        assertEquals(UiDiagnosticStage.LAYOUT, failure.stage());

        UiFailure anchorFailure = assertThrows(UiFailure.class, () ->
                UiPrimitiveRegistry.validateLayoutValues(
                        UiProps.of(Map.of("anchor", "middle-ish")), "screen/row[0]"));
        assertEquals(UiFailure.INVALID_LAYOUT, anchorFailure.code());
    }

    @Test
    void controlledResourceIdRejectsUrlAndAbsolutePaths() {
        UiPrimitiveRegistry.validateResourceId("nekojs:textures/gui/panel.png", "screen/image[0]");
        for (String bad : List.of("https://example.com/a.png", "/etc/passwd", "a:b/../c", "nopath", "ns:http://x")) {
            assertThrows(UiFailure.class,
                    () -> UiPrimitiveRegistry.validateResourceId(bad, "screen/image[0]"),
                    "resource id must reject: " + bad);
        }
    }

    @Test
    void duplicateKeyIsRejectedExplicitly() {
        UiFailure failure = assertThrows(UiFailure.class, () ->
                UiPrimitiveRegistry.validateUniqueKeys("screen/row[0]", List.of(
                        new UiPrimitiveRegistry.KeyedTag("label", "a"),
                        new UiPrimitiveRegistry.KeyedTag("label", "a"))));
        assertEquals(UiFailure.DUPLICATE_KEY, failure.code());
        assertEquals(UiDiagnosticStage.RENDER, failure.stage());
    }

    @Test
    void everyEventPropMapsToAStableEventType() {
        for (UiProp prop : UiProp.values()) {
            if (!prop.isEvent()) continue;
            assertNotNull(com.tkisor.nekojs.api.ui.UiEventType.fromJsName(
                            prop.jsName().substring(2, 3).toLowerCase() + prop.jsName().substring(3)),
                    prop.jsName() + " must map to an event type");
        }
    }
}
