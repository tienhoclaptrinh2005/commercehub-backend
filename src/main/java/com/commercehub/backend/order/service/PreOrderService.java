package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.request.CheckoutItemRequest;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.entity.PreOrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.repository.PreOrderItemRepository;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.service.WalletService;
import com.commercehub.backend.common.util.OrderCodeGenerator;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreOrderService {

    private final ProductVariantRepository variantRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PreOrderItemRepository preOrderItemRepository;
    private final UserRepository userRepository;
    private final OrderStatusService orderStatusService;

    // Inject WalletService để trừ tiền ngay lúc đặt
    private final WalletService walletService;

    @Transactional
    public Long checkoutPreOrder(Long buyerId, CheckoutRequest request) {

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Shop targetShop = null;
        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        List<ItemProcessContext> processedItems = new ArrayList<>();

        // 1. Lọc và Validate
        for (CheckoutItemRequest itemReq : request.getItems()) {
            ProductVariant variant = variantRepository.findById(itemReq.getProductVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

            if (!"PRE_ORDER".equals(variant.getProduct().getDeliveryType())) {
                throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
            }
            if (!"ACTIVE".equals(variant.getStatus()) || !"ACTIVE".equals(variant.getProduct().getStatus())) {
                throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }

            Shop currentShop = variant.getProduct().getShop();
            if (buyerId.equals(currentShop.getOwner().getId())) {
                throw new AppException(ErrorCode.CANNOT_BUY_OWN_PRODUCT);
            }
            if (targetShop == null) {
                targetShop = currentShop;
                if (!"ACTIVE".equals(targetShop.getStatus())) {
                    throw new AppException(ErrorCode.SHOP_SUSPENDED);
                }
            } else if (!targetShop.getId().equals(currentShop.getId())) {
                throw new AppException(ErrorCode.ITEMS_MUST_BE_SAME_SHOP);
            }

            BigDecimal itemQuantity = new BigDecimal(itemReq.getQuantity());
            BigDecimal itemSubtotal = variant.getPrice().multiply(itemQuantity);

            totalOrderAmount = totalOrderAmount.add(itemSubtotal);

            // ĐÃ SỬA: Lấy dữ liệu thật từ request thay vì gán cứng null
            String buyerInputs = itemReq.getBuyerInputs();

            processedItems.add(new ItemProcessContext(variant, itemReq.getQuantity(), itemSubtotal, buyerInputs));
        }

        Long sellerId = targetShop.getOwner().getId();

        // 2. Tạo Order với trạng thái WAITING_APPROVAL và PAID
        Order order = Order.builder()
                .orderCode(OrderCodeGenerator.generate(targetShop.getId()))
                .user(buyer)
                .shop(targetShop)
                .deliveryType("PRE_ORDER")
                .subtotalAmount(totalOrderAmount)
                .totalAmount(totalOrderAmount)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "WALLET")
                .paymentStatus("PAID")
                .status("WAITING_APPROVAL")
                .placedAt(OffsetDateTime.now())
                .approvalDeadlineAt(OffsetDateTime.now().plusHours(48))
                .build();

        order = orderRepository.save(order);

        // =========================================================================
        // TRỪ TIỀN NGAY THEO LOGIC MỚI
        // ĐÃ FIX Bug #24: Truyền order.getId() làm refId thay vì null
        // =========================================================================
        walletService.deductBalance(buyerId, totalOrderAmount, "ORDER_PAYMENT", order.getId(), "ORDER_PRE");
        walletService.holdForSeller(sellerId, totalOrderAmount, order.getId());
        // =========================================================================

        orderStatusService.logStatusChange(
                order, null, "WAITING_APPROVAL", buyerId, "Đã thanh toán và đặt hàng thành công, chờ Shop duyệt"
        );

        // 3. Tạo OrderItem và PreOrderItem
        for (ItemProcessContext ctx : processedItems) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productVariant(ctx.getVariant())
                    .productName(ctx.getVariant().getProduct().getName())
                    .variantName(ctx.getVariant().getName())
                    .productType(ctx.getVariant().getProduct().getProductType())
                    .deliveryType("PRE_ORDER")
                    .unitPrice(ctx.getVariant().getPrice())
                    .quantity(ctx.getQuantity())
                    .lineTotal(ctx.getItemSubtotal())
                    .build();
            orderItem = orderItemRepository.save(orderItem);

            PreOrderItem preOrderItem = PreOrderItem.builder()
                    .orderItem(orderItem)
                    .buyerInputs(ctx.getBuyerInputs()) // Giờ thì dữ liệu sẽ được lưu trọn vẹn
                    .build();
            preOrderItemRepository.save(preOrderItem);
        }

        log.info("⏳ Checkout PRE-ORDER hoàn tất. Đã trừ tiền người mua. Đơn hàng {} chờ shop duyệt.", order.getId());
        return order.getId();
    }

    @Data
    @Builder
    private static class ItemProcessContext {
        private final ProductVariant variant;
        private final Integer quantity;
        private final BigDecimal itemSubtotal;
        private final String buyerInputs;
    }
}