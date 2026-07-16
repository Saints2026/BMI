package com.bmi.model.db;

import java.util.List;

/**
 * 统一分页结果封装类。
 * <p>
 * 包含当前页数据、总记录数、页码、每页大小和总页数。
 * 总页数由构造方法根据 {@code total / pageSize} 自动计算。
 * 仅提供 getter，不提供 setter（不可变对象）。
 *
 * @param <T> 数据类型
 */
public class PageResult<T> {

    private final List<T> data;
    private final long total;
    private final int currentPage;
    private final int pageSize;
    private final int totalPages;

    /**
     * 全参构造方法。
     *
     * @param data        当前页数据
     * @param total       总记录数
     * @param currentPage 当前页码（从 1 开始）
     * @param pageSize    每页大小
     */
    public PageResult(List<T> data, long total, int currentPage, int pageSize) {
        this.data = data;
        this.total = total;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }

    public List<T> getData() {
        return data;
    }

    public long getTotal() {
        return total;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
