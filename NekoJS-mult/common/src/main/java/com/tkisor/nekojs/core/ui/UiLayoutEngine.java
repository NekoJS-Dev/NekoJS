package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiAlignment;
import com.tkisor.nekojs.api.ui.UiDimension;
import com.tkisor.nekojs.api.ui.UiHostAdapter;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiLayoutSpec;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiTextMeasure;
import com.tkisor.nekojs.api.ui.UiTextMeasurer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 布局引擎：measure → arrange（jsx-ui-spec「布局」）。
 *
 * <p>只实现明确的 Minecraft UI 约束：逻辑像素、fill、auto、受控百分比、min/max、
 * margin/padding/gap、主轴与交叉轴对齐、九宫格锚定。不实现 CSS selector、继承、
 * 浏览器 box model 或任意 CSS 字符串。
 *
 * <p>文本尺寸经 {@link UiTextMeasurer}（Client UI Adapter 负责字体访问与换行）；
 * 文本测量只发生在 measure 阶段，正常绘制帧不重新运行脚本。
 */
final class UiLayoutEngine {

    private final UiHostAdapter adapter;
    private final UiTextMeasurer measurer;

    UiLayoutEngine(UiHostAdapter adapter, UiTextMeasurer measurer) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.measurer = measurer == null ? request -> UiSize.zero() : measurer;
    }

    /** 从 root 开始 measure + arrange 整棵树。 */
    void layout(UiHostNode root, UiSize viewport) {
        measure(root, viewport);
        arrange(root, new UiRect(0, 0, viewport.width(), viewport.height()), viewport);
    }

    /** 自底向上测量每个节点的内容尺寸（不含 margin）。 */
    UiSize measure(UiHostNode node, UiSize available) {
        UiProps props = node.props();
        UiLayoutSpec layout;
        try {
            layout = UiLayoutSpec.fromProps(props);
        } catch (com.tkisor.nekojs.api.ui.UiFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new com.tkisor.nekojs.api.ui.UiFailure(
                    com.tkisor.nekojs.api.ui.UiDiagnosticStage.LAYOUT,
                    com.tkisor.nekojs.api.ui.UiFailure.INVALID_LAYOUT,
                    "invalid layout props on " + node.path() + ": " + failure.getMessage(),
                    node.path(), failure);
        }
        double pad = layout.padding();
        UiSize inner = new UiSize(Math.max(0, available.width() - 2 * pad),
                Math.max(0, available.height() - 2 * pad));

        UiSize content;
        if (node.children().isEmpty()) {
            content = leafSize(node, layout, inner);
        } else {
            content = containerSize(node, layout, inner);
        }
        double width = resolve(layout.width(), layout.minWidth(), layout.maxWidth(),
                content.width() + 2 * pad, available.width());
        double height = resolve(layout.height(), layout.minHeight(), layout.maxHeight(),
                content.height() + 2 * pad, available.height());
        return new UiSize(width, height);
    }

    /** 摆放：父节点先定矩形，再按主轴/交叉轴规则摆放子节点。 */
    void arrange(UiHostNode node, UiRect rect, UiSize available) {
        adapter.arrange(node, rect);
        List<UiHostNode> children = node.children();
        if (children.isEmpty()) {
            return;
        }
        UiLayoutSpec layout = UiLayoutSpec.fromProps(node.props());
        double pad = layout.padding();
        double innerX = rect.x() + pad;
        double innerY = rect.y() + pad;
        double innerWidth = Math.max(0, rect.width() - 2 * pad);
        double innerHeight = Math.max(0, rect.height() - 2 * pad);

        List<UiSize> sizes = new ArrayList<>();
        for (UiHostNode child : children) {
            sizes.add(measure(child, new UiSize(innerWidth, innerHeight)));
        }

        UiPrimitive primitive = node.primitive();
        boolean horizontal = primitive == UiPrimitive.ROW;
        switch (primitive) {
            case ROW, COLUMN -> arrangeLinear(children, sizes, layout, horizontal,
                    innerX, innerY, innerWidth, innerHeight);
            case SCREEN, STACK, PANEL, SCROLL -> arrangeStack(children, sizes, layout,
                    innerX, innerY, innerWidth, innerHeight);
            default -> arrangeStack(children, sizes, layout, innerX, innerY, innerWidth, innerHeight);
        }
    }

    // ---- measure 辅助 ----

    private UiSize leafSize(UiHostNode node, UiLayoutSpec layout, UiSize available) {
        if (node.primitive() == UiPrimitive.LABEL) {
            UiProps props = node.props();
            String text = props.string(UiProp.TEXT.jsName(), "");
            double fontSize = props.number(UiProp.FONT_SIZE.jsName(), 8);
            boolean wrap = props.bool(UiProp.WRAP.jsName(), false);
            String font = props.string(UiProp.FONT.jsName());
            double measuredWidth = layout.width().kind() == UiDimension.Kind.FILL
                    ? available.width()
                    : layout.width().kind() == UiDimension.Kind.PIXELS
                            ? layout.width().value() : available.width();
            UiSize measured = measurer.measure(new UiTextMeasure(text, font, fontSize, wrap, measuredWidth));
            return measured;
        }
        if (node.primitive() == UiPrimitive.SPACER || node.primitive() == UiPrimitive.IMAGE) {
            return new UiSize(0, 0);
        }
        return new UiSize(0, 0);
    }

    private UiSize containerSize(UiHostNode node, UiLayoutSpec layout, UiSize available) {
        List<UiHostNode> children = node.children();
        List<UiSize> sizes = new ArrayList<>();
        for (UiHostNode child : children) {
            sizes.add(measure(child, available));
        }
        boolean horizontal = node.primitive() == UiPrimitive.ROW;
        double gap = layout.gap();
        double width = 0;
        double height = 0;
        for (int i = 0; i < sizes.size(); i++) {
            UiSize size = sizes.get(i);
            double margin = UiLayoutSpec.fromProps(children.get(i).props()).margin();
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
                               boolean horizontal, double x, double y, double width, double height) {
        double gap = layout.gap();
        double totalMain = 0;
        double totalGrow = 0;
        for (UiHostNode child : children) {
            totalGrow += Math.max(0, UiLayoutSpec.fromProps(child.props()).grow());
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
        for (int i = 0; i < children.size(); i++) {
            UiHostNode child = children.get(i);
            UiSize size = sizes.get(i);
            UiLayoutSpec childLayout = UiLayoutSpec.fromProps(child.props());
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
            arrange(child, rect, new UiSize(rect.width(), rect.height()));
            cursor += mainSize + childLayout.margin() * 2 + gap;
            totalMain += mainSize;
        }
        if (layout.justify() == UiAlignment.CENTER || layout.justify() == UiAlignment.END) {
            double used = totalMain;
            double slack = Math.max(0, mainExtent - used);
            double shift = layout.justify() == UiAlignment.CENTER ? slack / 2 : slack;
            shiftLinear(children, horizontal, shift);
        }
    }

    private void arrangeStack(List<UiHostNode> children, List<UiSize> sizes, UiLayoutSpec layout,
                              double x, double y, double width, double height) {
        for (int i = 0; i < children.size(); i++) {
            UiHostNode child = children.get(i);
            UiSize size = sizes.get(i);
            UiLayoutSpec childLayout = UiLayoutSpec.fromProps(child.props());
            double w = childLayout.width().kind() == UiDimension.Kind.FILL ? width : size.width();
            double h = childLayout.height().kind() == UiDimension.Kind.FILL ? height : size.height();
            double dx = x + horizontalOffset(layout.anchor(), width, w) + childLayout.margin();
            double dy = y + verticalOffset(layout.anchor(), height, h) + childLayout.margin();
            UiRect rect = new UiRect(dx, dy, w, h);
            arrange(child, rect, new UiSize(w, h));
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

    private static double crossOffset(UiAlignment align, double extent, double size) {
        return switch (align) {
            case CENTER -> Math.max(0, (extent - size) / 2);
            case END -> Math.max(0, extent - size);
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

    /** 解析单轴尺寸：auto → 内容；fill → 可用；pixels → 固定；percent → 可用比例；再夹 min/max。 */
    private static double resolve(UiDimension dimension, double min, double max,
                                  double content, double available) {
        double value = switch (dimension.kind()) {
            case AUTO -> content;
            case FILL -> available;
            case PIXELS -> dimension.value();
            case PERCENT -> available * dimension.value() / 100.0;
        };
        if (min > 0) value = Math.max(min, value);
        if (max > 0) value = Math.min(max, value);
        return Math.max(0, value);
    }
}
