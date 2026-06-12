package com.microcourse.dto;

/**
 * 趋势数据点 VO
 */
public class TrendPointVO {

    private String month;
    private Double value;

    public TrendPointVO() {}

    public TrendPointVO(String month, Double value) {
        this.month = month;
        this.value = value;
    }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
}
