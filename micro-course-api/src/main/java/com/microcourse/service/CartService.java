package com.microcourse.service;

import com.microcourse.entity.CartItem;

import java.util.List;

public interface CartService {
    List<CartItem> getCart(Long userId);
    CartItem addItem(Long userId, Long courseId, Integer quantity);
    CartItem updateQuantity(Long userId, Long cartItemId, Integer quantity);
    void removeItem(Long userId, Long cartItemId);
    void clearCart(Long userId);
}
