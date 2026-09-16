package com.commercehub.backend.voucher.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.voucher.dto.request.VoucherRequest;
import com.commercehub.backend.voucher.dto.response.VoucherResponse;
import com.commercehub.backend.voucher.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerVoucherController {
    private final VoucherService voucherService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getSellerVouchers(user.getId(), keyword, page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Tạo mã giảm giá thành công",
                        voucherService.create(user.getId(), request)));
    }

    @PutMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long voucherId,
            @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật mã giảm giá thành công",
                voucherService.update(user.getId(), voucherId, request)));
    }

    @PatchMapping("/{voucherId}/active")
    public ResponseEntity<ApiResponse<VoucherResponse>> setActive(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long voucherId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(active ? "Đã kích hoạt mã giảm giá" : "Đã tạm dừng mã giảm giá",
                voucherService.setActive(user.getId(), voucherId, active)));
    }
}
