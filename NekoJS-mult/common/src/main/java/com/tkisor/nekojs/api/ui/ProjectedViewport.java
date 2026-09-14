package com.tkisor.nekojs.api.ui;

/**
 * root 对外可观察的响应式视口事实（jsx-ui-spec「响应式模型」，票 43 AC1/AC5）。
 *
 * <p>由 {@link UiRootHandle#viewport()} 返回；inspector 与 golden 只读这里的事实，
 * 不重新推导 profile。字段都是判定输入 + 判定结果的直接投影，因此不存在第二套布局真相源。
 *
 * @param profile      判定得到的档位（可观察输出）
 * @param logicalWidth 逻辑视口宽
 * @param logicalHeight 逻辑视口高
 * @param contentWidth 可用布局宽（逻辑视口宽 - 安全区域水平占用）
 * @param contentHeight 可用布局高（逻辑视口高 - 安全区域垂直占用）
 * @param safeArea      常驻 chrome 占用的安全区域
 * @param guiScale      原生 GUI scale（仅作为输入事实被记录，不参与档位判定）
 * @param capabilities  参与判定的 host 布局能力码（稳定排序）
 * @param revision      布局代数：每成功应用一次新的视口事实 +1
 */
public record ProjectedViewport(
        ViewportProfile profile,
        double logicalWidth,
        double logicalHeight,
        double contentWidth,
        double contentHeight,
        ViewportContext.SafeArea safeArea,
        int guiScale,
        java.util.List<String> capabilities,
        long revision
) {

    public ProjectedViewport {
        java.util.Objects.requireNonNull(profile, "profile");
        safeArea = safeArea == null ? ViewportContext.SafeArea.none() : safeArea;
        capabilities = capabilities == null ? java.util.List.of() : java.util.List.copyOf(capabilities);
    }

    /** 从判定输入投影出可观察事实。 */
    public static ProjectedViewport of(ViewportContext context, long revision) {
        java.util.List<String> codes = new java.util.ArrayList<>();
        for (ViewportContext.Capability capability : context.capabilities()) {
            codes.add(capability.code());
        }
        java.util.Collections.sort(codes);
        return new ProjectedViewport(context.profile(), context.logicalWidth(), context.logicalHeight(),
                context.contentWidth(), context.contentHeight(), context.safeArea(), context.guiScale(),
                codes, revision);
    }

    /** 是否达到了某个档位（含更宽档位）。 */
    public boolean atLeast(ViewportProfile other) {
        return profile.number() <= other.number();
    }
}
