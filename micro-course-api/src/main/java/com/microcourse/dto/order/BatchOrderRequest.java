package com.microcourse.dto.order;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BatchOrderRequest {

    @NotEmpty(message = "课程列表不能为空")
    private List<Long> courseIds;

    private String paymentMethod;

    public BatchOrderRequest() {}

    public List<Long> getCourseIds() { return courseIds; }

    public void setCourseIds(List<Long> courseIds) { this.courseIds = courseIds; }

    public String getPaymentMethod() { return paymentMethod; }

    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}
