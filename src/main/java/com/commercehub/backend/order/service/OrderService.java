package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.response.OrderDetailResponse;
import com.commercehub.backend.order.dto.response.OrderItemResponse;
import com.commercehub.backend.order.dto.response.OrderResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.wallet.service.HoldReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusService orderStatusService;
    private final HoldReleaseService holdReleaseService;

    @Transactional(readOnly = true)
    public Page<OrderResponse> getBuyerOrders(Long buyerId, Pageable pageable) {
        return orderRepository.findByUserId(buyerId, pageable).map(this::mapToOrderResponse);
    }

    /**
     * Chỉ verify buyer sở hữu order, không map response — dùng cho ownership check nhẹ.
     */
    @Transactional(readOnly = true)
    public void assertBuyerOwnsOrder(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
        if (!order.getUser().getId().equals(buyerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getBuyerOrderDetail(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getUser().getId().equals(buyerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return mapToOrderDetailResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getSellerOrders(Long shopId, Pageable pageable) {
        return orderRepository.findByShopId(shopId, pageable).map(this::mapToOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getSellerOrderDetail(Long shopId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getShop().getId().equals(shopId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return mapToOrderDetailResponse(order);
    }

    // Hành động Buyer xác nhận đã nhận hàng (Tùy chọn)
    @Transactional
    public void confirmReceipt(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getUser().getId().equals(buyerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!"DELIVERED".equals(order.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        String oldStatus = order.getStatus();
        order.setStatus("COMPLETED");
        orderRepository.save(order);

        orderStatusService.logStatusChange(order, oldStatus, "COMPLETED", buyerId, "Người mua xác nhận đã nhận hàng");

        // Nhả tiền sớm cho Seller (không cần đợi 7 ngày)
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            holdReleaseService.earlyReleaseByOrderItemId(item.getId());
        }
        log.info("Buyer {} đã confirm đơn hàng {} - Nhả tiền sớm cho seller.", buyerId, orderId);
    }

    // --- Các hàm Mapping nội bộ ---
    private OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .shopId(order.getShop().getId())
                .shopName(order.getShop().getName())
                .deliveryType(order.getDeliveryType())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalAmount(order.getTotalAmount())
                .placedAt(order.getPlacedAt())
                .build();
    }

    private OrderDetailResponse mapToOrderDetailResponse(Order order) {
        var items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productName(item.getProductName())
                        .variantName(item.getVariantName())
                        .productType(item.getProductType())
                        .deliveryType(item.getDeliveryType())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        return OrderDetailResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .shopId(order.getShop().getId())
                .shopName(order.getShop().getName())
                .deliveryType(order.getDeliveryType())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .subtotalAmount(order.getSubtotalAmount())
                .voucherDiscount(order.getVoucherDiscount())
                .totalAmount(order.getTotalAmount())
                .placedAt(order.getPlacedAt())
                .deliveredAt(order.getDeliveredAt())
                .items(items)
                .build();
    }
}