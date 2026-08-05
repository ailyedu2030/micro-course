package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.order.OrderVO;

public interface OrderQueryService {

    OrderVO getOrder(Long orderId);

    PageResult<OrderVO> getMyOrders(Long userId, int page, int size, Long courseId, String status);

    /**
     * B14.4 管理端订单明细（营收看板下钻）。
     *
     * @param teacherId 可选，仅返回该教师课程相关的订单
     */
    java.util.List<OrderVO> adminListOrders(Long teacherId);

    /**
     * VO 转换（单订单，含 courseTitle/bundleTitle 实时查询）
     */
    OrderVO toVO(com.microcourse.entity.Order order);
}
