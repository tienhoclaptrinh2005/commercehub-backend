package com.commercehub.backend.dispute.controller;

import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.dispute.dto.request.CreateDisputeRequest;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * Buyer tạo khiếu nại.
     */
    @PostMapping(
            "/orders/{orderId}/items/{itemId}/complain"
    )
    public ResponseEntity<DisputeResponse> complain(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid
            @RequestBody CreateDisputeRequest request
    ) {

        Long buyerId =
                SecurityUtils.getCurrentUserId();

        DisputeResponse response =
                disputeService.createComplaint(
                        buyerId,
                        orderId,
                        itemId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Buyer không đồng ý hướng xử lý,
     * đưa dispute lên Admin.
     */
    @PostMapping(
            "/disputes/{disputeId}/escalate"
    )
    public ResponseEntity<DisputeResponse> escalate(
            @PathVariable Long disputeId
    ) {

        Long buyerId =
                SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(
                disputeService.escalateByBuyer(
                        buyerId,
                        disputeId
                )
        );
    }

    /** Buyer xác nhận kết quả bảo hành và cho phép đồng hồ T+7 chạy tiếp. */
    @PostMapping("/disputes/{disputeId}/confirm-warranty")
    public ResponseEntity<DisputeResponse> confirmWarranty(
            @PathVariable Long disputeId
    ) {
        Long buyerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                disputeService.confirmWarrantyByBuyer(buyerId, disputeId)
        );
    }

    /** Buyer tự hủy khiếu nại; hồ sơ này không thể mở lại. */
    @PostMapping("/disputes/{disputeId}/withdraw")
    public ResponseEntity<DisputeResponse> withdraw(
            @PathVariable Long disputeId
    ) {
        Long buyerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                disputeService.withdrawByBuyer(buyerId, disputeId)
        );
    }

    @GetMapping("/disputes/{disputeId}")
    public ResponseEntity<DisputeResponse> detail(
            @PathVariable Long disputeId
    ) {

        Long buyerId =
                SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(
                disputeService.getBuyerDispute(
                        buyerId,
                        disputeId
                )
        );
    }

    @GetMapping("/disputes")
    public ResponseEntity<Page<DisputeResponse>> list(
            Pageable pageable
    ) {

        Long buyerId =
                SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(
                disputeService.getBuyerDisputes(
                        buyerId,
                        pageable
                )
        );
    }
}
