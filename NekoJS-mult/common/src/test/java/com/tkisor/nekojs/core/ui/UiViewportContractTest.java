package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.ui.ProjectedViewport;
import com.tkisor.nekojs.api.ui.UiLayoutSpec;
import com.tkisor.nekojs.api.ui.UiProfileOverride;
import com.tkisor.nekojs.api.ui.UiSpec;
import com.tkisor.nekojs.api.ui.UiViewportState;
import com.tkisor.nekojs.api.ui.ViewportContext;
import com.tkisor.nekojs.api.ui.ViewportProfile;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;
import com.tkisor.nekojs.core.api.FrozenApiRegistry;
import com.tkisor.nekojs.core.api.JsApiSurfaceResolver;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC1/AC2/AC6：Viewport Profile 的判定规则、可观察输出与响应式解析语义进入公开 contract。
 *
 * <p>断言穿过最高调用者 Seam（{@code CoreManagedApiBootstrap} → 契约 → 冻结表面 →
 * JSX 声明），不检查布局引擎私有字段。
 */
class UiViewportContractTest {

    private static final URI CODE_SOURCE = URI.create("file:///ticket43-viewport-profiles.jar");

    @BeforeAll
    static void init() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void viewportProfileCapabilityIsDeclaredInTheNormativeContract() {
        var capabilities = CoreManagedApiBootstrap.buildContract(CODE_SOURCE)
                .contract().capabilities();
        Set<String> ids = capabilities.stream()
                .map(NormativeApiContract.ContractCapability::id)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(ids.contains("ui.viewport-profiles"),
                "六档 Viewport Profile capability 必须进入契约: " + ids);
        assertTrue(UiSpec.CAPABILITY_IDS.contains("ui.viewport-profiles"));

        var capability = capabilities.stream()
                .filter(entry -> "ui.viewport-profiles".equals(entry.id()))
                .findFirst().orElseThrow();
        assertEquals(com.tkisor.nekojs.api.capability.CapabilityStatus.SUPPORTED, capability.status(),
                "common 侧判定与解析已完整实现，六个档位都有 fake 输出");
    }

    /**
     * AC1 的规则文本与枚举阈值必须同源：规则表由 {@link ViewportProfile} 迭代产出，
     * 任何一处阈值漂移都会让本文本与枚举不一致。
     */
    @Test
    void declaredRuleTextMatchesTheEnumThresholds() {
        String rule = UiSpec.viewportProfileRule();
        for (ViewportProfile profile : ViewportProfile.values()) {
            assertTrue(rule.contains(profile.code()),
                    "规则文本必须列出档位 " + profile.code());
            assertTrue(rule.contains((long) profile.minContentWidth() + "x"
                            + (long) profile.minContentHeight()),
                    "规则文本阈值必须来自枚举: " + profile.code());
        }
        assertTrue(rule.contains("tie-break"), "规则文本必须写明 tie-break");
        assertTrue(rule.contains("安全区域"), "规则文本必须写明安全区域");
        assertTrue(rule.contains("GUI scale"), "规则文本必须写明与 GUI scale 的关系");
        assertFalse(rule.isBlank());
    }

    @Test
    void viewportVocabularyIsDerivableFromThePublicSurfaceWithoutGoldenChange() {
        // 票 43 不扩 symbols（保持票 40 的冻结 golden 不变）：视口语汇经 capability + 声明进入契约。
        CoreManagedApiBootstrap.CoreManagedApi core =
                CoreManagedApiBootstrap.load(new EmptyPlatform(), CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                serverEnvironment(), core.contracts(), java.util.List.of(core.contributions()),
                java.util.List.of());
        Set<String> symbols = registry.environmentSnapshot().surfaceSnapshot().symbols().stream()
                .map(symbol -> symbol.id().value())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(symbols.contains("global:Ui"));
        assertFalse(symbols.contains("member:Ui.viewportState"),
                "viewportState 是 Java 侧只读查询，不作为脚本 facade 成员符号暴露");
    }

    @Test
    void jsxDeclarationCarriesTheViewportVocabulary() {
        String jsx = UiJsxDeclarationGenerator.generate();
        assertTrue(jsx.contains("readonly viewport: ProjectedViewport;"), jsx);
        assertTrue(jsx.contains("readonly viewportState: UiViewportState;"), jsx);
        assertTrue(jsx.contains("interface ProjectedViewport"), jsx);
        assertTrue(jsx.contains("interface UiViewportState"), jsx);
        assertTrue(jsx.contains("interface UiResolvedStyle"), jsx);
        assertTrue(jsx.contains("type ViewportProfileCode = "
                + "'desktop-wide' | 'desktop' | 'tablet' | 'handheld' | 'compact' | 'minimal';"), jsx);
        assertTrue(jsx.contains("type UiAxis = 'row' | 'column';"), jsx);
        // 属性词汇表仍然来自 UiProp.kind()：新增属性必须出现在声明里。
        assertTrue(jsx.contains("readonly direction?: NekoJsx.UiAxis;"), jsx);
        assertTrue(jsx.contains("readonly profiles?: NekoJsx.UiProfileOverrides;"), jsx);
    }

    @Test
    void resolvedStylesViewIsImmutableAndReflectsTheAppliedOverride() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new com.tkisor.nekojs.api.ui.UiSize(900, 520);
        fixture.render = () -> UiTestFixture.host(com.tkisor.nekojs.api.ui.UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill",
                        "profiles", java.util.Map.of(1, java.util.Map.of("gap", 14.0))));
        var root = fixture.facade().open(fixture.screen());

        UiViewportState state = root.viewportState();
        assertNotNull(state.viewport());
        assertEquals(ViewportProfile.DESKTOP_WIDE, state.profile());
        assertEquals(900.0, state.contentWidth(), 0.0001);
        assertEquals(520.0, state.contentHeight(), 0.0001);

        assertTrue(fixture.diagnostics.isEmpty(), "诊断: " + fixture.diagnostics);
        java.util.Map<String, UiLayoutSpec> styles = state.resolvedStyles();
        assertEquals(14.0, styles.get("screen").gap(), 0.0001,
                "解析样式必须反映已应用的 profile 覆盖");
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> styles.put("x", UiLayoutSpec.defaults()),
                "解析样式视图必须是不可变的");
    }

    @Test
    void projectedViewportProjectsEveryJudgementInputAndOutput() {
        ViewportContext context = new ViewportContext(520, 340,
                new ViewportContext.SafeArea(10, 20, 30, 40), 3,
                Set.of(ViewportContext.Capability.RESPONSIVE_LAYOUT));
        ProjectedViewport projected = ProjectedViewport.of(context, 7);
        assertEquals(480.0, projected.contentWidth(), 0.0001);
        assertEquals(280.0, projected.contentHeight(), 0.0001);
        assertEquals(ViewportProfile.TABLET, projected.profile());
        assertEquals(3, projected.guiScale());
        assertEquals(java.util.List.of("responsive-layout"), projected.capabilities());
        assertEquals(7, projected.revision());
        assertTrue(projected.atLeast(ViewportProfile.HANDHELD));
        assertFalse(projected.atLeast(ViewportProfile.DESKTOP));
    }

    @Test
    void overrideVocabularyIsExactlyTheDeclaredKeys() {
        assertEquals(java.util.List.of("align", "anchor", "direction", "fontSize", "gap", "height",
                "justify", "margin", "maxHeight", "maxWidth", "minHeight", "minWidth", "padding",
                "visible", "width"), UiProfileOverride.KEYS);
    }

    private static com.tkisor.nekojs.api.surface.EnvironmentKey serverEnvironment() {
        return new com.tkisor.nekojs.api.surface.EnvironmentKey(
                com.tkisor.nekojs.api.surface.ScriptTypeId.SERVER,
                com.tkisor.nekojs.api.surface.RuntimeDist.DEDICATED_SERVER,
                "neoforge", "21.1.0",
                com.tkisor.nekojs.api.surface.LoaderVersion.parse("21.1.0"),
                "1.21.1", java.util.Map.of());
    }

    private static final class EmptyPlatform implements com.tkisor.nekojs.platform.IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return false; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public String getLoaderId() { return "neoforge"; }
        @Override public String getLoaderVersion() { return "21.1.0"; }
        @Override public java.nio.file.Path getGameDir() { return java.nio.file.Path.of("."); }
        @Override public java.util.Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() {
            return java.util.Map.of();
        }
        @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
    }
}
