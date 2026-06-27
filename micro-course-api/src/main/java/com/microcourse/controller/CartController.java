package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.entity.CartItem;
import com.microcourse.exception.BusinessException;
import com.microcourse.service.CartService;
import com.microcourse.util.SecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * P2-16: 购物车服务端同步 Controller。
 * 替代纯 localStorage 方案，支持多设备/多标签页同步。
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<CartItem>> getCart() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(cartService.getCart(userId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<CartItem> addItem(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long courseId = ((Number) body.get("courseId")).longValue();
        Integer quantity = body.get("quantity") == null ? 1 : ((Number) body.get("quantity")).intValue();
        return R.ok(cartService.addItem(userId, courseId, quantity));
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public R<CartItem> updateQuantity(@PathVariable Long itemId, @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtil.getCurrentUserId();
        Integer quantity = ((Number) body.get("quantity")).intValue();
        return R.ok(cartService.updateQuantity(userId, itemId, quantity));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> removeItem(@PathVariable Long itemId) {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.removeItem(userId, itemId);
        return R.ok();
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public R<Void> clearCart() {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.clearCart(userId);
        return R.ok();
    }
}
