package com.commercehub.backend.dispute.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.dispute.dto.request.SellerRespondRequest;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerDisputeController {

    private final DisputeService disputeService;

    /**
     * Seller bắt đầu bảo hành.
     */
    @PostMapping("/orders/{orderId}/items/{itemId}/warranty-start")
    public ResponseEntity<ApiResponse<DisputeResponse>> startWarranty(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid
            @RequestBody(required = false)
            SellerRespondRequest request
    ) {

        Long sellerId =
                SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                "Đã tiếp nhận bảo hành!",
                disputeService.startWarranty(
                        sellerId,
                        orderId,
                        itemId,
                        request
                )
        ));
    }

    /**
     * Seller hoàn thành bảo hành.
     */
    @PostMapping("/orders/{orderId}/items/{itemId}/warranty-complete")
    public ResponseEntity<ApiResponse<DisputeResponse>> completeWarranty(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid
            @RequestBody(required = false)
            SellerRespondRequest request
    ) {

        Long sellerId =
                SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                "Đã gửi kết quả bảo hành đến buyer!",
                disputeService.completeWarranty(
                        sellerId,
                        orderId,
                        itemId,
                        request
                )
        ));
    }

    /**
     * Seller từ chối/không xử lý được
     * và đưa lên Admin.
     */
    @PostMapping("/orders/{orderId}/items/{itemId}/dispute")
    public ResponseEntity<ApiResponse<DisputeResponse>> escalate(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid
            @RequestBody(required = false)
            SellerRespondRequest request
    ) {

        Long sellerId =
                SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                "Đã chuyển tranh chấp đến quản trị viên!",
                disputeService.escalateBySeller(
                        sellerId,
                        orderId,
                        itemId,
                        request
                )
        ));
    }

    @GetMapping("/disputes")
    public ResponseEntity<ApiResponse<PageResponse<DisputeResponse>>> list(
            Pageable pageable
    ) {

        Long sellerId =
                SecurityUtils.getCurrentUserId();

        Page<DisputeResponse> disputes = disputeService.getSellerDisputes(
                        sellerId,
                        pageable
                );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(disputes)));
    }

    @GetMapping("/disputes/{disputeId}")
    public ResponseEntity<ApiResponse<DisputeResponse>> detail(
            @PathVariable Long disputeId
    ) {

        Long sellerId =
                SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                disputeService.getSellerDispute(
                        sellerId,
                        disputeId
                )
        ));
    }
}
