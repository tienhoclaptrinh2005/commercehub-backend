package com.commercehub.backend.voucher.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.voucher.dto.request.VoucherPreviewRequest;
import com.commercehub.backend.voucher.dto.response.VoucherPreviewResponse;
import com.commercehub.backend.voucher.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {
    private final VoucherService voucherService;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<VoucherPreviewResponse>> preview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody VoucherPreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Áp dụng mã giảm giá thành công",
                voucherService.preview(user.getId(), request)));
    }
}
