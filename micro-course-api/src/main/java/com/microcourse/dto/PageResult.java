package com.microcourse.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public class PageResult<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private long totalPages;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setItems(page.getRecords());
        result.setPage((int) page.getCurrent() - 1);  // MyBatis-Plus 1-based → 0-based
        result.setSize((int) page.getSize());
        result.setTotalElements(page.getTotal());
        result.setTotalPages(page.getPages());
        return result;
    }

    /** @param page 0-based（与 MyBatis-Plus PageHelper 一致：0=第1页） */
    public static <T> PageResult<T> of(List<T> items, long totalElements, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.setItems(items);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(totalElements);
        result.setTotalPages(size > 0 ? (int) Math.ceil((double) totalElements / size) : 0);
        return result;
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public long getTotalPages() { return totalPages; }
    public void setItems(List<T> items) { this.items = items; }
    public void setPage(int page) { this.page = page; }
    public void setSize(int size) { this.size = size; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public void setTotalPages(long totalPages) { this.totalPages = totalPages; }
}
