package com.microcourse.controller;

import com.microcourse.dto.CartAddRequest;
import com.microcourse.dto.CartUpdateRequest;
import com.microcourse.dto.R;
import com.microcourse.entity.CartItem;
import com.microcourse.service.CartService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public R<CartItem> addItem(@Valid @RequestBody CartAddRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(cartService.addItem(userId, request.getCourseId(), request.getQuantity()));
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public R<CartItem> updateQuantity(@PathVariable Long itemId, @Valid @RequestBody CartUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(cartService.updateQuantity(userId, itemId, request.getQuantity()));
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
