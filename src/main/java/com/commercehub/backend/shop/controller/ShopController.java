package com.commercehub.backend.shop.controller;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.dto.request.UpdateShopRequest;
import com.commercehub.backend.shop.dto.response.ShopResponse;
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
    public ResponseEntity<ApiResponse<Page<ShopResponse>>> getAllActiveShops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(shopService.getAllActiveShops(page, size)));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ShopResponse>>> getAllShopsForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(shopService.getAllShopsForAdmin(page, size)));
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

    /**
     * Mọi role có quyền BUYER đều có thể gửi hồ sơ mở shop lần đầu.
     * Shop được tạo ở trạng thái PENDING; chỉ khi admin duyệt ACTIVE thì
     * BUYER mới được chuyển thành SELLER. ADMIN/SUPER_ADMIN giữ nguyên role.
     */
    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<ShopResponse>> createShop(@Valid @RequestBody CreateShopRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        ShopResponse response = shopService.createShop(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo gian hàng thành công!", response));
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
