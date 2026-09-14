package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiLayoutSpec;
import com.tkisor.nekojs.api.ui.UiOverflow;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.ViewportContext;
import com.tkisor.nekojs.api.ui.ViewportProfile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC6：六个 Viewport Profile 均有<b>稳定</b> fake 输出。
 *
 * <p>输出是 fake Adapter 下整棵树的最终矩形、裁剪、溢出诊断与每个可见节点的最终解析样式
 * （profile 覆盖与设计坐标缩放都已应用）。它是冻结基线：普通测试只读；
 * 变更必须走 {@code -Dnekojs.golden.regenerate=true} 再生成，并由维护者审阅旧新 diff。
 *
 * <p>断言来自公开观察面（{@code UiRootHandle.inspection()} / {@code viewportState()}），
 * 不使用私有 widget 布局，也不包含任何 Minecraft 类型。
 */
class UiViewportProfileGoldenTest {

    private static final Path GOLDEN_PATH =
            Path.of("src/test/resources/nekojs/golden/jsx-ui-viewport-profiles.txt");

    /** 六个档位各自的代表视口（含 GUI scale 与安全区域，用来证明它们不决定档位）。 */
    private static final List<Case> CASES = List.of(
            new Case(ViewportProfile.DESKTOP_WIDE, 900, 520, 2, ViewportContext.SafeArea.none()),
            new Case(ViewportProfile.DESKTOP, 520, 340, 1, ViewportContext.SafeArea.none()),
            new Case(ViewportProfile.TABLET, 380, 260, 3, ViewportContext.SafeArea.none()),
            new Case(ViewportProfile.HANDHELD, 280, 200, 1, ViewportContext.SafeArea.none()),
            new Case(ViewportProfile.COMPACT, 200, 140, 2, ViewportContext.SafeArea.none()),
            new Case(ViewportProfile.MINIMAL, 160, 110, 4, ViewportContext.SafeArea.none()));

    @Test
    void sixProfilesProduceStableFakeOutput() throws Exception {
        String actual = report();

        if (Boolean.getBoolean("nekojs.golden.regenerate")) {
            Files.createDirectories(GOLDEN_PATH.getParent());
            Files.writeString(GOLDEN_PATH, actual);
            Assumptions.abort("viewport profile golden regenerated, review the diff before committing");
        }

        assertEquals(Files.readString(GOLDEN_PATH), actual,
                "六档 Viewport Profile 的 fake 输出漂移；用 "
                        + "`./gradlew :common:test -Dnekojs.golden.regenerate=true --tests "
                        + "*UiViewportProfileGoldenTest` 再生成，并在维护者审阅旧新 diff 后提交");
    }

    @Test
    void regeneratedGoldenIsOffDuringNormalRuns() {
        org.junit.jupiter.api.Assertions.assertFalse(
                Boolean.getBoolean("nekojs.golden.regenerate"),
                "普通测试不得写 golden");
    }

    /** 报告必须覆盖六个档位，且每个档位都有非空的可观察输出。 */
    @Test
    void reportCoversAllSixProfilesWithDistinctOutput() {
        String report = report();
        for (ViewportProfile profile : ViewportProfile.values()) {
            assertTrue(report.contains("profile=" + profile.code()),
                    "缺少档位 " + profile.code() + " 的输出");
        }
        // 六个输出块必须彼此不同：用 rects 段落作为指纹（不同档位的最终矩形必然不同）。
        String[] blocks = report.split("(?m)^profile=");
        assertEquals(7, blocks.length, "报告必须恰好包含六个档位块");
        long distinct = java.util.Arrays.stream(blocks).skip(1)
                .map(block -> block.substring(block.indexOf("rects:")))
                .distinct().count();
        assertEquals(6, distinct, "六个档位的输出块必须彼此不同（否则 golden 无法发现漂移）");
    }

    // ---- 报告构造 ----

    private static String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("# NekoJS JSX UI Viewport Profile fake output (ticket 43 AC6)\n");
        sb.append("# Frozen golden: read-only for normal tests; regenerate explicitly and review the diff.\n");
        for (Case testCase : CASES) {
            sb.append("\n");
            sb.append(block(testCase));
        }
        return sb.toString();
    }

    private static String block(Case testCase) {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(testCase.width(), testCase.height());
        fixture.safeArea = testCase.safeArea();
        fixture.guiScale = testCase.guiScale();
        fixture.render = UiViewportProfileGoldenTest::screen;

        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());
        assertTrue(fixture.diagnostics.isEmpty(), "golden 场景不得产生诊断: " + fixture.diagnostics);
        var inspection = root.paint();

        StringBuilder sb = new StringBuilder();
        var viewport = root.viewportState().viewport();
        assertEquals(testCase.expected(), viewport.profile(),
                "代表视口必须落在预期档位: " + testCase);
        sb.append("profile=").append(viewport.profile().code())
                .append(" (").append(viewport.profile().number()).append(")\n");
        sb.append("logical=").append(number(viewport.logicalWidth()))
                .append('x').append(number(viewport.logicalHeight())).append('\n');
        sb.append("content=").append(number(viewport.contentWidth()))
                .append('x').append(number(viewport.contentHeight())).append('\n');
        sb.append("safeArea=").append(number(viewport.safeArea().left())).append(',')
                .append(number(viewport.safeArea().top())).append(',')
                .append(number(viewport.safeArea().right())).append(',')
                .append(number(viewport.safeArea().bottom())).append('\n');
        sb.append("guiScale=").append(viewport.guiScale())
                .append(" capabilities=").append(viewport.capabilities()).append('\n');
        sb.append("layoutRevisions=").append(inspection.layoutRevisions()).append('\n');

        sb.append("styles:\n");
        for (Map.Entry<String, UiLayoutSpec> entry : new java.util.TreeMap<>(
                root.viewportState().resolvedStyles()).entrySet()) {
            UiLayoutSpec style = entry.getValue();
            sb.append("  ").append(entry.getKey())
                    .append(" width=").append(style.width().literal())
                    .append(" height=").append(style.height().literal())
                    .append(" min=").append(number(style.minWidth())).append('/').append(number(style.minHeight()))
                    .append(" max=").append(number(style.maxWidth())).append('/').append(number(style.maxHeight()))
                    .append(" margin=").append(number(style.margin()))
                    .append(" padding=").append(number(style.padding()))
                    .append(" gap=").append(number(style.gap()))
                    .append(" align=").append(style.align().jsName())
                    .append(" justify=").append(style.justify().jsName())
                    .append(" anchor=").append(style.anchor().jsName())
                    .append(" grow=").append(number(style.grow()))
                    .append(" visible=").append(style.visible())
                    .append(" direction=").append(style.direction() == null ? "inherit" : style.direction().code())
                    .append(" contentScale=").append(number(style.contentScale()))
                    .append(" scroll=").append(number(style.scrollX())).append('/').append(number(style.scrollY()))
                    .append(" text=").append(style.fontSize() == null ? "inherit" : number(style.fontSize()))
                    .append('/').append(style.font() == null ? "inherit" : style.font())
                    .append('/').append(style.wrap() == null ? "inherit" : style.wrap())
                    .append('\n');
        }

        sb.append("rects:\n");
        for (UiHostNode node : flatten(inspection.committedRoot())) {
            sb.append("  ").append(node.path()).append(' ')
                    .append(rect(node.layout())).append('\n');
        }
        sb.append("clip:\n");
        for (Map.Entry<String, UiRect> entry : new java.util.TreeMap<>(inspection.clipRects()).entrySet()) {
            sb.append("  ").append(entry.getKey()).append(' ')
                    .append(rect(entry.getValue())).append('\n');
        }
        sb.append("overflow:\n");
        for (Map.Entry<String, UiOverflow> entry : new java.util.TreeMap<>(inspection.overflow()).entrySet()) {
            sb.append("  ").append(entry.getKey())
                    .append(" h=").append(number(entry.getValue().horizontal()))
                    .append(" v=").append(number(entry.getValue().vertical()))
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * 六档共用同一棵代表性 UI：基础值是窄屏策略，1 档放宽间距，4–6 档切换方向、收缩字号、
     * 隐藏次要内容——覆盖「窄屏/宽屏重排不需要每个属性重复填写六遍」（AC3）。
     */
    private static com.tkisor.nekojs.api.ui.UiVNode screen() {
        Map<String, Object> wide = Map.of("padding", 12.0);
        Map<String, Object> narrow = Map.of("padding", 4.0);
        Map<String, Object> minimal = Map.of("padding", 2.0);

        return UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill", "padding", 8.0,
                        "profiles", Map.of(1, wide, 4, narrow, 6, minimal)),
                UiTestFixture.host(UiPrimitive.COLUMN, null,
                        UiTestFixture.props("gap", 6.0, "width", "fill",
                                "profiles", Map.of(1, Map.of("gap", 10.0), 6, Map.of("gap", 1.0))),
                        UiTestFixture.host(UiPrimitive.ROW, null,
                                UiTestFixture.props("gap", 4.0, "width", "fill",
                                        "profiles", Map.of(4, Map.of("direction", "column", "gap", 2.0))),
                                UiTestFixture.host(UiPrimitive.LABEL, "title", UiTestFixture.props(
                                        "text", "title", "fontSize", 12.0,
                                        "profiles", Map.of(5, Map.of("fontSize", 9.0)))),
                                UiTestFixture.host(UiPrimitive.SPACER, "gap", UiTestFixture.props("grow", 1.0)),
                                UiTestFixture.host(UiPrimitive.BUTTON, "ok", UiTestFixture.props(
                                        "text", "ok", "width", "60dp", "height", 20.0,
                                        "profiles", Map.of(4, Map.of("width", 50.0, "height", 16.0))))),
                        UiTestFixture.host(UiPrimitive.PANEL, null,
                                UiTestFixture.props("width", "fill", "height", 30.0, "padding", 2.0,
                                        "profiles", Map.of(5, Map.of("height", 20.0),
                                                6, Map.of("height", 14.0))),
                                UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props(
                                        "text", "body text that wraps", "wrap", true, "fontSize", 9.0,
                                        "profiles", Map.of(6, Map.of("visible", false))))),
                        // 固定 400 逻辑像素的内容：窄档必然溢出，用来冻结溢出诊断的输出。
                        UiTestFixture.host(UiPrimitive.PANEL, "fixed", UiTestFixture.props(
                                "width", 400.0, "height", 8.0)),
                        // percent + min/max + scroll offset 的解析与裁剪（AC2/AC3）。
                        UiTestFixture.host(UiPrimitive.SCROLL, "scroller", UiTestFixture.props(
                                "width", "50%", "height", 24.0, "minWidth", 40.0, "maxWidth", 160.0,
                                "profiles", Map.of(2, Map.of("width", "25%", "scrollY", 6.0),
                                        4, Map.of("scrollY", 12.0, "maxWidth", 90.0))),
                                UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props(
                                        "width", 300.0, "height", 40.0)))));
    }

    private static List<UiHostNode> flatten(UiHostNode node) {
        List<UiHostNode> all = new ArrayList<>();
        collect(node, all);
        return all;
    }

    private static void collect(UiHostNode node, List<UiHostNode> out) {
        out.add(node);
        for (UiHostNode child : node.children()) {
            collect(child, out);
        }
    }

    private static String rect(UiRect rect) {
        return number(rect.x()) + "," + number(rect.y()) + " "
                + number(rect.width()) + "x" + number(rect.height());
    }

    /** 稳定数值格式：整数不带小数点，小数保留两位，避免浮点尾数造成伪漂移。 */
    private static String number(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private record Case(ViewportProfile expected, double width, double height, int guiScale,
                        ViewportContext.SafeArea safeArea) {
    }
}