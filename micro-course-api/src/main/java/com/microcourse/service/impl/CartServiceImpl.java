package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.CartItem;
import com.microcourse.entity.Course;
import com.microcourse.enums.CourseStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CartItemRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final CourseRepository courseRepository;

    public CartServiceImpl(CartItemRepository cartItemRepository, CourseRepository courseRepository) {
        this.cartItemRepository = cartItemRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public List<CartItem> getCart(Long userId) {
        List<CartItem> items = cartItemRepository.findActiveByUserId(userId);
        if (items.isEmpty()) return items;
        // P1C-017 优化: 批量查询课程状态，消除 N+1
        Set<Long> courseIds = items.stream()
                .map(CartItem::getCourseId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, Course> courseMap = courseRepository.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        for (CartItem item : items) {
            if (item.getCourseId() != null) {
                Course course = courseMap.get(item.getCourseId());
                if (course == null || course.getDeletedAt() != null
                        || course.getStatus() == null
                        || !CourseStatus.fromCode(course.getStatus()).isSelectable()) {
                    item.setItemStatus("INACTIVE");
                } else {
                    item.setItemStatus("ACTIVE");
                }
            }
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartItem addItem(Long userId, Long courseId, Integer quantity) {
        if (quantity == null || quantity < 1) quantity = 1;
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getUserId, userId)
                .eq(CartItem::getCourseId, courseId)
                .isNull(CartItem::getDeletedAt);
        CartItem existing = cartItemRepository.selectOne(wrapper);
        if (existing != null) {
            // CON-003 修复: 使用 MyBatis-Plus 参数化方式（替代 setSql 拼接，避免 SQL 注入风险）
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setUpdatedAt(LocalDateTime.now());
            cartItemRepository.updateById(existing);
            return existing;
        }
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setCourseId(courseId);
        item.setQuantity(quantity);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        cartItemRepository.insert(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CartItem updateQuantity(Long userId, Long cartItemId, Integer quantity) {
        CartItem item = cartItemRepository.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        item.setQuantity(quantity);
        item.setUpdatedAt(LocalDateTime.now());
        cartItemRepository.updateById(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, Long cartItemId) {
        CartItem item = cartItemRepository.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        cartItemRepository.deleteById(cartItemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getUserId, userId).isNull(CartItem::getDeletedAt);
        List<CartItem> items = cartItemRepository.selectList(wrapper);
        for (CartItem item : items) {
            cartItemRepository.deleteById(item.getId());
        }
    }
}
