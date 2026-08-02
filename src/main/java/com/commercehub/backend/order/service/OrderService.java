package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.response.OrderDetailResponse;
import com.commercehub.backend.order.dto.response.OrderItemResponse;
import com.commercehub.backend.order.dto.response.OrderResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.mapper.OrderMapper;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;

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
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public Page<OrderResponse> getBuyerOrders(Long buyerId, Pageable pageable) {
        return orderRepository.findByUserId(buyerId, pageable).map(orderMapper::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public void assertBuyerOwnsOrder(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
        if (!order.getUser().getId().equals(buyerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    @Transactional(readOnly = true)
    public Order getBuyerOrderOrThrow(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
        if (!order.getUser().getId().equals(buyerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return order;
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getBuyerOrderDetail(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getUser().getId().equals(buyerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return buildOrderDetail(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getSellerOrders(Long shopId, Pageable pageable) {
        return orderRepository.findByShopId(shopId, pageable).map(orderMapper::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getSellerOrderDetail(Long shopId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!order.getShop().getId().equals(shopId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return buildOrderDetail(order);
    }



    private OrderDetailResponse buildOrderDetail(Order order) {
        var items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(orderMapper::toOrderItemResponse)
                .collect(Collectors.toList());

        OrderDetailResponse response = orderMapper.toOrderDetailResponse(order);
        response.setItems(items);

        return response;
    }
}