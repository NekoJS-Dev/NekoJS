package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.network.ErrorSummaryDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 错误面板的纯数据快照模型。
 *
 * <p>模型只负责“当前快照 + 搜索 + 稳定 ID 选中”这三个概念；列表滚动、展开和渲染状态由 UI 自行持有。
 * 输入中的畸形 DTO 会被跳过，合法 DTO 的字符串字段（特别是 fullDetails）原样保留，不做展示层改写。</p>
 */
public final class ErrorDashboardModel {
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public ErrorDashboardModel() {}

    /** 替换完整错误快照；同一 ID 的选中会转移到新 DTO，选中被过滤或消失时清空。 */
    public void update(List<ErrorSummaryDTO> errors) {
        List<ErrorSummaryDTO> safeErrors = safeErrors(errors);
        this.snapshot = this.snapshot.withErrors(safeErrors);
    }

    /** 设置搜索词；null 与空白都表示不过滤，匹配使用 {@link Locale#ROOT} 的大小写规则。 */
    public void setSearch(String search) {
        this.snapshot = this.snapshot.withSearch(search == null ? "" : search);
    }

    public String search() {
        return snapshot.search();
    }

    /** 选中一个当前可见的稳定 ID。null 清空选中；未知或被过滤的 ID 不改变现有选中。 */
    public void select(String id) {
        this.snapshot = this.snapshot.select(id);
    }

    public List<ErrorSummaryDTO> visibleErrors() {
        return snapshot.visibleErrors();
    }

    /** 返回当前选中的 DTO；没有可见选中时返回 {@code null}。 */
    public ErrorSummaryDTO selectedError() {
        return snapshot.selectedError();
    }

    /** 完整快照中的错误条数；不受搜索过滤影响。 */
    public int totalErrors() {
        return snapshot.errors().size();
    }

    /** 完整快照的总出现次数；使用 long 累加避免多个 int 计数相加溢出。 */
    public long totalOccurrences() {
        long total = 0L;
        for (ErrorSummaryDTO error : snapshot.errors()) {
            total += Math.max(0, error.count());
        }
        return total;
    }

    private static List<ErrorSummaryDTO> safeErrors(List<ErrorSummaryDTO> errors) {
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        List<ErrorSummaryDTO> result = new ArrayList<>(errors.size());
        for (ErrorSummaryDTO error : errors) {
            // id 是面板的稳定选中键；其余字符串为空仍可显示，但 null 会让渲染层产生隐式契约。
            if (error != null && error.id() != null && !error.id().isBlank()
                    && error.path() != null && error.message() != null && error.fullDetails() != null) {
                result.add(error);
            }
        }
        return List.copyOf(result);
    }

    private static boolean matches(ErrorSummaryDTO error, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        return contains(error.id(), normalizedQuery)
                || contains(error.path(), normalizedQuery)
                || contains(error.message(), normalizedQuery)
                || contains(error.fullDetails(), normalizedQuery);
    }

    private static boolean contains(String value, String normalizedQuery) {
        return value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    /** 不可变视图状态；volatile 替换让并发的读者始终看到某一代完整快照。 */
    private record Snapshot(
            List<ErrorSummaryDTO> errors,
            List<ErrorSummaryDTO> visibleErrors,
            String search,
            String selectedId
    ) {
        private static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), "", null);

        private Snapshot {
            Objects.requireNonNull(errors, "errors");
            Objects.requireNonNull(visibleErrors, "visibleErrors");
            Objects.requireNonNull(search, "search");
            errors = List.copyOf(errors);
            visibleErrors = List.copyOf(visibleErrors);
            search = search == null ? "" : search;
        }

        private Snapshot withErrors(List<ErrorSummaryDTO> newErrors) {
            List<ErrorSummaryDTO> newVisible = visibleFor(newErrors, search);
            String newSelectedId = containsId(newVisible, selectedId) ? selectedId : null;
            return new Snapshot(newErrors, newVisible, search, newSelectedId);
        }

        private Snapshot withSearch(String newSearch) {
            List<ErrorSummaryDTO> newVisible = visibleFor(errors, newSearch);
            String newSelectedId = containsId(newVisible, selectedId) ? selectedId : null;
            return new Snapshot(errors, newVisible, newSearch, newSelectedId);
        }

        private Snapshot select(String id) {
            if (id == null) {
                return new Snapshot(errors, visibleErrors, search, null);
            }
            return containsId(visibleErrors, id)
                    ? new Snapshot(errors, visibleErrors, search, id)
                    : this;
        }

        private ErrorSummaryDTO selectedError() {
            if (selectedId == null) {
                return null;
            }
            for (ErrorSummaryDTO error : visibleErrors) {
                if (selectedId.equals(error.id())) {
                    return error;
                }
            }
            return null;
        }

        private static List<ErrorSummaryDTO> visibleFor(List<ErrorSummaryDTO> errors, String search) {
            if (search == null || search.isBlank()) {
                return errors;
            }
            String query = search.toLowerCase(Locale.ROOT);
            List<ErrorSummaryDTO> result = new ArrayList<>();
            for (ErrorSummaryDTO error : errors) {
                if (matches(error, query)) {
                    result.add(error);
                }
            }
            return List.copyOf(result);
        }

        private static boolean containsId(List<ErrorSummaryDTO> errors, String id) {
            if (id == null) {
                return false;
            }
            for (ErrorSummaryDTO error : errors) {
                if (id.equals(error.id())) {
                    return true;
                }
            }
            return false;
        }
    }
}

