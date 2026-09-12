package com.commercehub.backend.shop.controller;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.dto.request.UpdateShopRequest;
import com.commercehub.backend.shop.dto.response.ShopResponse;
import com.commercehub.backend.shop.dto.response.ShopApplicationResponse;
import com.commercehub.backend.shop.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ShopResponse>>> getAllActiveShops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "trusted") String sort) {
        Page<ShopResponse> shops = shopService.getAllActiveShops(
                page,
                size,
                keyword,
                categoryId,
                sort
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(shops)));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ShopApplicationResponse>>> getAllShopsForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ShopApplicationResponse> shops = shopService.getAllShopsForAdmin(page, size);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(shops)));
    }

    @PatchMapping("/admin/{shopId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changeShopStatus(
            @PathVariable Long shopId,
            @RequestParam String status) {
        shopService.changeShopStatus(shopId, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái gian hàng thành công!", null));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<ShopResponse>> getShopBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(shopService.getShopBySlug(slug)));
    }

    @GetMapping("/me/application")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShopApplicationResponse>> getMyShopApplication() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(shopService.getMyShopApplication(currentUserId)));
    }

    /**
     * Chỉ tài khoản có role gốc BUYER được gửi hồ sơ mở shop lần đầu.
     * SELLER đã có shop; ADMIN/SUPER_ADMIN chỉ quản trị và không được bán hàng.
     */
    @PostMapping
    @PreAuthorize("hasRole('BUYER') and !hasAnyRole('SELLER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ShopApplicationResponse>> createShop(@Valid @RequestBody CreateShopRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        ShopApplicationResponse response = shopService.createShop(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Đã gửi yêu cầu đăng ký bán hàng. Vui lòng chờ quản trị viên duyệt!", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ShopResponse>> updateShop(@PathVariable Long id, @Valid @RequestBody UpdateShopRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        ShopResponse response = shopService.updateShop(id, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin gian hàng thành công!", response));
    }
}
