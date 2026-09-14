package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiAlignment;
import com.tkisor.nekojs.api.ui.UiAxis;
import com.tkisor.nekojs.api.ui.UiDimension;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiHostAdapter;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiLayoutSpec;
import com.tkisor.nekojs.api.ui.UiOverflow;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiResponsiveRules;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiTextMeasure;
import com.tkisor.nekojs.api.ui.UiTextMeasurer;
import com.tkisor.nekojs.api.ui.ViewportContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 布局引擎：measure → arrange（jsx-ui-spec「布局」）。
 *
 * <p>只实现明确的 Minecraft UI 约束：逻辑像素、fill、auto、受控百分比、设计坐标、min/max、
 * margin/padding/gap、主轴与交叉轴对齐、九宫格锚定，以及按 Viewport Profile 的覆盖与方向切换
 * （票 43 AC3/AC4）。不实现 CSS selector、继承、浏览器 box model 或任意 CSS 字符串。
 *
 * <p>解析入口只有一个：{@link UiLayoutSpec#fromProps(UiProps, ViewportContext)}。基础属性与
 * profile 覆盖在这一处合并，因此不存在「两套布局真相源」。
 *
 * <p>文本尺寸经 {@link UiTextMeasurer}（Client UI Adapter 负责字体访问与换行）；文本测量只发生
 * 在 measure 阶段，正常绘制帧不重新运行脚本。字号按
 * {@link UiResponsiveRules#resolveFontSize} 的受控区间解析——不随视口无限等比放大（AC4）。
 */
final class UiLayoutEngine {

    /** 单棵树的布局结果：最终解析样式与溢出诊断（AC5 的可观察输出）。 */
    record LayoutFacts(Map<String, UiLayoutSpec> resolvedStyles, Map<String, UiOverflow> overflow) {

        static LayoutFacts empty() {
            return new LayoutFacts(Map.of(), Map.of());
        }
    }

    private final UiHostAdapter adapter;
    private final UiTextMeasurer measurer;

    UiLayoutEngine(UiHostAdapter adapter, UiTextMeasurer measurer) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.measurer = measurer == null ? request -> UiSize.zero() : measurer;
    }

    /** 从 root 开始 measure + arrange 整棵树，并收集布局事实（AC5）。 */
    LayoutFacts layout(UiHostNode root, ViewportContext context) {
        Viewport viewport = new Viewport(context);
        UiSize size = new UiSize(context.logicalWidth(), context.logicalHeight());
        measure(root, viewport, size);
        UiRect clip = new UiRect(0, 0, size.width(), size.height());
        arrange(root, clip, clip, size, viewport);
        return new LayoutFacts(Map.copyOf(viewport.styles), Map.copyOf(viewport.overflow));
    }

    /**
     * 布局期间共享的解析状态：当前视口事实、已解析样式与溢出记录。
     *
     * <p>它只承载「本次 measure/arrange 已经算出的结果」，不是可变的第二份布局真相源。
     */
    private static final class Viewport {
        private final ViewportContext context;
        private final Map<String, UiLayoutSpec> styles = new LinkedHashMap<>();
        private final Map<String, UiOverflow> overflow = new LinkedHashMap<>();

        Viewport(ViewportContext context) {
            this.context = context;
        }

        UiLayoutSpec specOf(UiHostNode node) {
            UiLayoutSpec spec = UiLayoutSpec.fromProps(node.props(), context);
            styles.put(node.path(), spec);
            return spec;
        }

        void recordOverflow(String path, double horizontal, double vertical) {
            UiOverflow value = UiOverflow.of(horizontal, vertical);
            if (value.any()) {
                overflow.put(path, value);
            }
        }
    }

    /** 自底向上测量每个节点的内容尺寸（不含 margin）。 */
    UiSize measure(UiHostNode node, Viewport viewport, UiSize available) {
        UiLayoutSpec layout = parse(node, viewport);
        double pad = layout.padding();
        UiSize inner = new UiSize(Math.max(0, available.width() - 2 * pad),
                Math.max(0, available.height() - 2 * pad));

        UiSize content;
        if (node.children().isEmpty()) {
            content = leafSize(node, layout, viewport, inner);
        } else {
            content = containerSize(node, layout, viewport, inner);
        }
        double width = resolve(layout.width(), layout.minWidth(), layout.maxWidth(),
                content.width() + 2 * pad, available.width(), layout.contentScale());
        double height = resolve(layout.height(), layout.minHeight(), layout.maxHeight(),
                content.height() + 2 * pad, available.height(), layout.contentScale());
        return new UiSize(width, height);
    }

    /**
     * 摆放：父节点先定矩形，再按主轴/交叉轴规则摆放子节点。
     *
     * <p>{@code clip} 是祖先裁剪与该节点矩形的交集——与 {@code paint()} 的裁剪规则同一处语义，
     * 溢出诊断因此报告的是「该节点相对其实际可见区域越界了多少」，而不是另起一套判定。
     */
    void arrange(UiHostNode node, UiRect rect, UiRect clip, UiSize available, Viewport viewport) {
        adapter.arrange(node, rect);
        viewport.recordOverflow(node.path(),
                Math.max(0, rect.x() + rect.width() - (clip.x() + clip.width()))
                        + Math.max(0, clip.x() - rect.x()),
                Math.max(0, rect.y() + rect.height() - (clip.y() + clip.height()))
                        + Math.max(0, clip.y() - rect.y()));

        List<UiHostNode> children = node.children();
        if (children.isEmpty()) {
            return;
        }
        UiLayoutSpec layout = parse(node, viewport);
        double pad = layout.padding();
        double innerX = rect.x() + pad;
        double innerY = rect.y() + pad;
        double innerWidth = Math.max(0, rect.width() - 2 * pad);
        double innerHeight = Math.max(0, rect.height() - 2 * pad);

        List<UiSize> sizes = new ArrayList<>();
        for (UiHostNode child : children) {
            sizes.add(measure(child, viewport, new UiSize(innerWidth, innerHeight)));
        }

        UiRect childrenClip = clip.intersect(rect);
        UiPrimitive primitive = node.primitive();
        boolean horizontal = horizontal(primitive, layout);
        switch (primitive) {
            case ROW, COLUMN -> arrangeLinear(children, sizes, layout, horizontal,
                    innerX, innerY, innerWidth, innerHeight, childrenClip, viewport);
            case SCREEN, STACK, PANEL, SCROLL -> arrangeStack(children, sizes, layout,
                    innerX, innerY, innerWidth, innerHeight, childrenClip, viewport);
            default -> arrangeStack(children, sizes, layout, innerX, innerY, innerWidth, innerHeight,
                    childrenClip, viewport);
        }
    }

    // ---- measure 辅助 ----

    private UiSize leafSize(UiHostNode node, UiLayoutSpec layout, Viewport viewport, UiSize available) {
        if (node.primitive() == UiPrimitive.LABEL) {
            UiProps props = node.props();
            String text = props.string(UiProp.TEXT, "");
            double fontSize = UiResponsiveRules.resolveFontSize(
                    props.number(UiProp.FONT_SIZE, 8), layout.contentScale());
            boolean wrap = props.bool(UiProp.WRAP, false);
            String font = props.string(UiProp.FONT);
            UiDimension width = layout.width();
            double measuredWidth = resolveWidth(width, available.width(), layout.contentScale());
            return measurer.measure(new UiTextMeasure(text, font, fontSize, wrap, measuredWidth));
        }
        if (node.primitive() == UiPrimitive.SPACER || node.primitive() == UiPrimitive.IMAGE) {
            return new UiSize(0, 0);
        }
        return new UiSize(0, 0);
    }

    private UiSize containerSize(UiHostNode node, UiLayoutSpec layout, Viewport viewport, UiSize available) {
        List<UiHostNode> children = node.children();
        List<UiSize> sizes = new ArrayList<>();
        for (UiHostNode child : children) {
            sizes.add(measure(child, viewport, available));
        }
        boolean horizontal = horizontal(node.primitive(), layout);
        double gap = layout.gap();
        double width = 0;
        double height = 0;
        for (int i = 0; i < sizes.size(); i++) {
            UiSize size = sizes.get(i);
            double margin = parse(children.get(i), viewport).margin();
            if (i > 0) {
                if (horizontal) {
                    width += gap;
                } else {
                    height += gap;
                }
            }
            if (horizontal) {
                width += size.width() + 2 * margin;
                height = Math.max(height, size.height() + 2 * margin);
            } else {
                height += size.height() + 2 * margin;
                width = Math.max(width, size.width() + 2 * margin);
            }
        }
        return new UiSize(width, height);
    }

    // ---- arrange 辅助 ----

    private void arrangeLinear(List<UiHostNode> children, List<UiSize> sizes, UiLayoutSpec layout,
                               boolean horizontal, double x, double y, double width, double height,
                               UiRect clip, Viewport viewport) {
        double gap = layout.gap();
        double totalGrow = 0;
        for (UiHostNode child : children) {
            totalGrow += Math.max(0, parse(child, viewport).grow());
        }
        double fixedMain = 0;
        for (int i = 0; i < children.size(); i++) {
            UiSize size = sizes.get(i);
            fixedMain += horizontal ? size.width() : size.height();
            if (i > 0) fixedMain += gap;
        }
        double mainExtent = horizontal ? width : height;
        double free = Math.max(0, mainExtent - fixedMain);
        double growUnit = totalGrow > 0 ? free / totalGrow : 0;

        double cursor = horizontal ? x : y;
        double totalMain = 0;
        for (int i = 0; i < children.size(); i++) {
            UiHostNode child = children.get(i);
            UiSize size = sizes.get(i);
            UiLayoutSpec childLayout = parse(child, viewport);
            double mainSize = horizontal ? size.width() : size.height();
            double crossSize = horizontal ? size.height() : size.width();
            if (childLayout.grow() > 0) {
                mainSize += childLayout.grow() * growUnit;
            }
            double crossStart = horizontal ? y : x;
            double crossExtent = horizontal ? height : width;
            double offset = crossOffset(layout.align(), crossExtent, crossSize);
            UiRect rect = horizontal
                    ? new UiRect(cursor + childLayout.margin(), crossStart + offset, mainSize, crossSize)
                    : new UiRect(crossStart + offset, cursor + childLayout.margin(), crossSize, mainSize);
            arrange(child, rect, clip, new UiSize(rect.width(), rect.height()), viewport);
            cursor += mainSize + childLayout.margin() * 2 + gap;
            totalMain += mainSize;
        }
        if (layout.justify() == UiAlignment.CENTER || layout.justify() == UiAlignment.END) {
            double slack = Math.max(0, mainExtent - totalMain);
            double shift = layout.justify() == UiAlignment.CENTER ? slack / 2 : slack;
            shiftLinear(children, horizontal, shift);
        }
    }

    private void arrangeStack(List<UiHostNode> children, List<UiSize> sizes, UiLayoutSpec layout,
                              double x, double y, double width, double height, UiRect clip,
                              Viewport viewport) {
        for (int i = 0; i < children.size(); i++) {
            UiHostNode child = children.get(i);
            UiSize size = sizes.get(i);
            UiLayoutSpec childLayout = parse(child, viewport);
            UiDimension childWidth = childLayout.width();
            UiDimension childHeight = childLayout.height();
            double w = childWidth.kind() == UiDimension.Kind.FILL ? width : size.width();
            double h = childHeight.kind() == UiDimension.Kind.FILL ? height : size.height();
            double dx = x + horizontalOffset(layout.anchor(), width, w) + childLayout.margin();
            double dy = y + verticalOffset(layout.anchor(), height, h) + childLayout.margin();
            UiRect rect = new UiRect(dx, dy, w, h);
            arrange(child, rect, clip, new UiSize(w, h), viewport);
        }
    }

    private void shiftLinear(List<UiHostNode> children, boolean horizontal, double shift) {
        for (UiHostNode child : children) {
            UiRect rect = child.layout();
            UiRect moved = horizontal
                    ? new UiRect(rect.x() + shift, rect.y(), rect.width(), rect.height())
                    : new UiRect(rect.x(), rect.y() + shift, rect.width(), rect.height());
            adapter.arrange(child, moved);
            shiftDescendants(child, horizontal, shift);
        }
    }

    private void shiftDescendants(UiHostNode node, boolean horizontal, double shift) {
        for (UiHostNode child : node.children()) {
            UiRect rect = child.layout();
            UiRect moved = horizontal
                    ? new UiRect(rect.x() + shift, rect.y(), rect.width(), rect.height())
                    : new UiRect(rect.x(), rect.y() + shift, rect.width(), rect.height());
            adapter.arrange(child, moved);
            shiftDescendants(child, horizontal, shift);
        }
    }

    // ---- 解析辅助 ----

    private static UiLayoutSpec parse(UiHostNode node, Viewport viewport) {
        try {
            return viewport.specOf(node);
        } catch (UiFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    "invalid layout props on " + node.path() + ": " + failure.getMessage(),
                    node.path(), failure);
        }
    }

    /**
     * 主轴方向（AC3）：{@code row} 缺省水平、{@code column} 缺省垂直；{@code direction} 属性或
     * profile 覆盖可以把任一容器切到另一轴，从而在窄屏重排而不复制节点。
     */
    private static boolean horizontal(UiPrimitive primitive, UiLayoutSpec layout) {
        if (layout.direction() != null) {
            return layout.direction() == UiAxis.ROW;
        }
        return primitive == UiPrimitive.ROW;
    }

    private static double crossOffset(UiAlignment align, double extent, double size) {
        return switch (align) {
            case CENTER -> Math.max(0, (extent - size) / 2);
            case END -> Math.max(0, (extent - size));
            default -> 0;
        };
    }

    private static double horizontalOffset(com.tkisor.nekojs.api.ui.UiAnchor anchor, double extent, double size) {
        return switch (anchor) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> Math.max(0, (extent - size) / 2);
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> Math.max(0, extent - size);
            default -> 0;
        };
    }

    private static double verticalOffset(com.tkisor.nekojs.api.ui.UiAnchor anchor, double extent, double size) {
        return switch (anchor) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> Math.max(0, (extent - size) / 2);
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> Math.max(0, extent - size);
            default -> 0;
        };
    }

    /** 文本换行可用宽度：fill → 容器宽；固定值 → 固定宽；其余按容器宽。 */
    private static double resolveWidth(UiDimension dimension, double available, double contentScale) {
        return switch (dimension.kind()) {
            case FILL -> available;
            case PIXELS -> dimension.value();
            case DESIGN -> dimension.value() * contentScale;
            case PERCENT -> available * dimension.value() / 100.0;
            case AUTO -> available;
        };
    }

    /**
     * 解析单轴尺寸：auto → 内容；fill → 可用；pixels → 固定；percent → 可用比例；
     * design → 设计像素 × 缩放系数；再夹 min/max。
     */
    private static double resolve(UiDimension dimension, double min, double max,
                                  double content, double available, double contentScale) {
        double value = switch (dimension.kind()) {
            case AUTO -> content;
            case FILL -> available;
            case PIXELS -> dimension.value();
            case PERCENT -> available * dimension.value() / 100.0;
            case DESIGN -> dimension.value() * contentScale;
        };
        if (min > 0) value = Math.max(min, value);
        if (max > 0) value = Math.min(max, value);
        return Math.max(0, value);
    }
}
