package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final ProductVariantRepository variantRepository;
    private final InstantOrderService instantOrderService;
    // private final PreOrderService preOrderService; // Mở comment khi làm luồng Pre-Order

    public Long processCheckout(Long buyerId, CheckoutRequest request) {
        // Lấy thông tin Variant để xác định loại giao hàng
        ProductVariant variant = variantRepository.findById(request.getProductVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        String deliveryType = variant.getProduct().getDeliveryType();

        if ("INSTANT".equals(deliveryType)) {
            // Điều hướng sang luồng thanh toán tức thì (Trừ tiền, khóa tài khoản, tạo Order Delivered)
            return instantOrderService.checkoutInstant(buyerId, request);
        } else if ("PRE_ORDER".equals(deliveryType)) {
            // Điều hướng sang luồng đặt trước (Tạo Order Pending, gửi yêu cầu cho Shop duyệt)
            // return preOrderService.checkoutPreOrder(buyerId, request);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION); // Tạm ném lỗi vì chưa làm luồng này
        } else {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
        }
    }
}