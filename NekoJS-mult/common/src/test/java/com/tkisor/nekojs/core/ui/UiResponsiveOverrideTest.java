package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiAxis;
import com.tkisor.nekojs.api.ui.UiDimension;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiLayoutSpec;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProfileOverride;
import com.tkisor.nekojs.api.ui.UiResponsiveRules;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.ViewportContext;
import com.tkisor.nekojs.api.ui.ViewportProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC2 + AC3 + AC4：基础值与 profile 覆盖值的解析顺序、缺省回退、非法 profile / 非法比例 /
 * min/max 裁剪规则；方向切换、可见性与布局属性可按 profile 覆盖；设计坐标与连续比例共存而
 * 字号不被强制无限等比缩放。
 */
class UiResponsiveOverrideTest {

    // ---- AC2：解析顺序与非法输入 ----

    @Test
    void overrideKeysAreExactlyTheDocumentedControlledSet() {
        assertEquals(List.of("align", "anchor", "direction", "font", "fontSize", "gap", "height",
                "justify", "margin", "maxHeight", "maxWidth", "minHeight", "minWidth", "padding",
                "scrollX", "scrollY", "visible", "width", "wrap"), UiProfileOverride.KEYS);
        assertTrue(UiProfileOverride.NONE.values().isEmpty());
    }

    @Test
    void mappingKeysAcceptNumbersAndStableCodes() {
        Map<ViewportProfile, UiProfileOverride> byNumber =
                UiProfileOverride.parseAll(Map.of(1, Map.of("gap", 12)), null);
        Map<ViewportProfile, UiProfileOverride> byCode =
                UiProfileOverride.parseAll(Map.of("desktop-wide", Map.of("gap", 12)), null);
        assertEquals(byNumber, byCode);
        assertEquals(12.0, byNumber.get(ViewportProfile.DESKTOP_WIDE).gap());
        // 数组按顺序合并：后面的覆盖前面的。
        Map<ViewportProfile, UiProfileOverride> merged = UiProfileOverride.parseAll(
                List.of(Map.of(4, Map.of("gap", 2)), Map.of(4, Map.of("gap", 3))), null);
        assertEquals(3.0, merged.get(ViewportProfile.HANDHELD).gap());
    }

    @Test
    void invalidProfileKeysAndValuesFailExplicitly() {
        UiFailure outOfRange = assertThrows(UiFailure.class, () ->
                UiProfileOverride.parseAll(Map.of(7, Map.of("gap", 1)), "screen/row[0]"));
        assertEquals(UiFailure.INVALID_LAYOUT, outOfRange.code());
        assertEquals("screen/row[0]", outOfRange.nodePath());
        assertThrows(UiFailure.class, () -> UiProfileOverride.parseAll(Map.of(0, Map.of()), null));
        assertThrows(UiFailure.class, () -> UiProfileOverride.parseAll(Map.of("huge", Map.of()), null));
        assertThrows(UiFailure.class, () -> UiProfileOverride.parseAll(Map.of(1, Map.of("nope", 1)), null));
        assertThrows(UiFailure.class, () -> UiProfileOverride.parseAll(Map.of(1, Map.of("visible", "maybe")), null));
        assertThrows(UiFailure.class, () -> UiProfileOverride.parseAll(Map.of(1, Map.of("direction", "diagonal")), null));
        assertThrows(UiFailure.class, () -> UiProfileOverride.parseAll(Map.of(1, Map.of("gap", -1)), null));
        assertThrows(UiFailure.class, () -> UiProfileOverride.parseAll(Map.of(1, "nope"), null));
        assertThrows(UiFailure.class, () -> UiProfileOverride.parseAll("nope", null));
        // min > max 的覆盖本身合法，冲突在裁剪阶段按固定规则解决（见 minMaxClampingIsFixed）。
    }

    @Test
    void defaultFallbackCascadesFromTheNearestDeclaredBreakpointDownward() {
        // 一条声明作用于它自己以及所有更窄的档位：断点是随窗口变窄依次命中的。
        Map<ViewportProfile, UiProfileOverride> declared = new LinkedHashMap<>();
        declared.put(ViewportProfile.DESKTOP, override(Map.of("gap", 10)));
        declared.put(ViewportProfile.MINIMAL, override(Map.of("gap", 1)));

        assertEquals(10.0, UiProfileOverride.forProfile(ViewportProfile.DESKTOP, declared).gap(),
                "本档声明优先");
        assertEquals(10.0, UiProfileOverride.forProfile(ViewportProfile.HANDHELD, declared).gap(),
                "命中更窄的档时沿用最近一次声明");
        assertEquals(1.0, UiProfileOverride.forProfile(ViewportProfile.MINIMAL, declared).gap(),
                "更窄的声明覆盖更宽的声明");
        assertEquals(UiProfileOverride.NONE,
                UiProfileOverride.forProfile(ViewportProfile.DESKTOP_WIDE, declared),
                "比所有声明都宽的档位回到基础值");

        Map<ViewportProfile, UiProfileOverride> widest = new LinkedHashMap<>();
        widest.put(ViewportProfile.DESKTOP_WIDE, override(Map.of("gap", 7)));
        assertEquals(7.0, UiProfileOverride.forProfile(ViewportProfile.MINIMAL, widest).gap(),
                "1 档的声明作用于全部六档（最宽的断点覆盖一切）");
        assertEquals(UiProfileOverride.NONE, UiProfileOverride.forProfile(ViewportProfile.TABLET, Map.of()),
                "没有任何声明时是基础值（空覆盖）");
    }

    @Test
    void resolutionOrderIsBaseThenOverrideThenValidation() {
        // 基础值 gap=4 / padding=1；2 档覆盖 gap=12；当前视口落在 2 档。
        var props = UiTestFixture.props("gap", 4.0, "padding", 1.0,
                "profiles", Map.of(2, Map.of("gap", 12.0)));
        UiLayoutSpec spec = UiLayoutSpec.fromProps(props, ViewportContext.of(500, 320));
        assertEquals(12.0, spec.gap(), 0.0001, "覆盖值替换基础值");
        assertEquals(1.0, spec.padding(), 0.0001, "未被覆盖的基础值原样保留");

        // 更窄的档命中更宽的声明：2 档的声明继续服务于 3–6 档。
        UiLayoutSpec narrower = UiLayoutSpec.fromProps(props, ViewportContext.of(200, 150));
        assertEquals(12.0, narrower.gap(), 0.0001, "窄档沿用最近一次断点声明");

        // 比所有声明都宽的档位回到基础值。
        UiLayoutSpec wider = UiLayoutSpec.fromProps(props, ViewportContext.of(1000, 600));
        assertEquals(4.0, wider.gap(), 0.0001, "更宽于所有断点时使用基础值");

        UiLayoutSpec noOverride = UiLayoutSpec.fromProps(UiTestFixture.props("gap", 4.0),
                ViewportContext.of(1000, 600));
        assertEquals(4.0, noOverride.gap(), 0.0001, "没有任何 profile 声明时使用基础值");
    }

    @Test
    void minMaxClampingIsFixedForBothAxes() {
        // 覆盖值自身 min > max 是非法组合，解析阶段显式失败（不静默交换）；
        // 经 props 校验链时统一表现为 INVALID_LAYOUT 的 UiFailure。
        assertThrows(UiFailure.class, () -> UiPrimitiveRegistry.validateProps(UiPrimitive.ROW,
                UiTestFixture.props("profiles", Map.of(2, Map.of("minWidth", 80, "maxWidth", 20))),
                "screen/row[0]"));
        // 经 registry 校验入口统一为契约错误类型 INVALID_LAYOUT；不经校验入口时是 UiLayoutSpec
        // 自身的取值校验异常（同一非法输入的两种调用路径，语义一致）。
        UiFailure direct = assertThrows(UiFailure.class, () -> UiPrimitiveRegistry.validateProps(
                UiPrimitive.ROW,
                UiTestFixture.props("profiles", Map.of(2, Map.of("minWidth", 80, "maxWidth", 20))),
                "screen/row[0]"));
        assertEquals(UiFailure.INVALID_LAYOUT, direct.code());
        assertThrows(IllegalArgumentException.class, () -> UiLayoutSpec.fromProps(
                UiTestFixture.props("profiles", Map.of(2, Map.of("minWidth", 80, "maxWidth", 20))),
                ViewportContext.of(500, 320)));
        UiLayoutSpec spec = UiLayoutSpec.fromProps(UiTestFixture.props(
                "width", 40, "minWidth", 60, "maxWidth", 100), ViewportContext.of(500, 320));
        assertEquals(60.0, spec.minWidth());
        assertEquals(100.0, spec.maxWidth());

        // 覆盖里的 min/max 与基础 min/max 冲突时，覆盖整体替换（不做合并），由 UiLayoutSpec 固定裁剪。
        var conflicting = UiTestFixture.props("width", 40, "minWidth", 60,
                "profiles", Map.of(2, Map.of("minWidth", 0.0)));
        assertEquals(0.0, UiLayoutSpec.fromProps(conflicting, ViewportContext.of(500, 320)).minWidth());
    }

    @Test
    void percentAndMinMaxAreResolvedAndClampedByTheContract() {
        // 520x340 落在 2 档：覆盖 25% 相对容器可用宽（520）解析为 130。
        UiHostNode wide = renderScreen(520, 340, "width", "50%", "height", 40, "profiles",
                Map.of(2, Map.of("width", "25%")));
        assertEquals(130.0, wide.children().get(0).layout().width(), 0.0001,
                "2 档覆盖 25% 相对可用宽 520 → 130");

        // 300x200 落在 4 档：4 档无声明，回退命中 2 档 → 25% of 300 = 75。
        UiHostNode narrow = renderScreen(300, 200, "width", "50%", "height", 40, "profiles",
                Map.of(2, Map.of("width", "25%")));
        assertEquals(75.0, narrow.children().get(0).layout().width(), 0.0001,
                "窄档沿用 2 档的 25% 覆盖（可用宽 300 → 75）");

        // min/max 裁剪：固定 40 被 min 抬起；固定 400 被 max 夹住。
        UiHostNode clamped = renderScreen(520, 340, "width", 40, "height", 20, "profiles",
                Map.of(2, Map.of("minWidth", 60.0, "maxWidth", 100.0)));
        assertEquals(60.0, clamped.children().get(0).layout().width(), 0.0001,
                "40 被 min 60 抬起（再被 max 100 夹住仍是 60）");

        UiHostNode capped = renderScreen(520, 340, "width", 400, "height", 20, "profiles",
                Map.of(2, Map.of("maxWidth", 120.0)));
        assertEquals(120.0, capped.children().get(0).layout().width(), 0.0001,
                "400 被 max 120 夹住");

        // 非法比例：>100% 显式失败（不静默夹到 100）；负数同样显式失败。
        UiFailure badPercent = assertThrows(UiFailure.class, () -> UiPrimitiveRegistry.validateProps(
                UiPrimitive.ROW,
                UiTestFixture.props("profiles", Map.of(2, Map.of("width", "150%"))),
                "screen/row[0]"));
        assertEquals(UiFailure.INVALID_LAYOUT, badPercent.code());
        assertThrows(UiFailure.class, () -> UiPrimitiveRegistry.validateProps(UiPrimitive.ROW,
                UiTestFixture.props("profiles", Map.of(2, Map.of("width", "-5%"))), "screen/row[0]"));
    }

    /** 渲染 screen + 一个 panel 子节点，返回已提交的 screen 节点。 */
    private static UiHostNode renderScreen(double width, double height, Object... panelProps) {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(width, height);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"),
                UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props(panelProps)));
        return fixture.facade().open(fixture.screen()).inspection().committedRoot();
    }

    // ---- AC3：方向、可见性与布局属性按 profile 覆盖 ----

    @Test
    void directionOverrideSwitchesRowAndColumnByProfile() {
        var props = UiTestFixture.props("direction", "row",
                "profiles", Map.of(4, Map.of("direction", "column")));
        assertEquals(UiAxis.ROW, UiLayoutSpec.fromProps(props, ViewportContext.of(500, 320)).direction());
        assertEquals(UiAxis.COLUMN, UiLayoutSpec.fromProps(props, ViewportContext.of(200, 150)).direction());

        // 没有覆盖时为 null：继承 primitive 缺省，是显式语义而不是某个具体方向。
        assertEquals(null, UiLayoutSpec.fromProps(UiTestFixture.props(), ViewportContext.of(500, 320)).direction());
    }

    @Test
    void rowReordersIntoColumnOnNarrowProfileThroughTheRealLayoutEngine() {
        // 宽屏：两个并排 label；窄屏：同一个 <row> 变竖排，X 相同、Y 递增。
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(700, 400);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"),
                UiTestFixture.host(UiPrimitive.ROW, null,
                        UiTestFixture.props("direction", "row",
                                "profiles", Map.of(4, Map.of("direction", "column"))),
                        UiTestFixture.host(UiPrimitive.LABEL, "a", UiTestFixture.props("text", "aa")),
                        UiTestFixture.host(UiPrimitive.LABEL, "b", UiTestFixture.props("text", "bb"))));

        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());
        UiHostNode row = root.inspection().committedRoot().children().get(0);
        assertEquals(row.children().get(0).layout().y(), row.children().get(1).layout().y(), 0.0001,
                "宽屏下两个 label 必须在同一行");

        facade.resize(ViewportContext.of(280, 200));
        row = root.inspection().committedRoot().children().get(0);
        assertEquals(ViewportProfile.HANDHELD, root.viewport().profile());
        assertEquals(row.children().get(0).layout().x(), row.children().get(1).layout().x(), 0.0001,
                "窄屏覆盖 direction=column 后两个 label 必须同列");
        assertTrue(row.children().get(1).layout().y() > row.children().get(0).layout().y(),
                "竖排后第二个 label 在下一行");
    }

    @Test
    void visibilityCanBeOverriddenPerProfile() {
        var props = UiTestFixture.props("visible", true,
                "profiles", Map.of(6, Map.of("visible", false)));
        assertTrue(UiLayoutSpec.fromProps(props, ViewportContext.of(500, 320)).visible());
        assertFalse(UiLayoutSpec.fromProps(props, ViewportContext.of(100, 60)).visible());

        // 通过真实 root：6 档下节点不进入已提交树。
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(100, 60);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.PANEL, null,
                        UiTestFixture.props("visible", true,
                                "profiles", Map.of(5, Map.of("visible", false)))));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        assertEquals(ViewportProfile.MINIMAL, root.viewport().profile());
        assertEquals(List.of("screen"), fixture.tagsOf(root.inspection().committedRoot()));
    }

    @Test
    void layoutPropertiesCanAllBeOverriddenPerProfile() {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("width", "40%");
        overrides.put("height", 30.0);
        overrides.put("minWidth", 5.0);
        overrides.put("minHeight", 6.0);
        overrides.put("maxWidth", 900.0);
        overrides.put("maxHeight", 910.0);
        overrides.put("margin", 3.0);
        overrides.put("padding", 4.0);
        overrides.put("gap", 5.0);
        overrides.put("align", "center");
        overrides.put("justify", "end");
        overrides.put("anchor", "bottom-right");
        overrides.put("direction", "column");
        overrides.put("fontSize", 11.0);
        overrides.put("visible", false);

        UiLayoutSpec base = UiLayoutSpec.fromProps(UiTestFixture.props(), ViewportContext.of(500, 320));
        UiLayoutSpec overridden = UiLayoutSpec.fromProps(
                UiTestFixture.props("profiles", Map.of(2, overrides)), ViewportContext.of(500, 320));

        assertEquals(UiDimension.percent(40), overridden.width());
        assertEquals(UiDimension.pixels(30), overridden.height());
        assertEquals(5.0, overridden.minWidth());
        assertEquals(6.0, overridden.minHeight());
        assertEquals(900.0, overridden.maxWidth());
        assertEquals(910.0, overridden.maxHeight());
        assertEquals(3.0, overridden.margin());
        assertEquals(4.0, overridden.padding());
        assertEquals(5.0, overridden.gap());
        assertEquals("center", overridden.align().jsName());
        assertEquals("end", overridden.justify().jsName());
        assertEquals("bottom-right", overridden.anchor().jsName());
        assertEquals(UiAxis.COLUMN, overridden.direction());
        assertFalse(overridden.visible());
        // 覆盖缺省不改变其它属性：基础 spec 的缺省仍然成立。
        assertEquals(UiDimension.auto(), base.width());
        assertTrue(base.visible());
    }

    // ---- AC4：设计坐标与字号 ----

    @Test
    void designPixelsScaleContinuouslyWithContentWidth() {
        assertEquals(1.0, UiResponsiveRules.contentScale(ViewportContext.of(640, 400)), 0.0001);
        assertEquals(0.5, UiResponsiveRules.contentScale(ViewportContext.of(320, 200)), 0.0001);
        assertEquals(0.25, UiResponsiveRules.contentScale(ViewportContext.of(160, 120)), 0.0001);
        assertEquals(1.0, UiResponsiveRules.contentScale(ViewportContext.of(2000, 900)),
                "超宽视口不放大设计稿");

        var props = UiTestFixture.props("width", "80dp", "height", 20.0);
        UiLayoutSpec wide = UiLayoutSpec.fromProps(props, ViewportContext.of(640, 400));
        assertEquals(UiDimension.Kind.DESIGN, wide.width().kind());
        assertEquals("80dp", wide.width().literal());
        assertEquals(80.0, wide.width().value(), 0.0001, "设计坐标保留设计像素原值");
        assertEquals(20.0, wide.height().value(), 0.0001, "逻辑像素不受缩放影响");

        UiHostNode screen = renderSingleLabel(320, 200, "width", "80dp", "height", 20.0);
        assertEquals(40.0, screen.children().get(0).layout().width(), 0.0001,
                "80dp 在可用宽 320（scale 0.5）下解析为 40 逻辑像素");
    }

    @Test
    void bareNumbersAreAlwaysLogicalPixelsAndNeverSilentlyScaled() {
        assertEquals(UiDimension.Kind.PIXELS, UiDimension.parse(80).kind());
        assertEquals(UiDimension.Kind.PIXELS, UiDimension.parse("80").kind());
        assertEquals(UiDimension.Kind.DESIGN, UiDimension.parse("80dp").kind());
        assertThrows(IllegalArgumentException.class, () -> UiDimension.parse("dp"));
        assertThrows(IllegalArgumentException.class, () -> UiDimension.parse("80dpdp"));
    }

    @Test
    void fontSizeIsClampedAndGrowsSlowerThanTheViewport() {
        // 读性区间固定：6..24。
        assertEquals(6.0, UiResponsiveRules.resolveFontSize(4.0, 1.0), 0.0001);
        assertEquals(24.0, UiResponsiveRules.resolveFontSize(200.0, 1.0), 0.0001);
        // sqrt 缩放：scale 0.25 时 12 → 6；线性等比会是 3（低于下限），被抬起。
        assertEquals(6.0, UiResponsiveRules.resolveFontSize(12.0, 0.25), 0.0001);
        assertEquals(12.0 * Math.sqrt(0.5), UiResponsiveRules.resolveFontSize(12.0, 0.5), 0.0001);
        assertTrue(UiResponsiveRules.resolveFontSize(12.0, 0.5) > 12.0 * 0.5,
                "字号必须比容器缩放得慢（不做线性等比）");
        // 从 1 档降到 6 档：字号收缩比可用宽收缩慢得多（并被读性下限兜住）。
        double wide = UiResponsiveRules.resolveFontSize(12.0, ViewportContext.of(640, 400));
        double narrow = UiResponsiveRules.resolveFontSize(12.0, ViewportContext.of(100, 60));
        assertEquals(6.0, narrow, 0.0001, "极端窄屏落到读性下限 6，而不是线性缩到 1.875");
        assertTrue(narrow / wide > 100.0 / 640.0, "字号收缩必须慢于视口收缩");
        assertThrows(UiFailure.class, () -> UiResponsiveRules.resolveFontSize(0, 1.0));
        assertThrows(UiFailure.class, () -> UiResponsiveRules.resolveFontSize(8.0, -1.0));
    }

    @Test
    void labelMeasurementUsesTheResolvedFontSizeNotTheRawOne() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(100, 60);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"),
                UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "hello")));
        fixture.facade().open(fixture.screen());

        double expected = UiResponsiveRules.resolveFontSize(8.0,
                UiResponsiveRules.contentScale(ViewportContext.of(100, 60)));
        assertEquals(6.0, expected, 0.0001, "100 逻辑像素宽的可用区把 8 号字压到下限 6");
        assertEquals(expected, fixture.textMeasures.getFirst().fontSize(), 0.0001,
                "文本测量必须使用受控解析后的字号");
    }

    // ---- 辅助 ----

    private static UiHostNode renderSingleLabel(double width, double height, Object... labelProps) {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(width, height);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"),
                UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props(labelProps)));
        return fixture.facade().open(fixture.screen()).inspection().committedRoot();
    }

    private static UiProfileOverride override(Map<String, Object> values) {
        return UiProfileOverride.parseAll(Map.of(1, values), null).get(ViewportProfile.DESKTOP_WIDE);
    }
}