package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.CartItem;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CartItemRepository;
import com.microcourse.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;

    public CartServiceImpl(CartItemRepository cartItemRepository) {
        this.cartItemRepository = cartItemRepository;
    }

    @Override
    public List<CartItem> getCart(Long userId) {
        return cartItemRepository.findActiveByUserId(userId);
    }

    @Override
    @Transactional
    public CartItem addItem(Long userId, Long courseId, Integer quantity) {
        if (quantity == null || quantity < 1) quantity = 1;
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getUserId, userId)
                .eq(CartItem::getCourseId, courseId)
                .isNull(CartItem::getDeletedAt);
        CartItem existing = cartItemRepository.selectOne(wrapper);
        if (existing != null) {
            // CON-003 修复: 使用原子 SQL 更新数量, 避免 read-modify-write 竞态
            cartItemRepository.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CartItem>()
                            .eq(CartItem::getId, existing.getId())
                            .setSql("quantity = quantity + " + quantity)
                            .set(CartItem::getUpdatedAt, LocalDateTime.now()));
            existing.setQuantity(existing.getQuantity() + quantity);
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
    @Transactional
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
    @Transactional
    public void removeItem(Long userId, Long cartItemId) {
        CartItem item = cartItemRepository.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        cartItemRepository.deleteById(cartItemId);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CartItem::getUserId, userId).isNull(CartItem::getDeletedAt);
        List<CartItem> items = cartItemRepository.selectList(wrapper);
        for (CartItem item : items) {
            cartItemRepository.deleteById(item.getId());
        }
    }
}
