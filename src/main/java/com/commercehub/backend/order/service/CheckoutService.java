package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.request.CheckoutItemRequest;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final ProductVariantRepository variantRepository;
    private final InstantOrderService instantOrderService;
    private final PreOrderService preOrderService;


    @Transactional
    public List<Long> processCheckout(Long buyerId, CheckoutRequest request) {

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Map<String, List<CheckoutItemRequest>> groupedItems = new HashMap<>();


        for (CheckoutItemRequest item : request.getItems()) {
            ProductVariant variant = variantRepository.findById(item.getProductVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

            Long shopId = variant.getProduct().getShop().getId();
            String deliveryType = variant.getProduct().getDeliveryType();
            String groupKey = shopId + "_" + deliveryType;

            groupedItems.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(item);
        }

        List<Long> createdOrderIds = new ArrayList<>();


        for (Map.Entry<String, List<CheckoutItemRequest>> entry : groupedItems.entrySet()) {
            String groupKey = entry.getKey();
            List<CheckoutItemRequest> itemsInGroup = entry.getValue();

            CheckoutRequest subRequest = new CheckoutRequest();
            subRequest.setItems(itemsInGroup);
            subRequest.setPaymentMethod(request.getPaymentMethod());

            if (groupKey.endsWith("INSTANT")) {
                Long orderId = instantOrderService.checkoutInstant(buyerId, subRequest);
                createdOrderIds.add(orderId);
            } else if (groupKey.endsWith("PRE_ORDER")) {
                Long orderId = preOrderService.checkoutPreOrder(buyerId, subRequest);
                createdOrderIds.add(orderId);
            } else {
                throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
            }
        }

        log.info(" Đã xử lý Checkout tổng. Phân tách thành {} đơn hàng: {}", createdOrderIds.size(), createdOrderIds);
        return createdOrderIds;
    }
}