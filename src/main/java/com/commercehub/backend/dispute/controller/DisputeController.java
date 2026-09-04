package com.commercehub.backend.dispute.controller;

import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.dispute.dto.request.CreateDisputeRequest;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.service.DisputeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class DisputeController {

    private final DisputeService disputeService;
    private final OrderService orderService;

    /**
     * Buyer tạo khiếu nại.
     */
    @PostMapping(
            "/orders/{orderCode}/items/{itemId}/complain"
    )
    public ResponseEntity<DisputeResponse> complain(
            @PathVariable @Size(min = 1, max = 50, message = "Mã đơn hàng không hợp lệ") String orderCode,
            @PathVariable Long itemId,
            @Valid
            @RequestBody CreateDisputeRequest request
    ) {

        Long buyerId =
                SecurityUtils.getCurrentUserId();

        Order order = orderService.getBuyerOrderOrThrow(buyerId, orderCode);

        DisputeResponse response =
                disputeService.createComplaint(
                        buyerId,
                        order.getId(),
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
