package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.order.OrderVO;

public interface OrderQueryService {

    OrderVO getOrder(Long orderId);

    PageResult<OrderVO> getMyOrders(Long userId, int page, int size, Long courseId, String status);

    /**
     * VO 转换（单订单，含 courseTitle/bundleTitle 实时查询）
     */
    OrderVO toVO(com.microcourse.entity.Order order);
}
