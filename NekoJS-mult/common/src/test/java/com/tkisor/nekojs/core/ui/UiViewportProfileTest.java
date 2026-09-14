package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiResponsiveRules;
import com.tkisor.nekojs.api.ui.ViewportContext;
import com.tkisor.nekojs.api.ui.ViewportProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC1：Viewport Profile 1–6 的判定规则、边界、优先级、tie-break 与可观察输出。
 *
 * <p>判定只看「实际逻辑视口 - 安全区域」的可用布局区与 host 布局能力；GUI scale 只是输入事实，
 * 既不能等同 profile 编号，也不能反向推导档位。
 */
class UiViewportProfileTest {

    @Test
    void sixProfilesAreNumberedOneToSixWithStableCodes() {
        assertEquals(ViewportProfile.COUNT, ViewportProfile.values().length);
        assertEquals(List.of(1, 2, 3, 4, 5, 6),
                java.util.Arrays.stream(ViewportProfile.values()).map(ViewportProfile::number).toList());
        assertEquals(List.of("desktop-wide", "desktop", "tablet", "handheld", "compact", "minimal"),
                ViewportProfile.codes());
        for (ViewportProfile profile : ViewportProfile.values()) {
            assertEquals(profile, ViewportProfile.byNumber(profile.number()));
            assertEquals(profile, ViewportProfile.fromCode(profile.code()));
        }
    }

    @Test
    void everyBoundaryMapsToItsOwnProfileInclusive() {
        // 恰好落在阈值上属于该档（>= 是闭区间下界），减 1 逻辑像素掉到下一档。
        assertBoundary(640, 360, ViewportProfile.DESKTOP_WIDE, ViewportProfile.DESKTOP);
        assertBoundary(480, 300, ViewportProfile.DESKTOP, ViewportProfile.TABLET);
        assertBoundary(360, 240, ViewportProfile.TABLET, ViewportProfile.HANDHELD);
        assertBoundary(260, 180, ViewportProfile.HANDHELD, ViewportProfile.COMPACT);
        assertBoundary(180, 120, ViewportProfile.COMPACT, ViewportProfile.MINIMAL);
    }

    private static void assertBoundary(double width, double height,
                                       ViewportProfile atThreshold, ViewportProfile justBelow) {
        assertEquals(atThreshold, ViewportContext.of(width, height).profile(),
                "可用区 " + width + "x" + height + " 正好落在阈值上，应判为 " + atThreshold.code());
        assertEquals(justBelow, ViewportContext.of(width - 1, height).profile(),
                "可用区宽减 1 逻辑像素应掉到 " + justBelow.code());
        assertEquals(justBelow, ViewportContext.of(width, height - 1).profile(),
                "可用区高减 1 逻辑像素应掉到 " + justBelow.code());
    }

    @Test
    void narrowAxisWinsRegardlessOfTheOtherAxis() {
        // tie-break：两轴都必须满足高档阈值；细长视口取更窄的档，不回退到宽档。
        assertEquals(ViewportProfile.HANDHELD, ViewportContext.of(800, 200).profile());
        assertEquals(ViewportProfile.COMPACT, ViewportContext.of(1920, 130).profile());
        assertEquals(ViewportProfile.MINIMAL, ViewportContext.of(1920, 40).profile());
        // 反向：窄但很高的视口同样取更窄的档（不按面积、不按单轴最大值）。
        assertEquals(ViewportProfile.TABLET, ViewportContext.of(360, 2000).profile());
        assertEquals(ViewportProfile.COMPACT, ViewportContext.of(200, 2000).profile());
    }

    @Test
    void safeAreaIsSubtractedBeforeTheDecision() {
        assertEquals(ViewportProfile.DESKTOP_WIDE, ViewportContext.of(700, 400).profile());
        ViewportContext withChrome = new ViewportContext(700, 400,
                ViewportContext.SafeArea.of(60), 1, Set.of());
        assertEquals(580.0, withChrome.contentWidth(), 0.0001);
        assertEquals(280.0, withChrome.contentHeight(), 0.0001);
        // 安全区域把可用高拉到 300 以下 → 掉到 3 档（判定用可用区而不是原始视口）。
        assertEquals(ViewportProfile.TABLET, withChrome.profile());
    }

    @Test
    void safeAreaNeverProducesNegativeContentArea() {
        ViewportContext tiny = new ViewportContext(100, 100,
                new ViewportContext.SafeArea(80, 80, 80, 80), 1, Set.of());
        assertEquals(0.0, tiny.contentWidth(), 0.0001);
        assertEquals(0.0, tiny.contentHeight(), 0.0001);
        assertEquals(ViewportProfile.MINIMAL, tiny.profile());
    }

    @Test
    void guiScaleIsAnInputFactAndNeverDeterminesTheProfile() {
        // 同一逻辑视口配 6 个不同的原生 scale：profile 完全相同。
        for (int scale = 1; scale <= 6; scale++) {
            ViewportContext context = new ViewportContext(500, 320,
                    ViewportContext.SafeArea.none(), scale, Set.of());
            assertEquals(ViewportProfile.DESKTOP, context.profile(),
                    "第 " + scale + " 档原生 scale 不得改变 profile 判定");
            assertEquals(scale, context.guiScale(), "原生 scale 仍需作为事实被记录");
        }
        // scale 数字与 profile 编号互不推导：500x320 在 scale 3 下仍不是 3 档。
        assertNotEquals(ViewportProfile.byNumber(3),
                new ViewportContext(500, 320, ViewportContext.SafeArea.none(), 3, Set.of()).profile());
    }

    @Test
    void declaredCapabilitiesAreInputsToTheDecision() {
        ViewportContext fixed = new ViewportContext(500, 320, ViewportContext.SafeArea.none(), 1,
                EnumSet.of(ViewportContext.Capability.FIXED_VIEWPORT));
        assertTrue(fixed.has(ViewportContext.Capability.FIXED_VIEWPORT));
        assertEquals(ViewportProfile.DESKTOP, fixed.profile(),
                "固定视口退化为按当前视口判档，不产生额外档位");
        assertTrue(fixed.capabilities().contains(ViewportContext.Capability.FIXED_VIEWPORT));
        // 能力集合是防御性拷贝：外部改动不影响已构造的输入事实。
        Set<ViewportContext.Capability> mutable = EnumSet.of(ViewportContext.Capability.RESPONSIVE_LAYOUT);
        ViewportContext snapshot = new ViewportContext(500, 320, ViewportContext.SafeArea.none(), 1, mutable);
        mutable.clear();
        assertTrue(snapshot.has(ViewportContext.Capability.RESPONSIVE_LAYOUT));
    }

    @Test
    void profileIsMonotonicInContentArea() {
        // 同一个高，宽从 0 增到 1000：档位编号单调不增（越宽越靠前）。
        ViewportProfile previous = ViewportProfile.MINIMAL;
        for (double width = 0; width <= 1000; width += 25) {
            ViewportProfile current = ViewportContext.of(width, 500).profile();
            assertTrue(current.number() <= previous.number(),
                    "可用宽增加不得让档位变窄: width=" + width + " " + previous.code() + " -> " + current.code());
            previous = current;
        }
        assertEquals(ViewportProfile.DESKTOP_WIDE, previous);
    }

    @Test
    void invalidProfileNumberIsRejectedExplicitly() {
        for (int invalid : new int[]{0, -1, 7, 99}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> ViewportProfile.byNumber(invalid));
            assertTrue(failure.getMessage().contains("[1, 6]"), failure.getMessage());
        }
        assertTrue(ViewportProfile.fromCode("huge") == null);
        assertTrue(ViewportProfile.fromCode(null) == null);
    }

    @Test
    void invalidViewportInputsAreRejectedExplicitly() {
        UiFailure negative = assertThrows(UiFailure.class, () -> ViewportContext.of(-1, 100));
        assertEquals(UiFailure.INVALID_LAYOUT, negative.code());
        assertThrows(UiFailure.class, () -> ViewportContext.of(Double.NaN, 100));
        assertThrows(UiFailure.class, () -> ViewportContext.of(100, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new ViewportContext(100, 100, ViewportContext.SafeArea.none(), 0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ViewportContext(100, 100, ViewportContext.SafeArea.of(-1), 1, Set.of()));
    }

    @Test
    void neighboursAndHelpersAreDerivedFromTheSameTable() {
        assertEquals(ViewportProfile.DESKTOP, ViewportProfile.DESKTOP_WIDE.narrower().orElseThrow());
        assertEquals(ViewportProfile.DESKTOP_WIDE, ViewportProfile.DESKTOP.wider().orElseThrow());
        assertTrue(ViewportProfile.DESKTOP_WIDE.wider().isEmpty());
        assertTrue(ViewportProfile.MINIMAL.narrower().isEmpty());
        // UiResponsiveRules 是判定唯一入口：ViewportContext 委托给它，结果必须一致。
        for (ViewportProfile profile : ViewportProfile.values()) {
            assertEquals(profile, ViewportContext.of(profile.minContentWidth(), profile.minContentHeight()).profile());
        }
        assertEquals(ViewportProfile.DESKTOP,
                UiResponsiveRules.profileOf(500, 320, ViewportContext.SafeArea.none()));
    }
}
