package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.request.CheckoutItemRequest;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.product.entity.DigitalAsset;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.DigitalAssetRepository;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.fee.dto.FeeResult;
import com.commercehub.backend.fee.entity.PlatformFeeLedger;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
import com.commercehub.backend.fee.service.FeeCalculationService;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.service.WalletService;
import com.commercehub.backend.common.util.OrderCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstantOrderService {

    private final ProductVariantRepository variantRepository;
    private final DigitalAssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final OrderStatusService orderStatusService;

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final HoldReleaseRepository holdReleaseRepository;
    private final FeeCalculationService feeCalculationService;
    private final PlatformFeeLedgerRepository feeLedgerRepository;

    @Transactional
    public Long checkoutInstant(Long buyerId, CheckoutRequest request) {


        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Map<Long, CheckoutItemRequest> mergedMap = new LinkedHashMap<>();
        for (CheckoutItemRequest item : request.getItems()) {
            if (mergedMap.containsKey(item.getProductVariantId())) {
                CheckoutItemRequest existing = mergedMap.get(item.getProductVariantId());
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
            } else {
                mergedMap.put(item.getProductVariantId(), item);
            }
        }

        List<CheckoutItemRequest> consolidatedItems = new ArrayList<>(mergedMap.values());

        BigDecimal orderTotalAmount = BigDecimal.ZERO;
        Shop targetShop = null;
        Long sellerId = null;


        List<ProductVariant> processedVariants = new ArrayList<>();
        List<List<DigitalAsset>> allocatedAssetsList = new ArrayList<>();


        //Validate, Khóa kho và Tính tổng tiền

        for (CheckoutItemRequest itemReq : consolidatedItems) {
            ProductVariant variant = variantRepository.findById(itemReq.getProductVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

            if (!"INSTANT".equals(variant.getProduct().getDeliveryType())) {
                throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
            }


            if (targetShop == null) {
                targetShop = variant.getProduct().getShop();
                sellerId = targetShop.getOwner().getId();
            }

            if (buyerId.equals(sellerId)) {
                throw new AppException(ErrorCode.CANNOT_BUY_OWN_PRODUCT);
            }
            if (!"ACTIVE".equals(targetShop.getStatus())) {
                throw new AppException(ErrorCode.SHOP_SUSPENDED);
            }
            if (!"ACTIVE".equals(variant.getProduct().getStatus()) || !"ACTIVE".equals(variant.getStatus())) {
                throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }


            List<DigitalAsset> assetsToSell = assetRepository.findAvailableAssetsWithLock(variant.getId(), itemReq.getQuantity());
            if (assetsToSell.size() < itemReq.getQuantity()) {
                throw new AppException(ErrorCode.OUT_OF_STOCK);
            }


            BigDecimal lineTotal = variant.getPrice().multiply(new BigDecimal(itemReq.getQuantity()));
            orderTotalAmount = orderTotalAmount.add(lineTotal);

            processedVariants.add(variant);
            allocatedAssetsList.add(assetsToSell);
        }



        Order order = Order.builder()
                .orderCode(OrderCodeGenerator.generate(targetShop.getId()))
                .user(buyer)
                .shop(targetShop)
                .deliveryType("INSTANT")
                .subtotalAmount(orderTotalAmount)
                .totalAmount(orderTotalAmount)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "WALLET")
                .paymentStatus("PAID")
                .status("DELIVERED") // Giao ngay thì status là DELIVERED luôn
                .placedAt(OffsetDateTime.now())
                .deliveredAt(OffsetDateTime.now())
                .build();


        order = orderRepository.save(order);


        walletService.deductBalance(buyerId, orderTotalAmount, "ORDER_PAYMENT", order.getId(), "ORDER");

        walletService.holdForSeller(sellerId, orderTotalAmount, order.getId());

        orderStatusService.logStatusChange(order, null, "DELIVERED", buyerId, "Checkout tức thì - giao hàng ngay");

        Wallet sellerWallet = walletRepository.findByUserId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));


        //Lưu OrderItem, Giao Key và Cắt phí sàn
        for (int i = 0; i < consolidatedItems.size(); i++) {
            CheckoutItemRequest itemReq = consolidatedItems.get(i);
            ProductVariant variant = processedVariants.get(i);
            List<DigitalAsset> assetsToSell = allocatedAssetsList.get(i);

            BigDecimal lineTotal = variant.getPrice().multiply(new BigDecimal(itemReq.getQuantity()));

            FeeResult feeResult = feeCalculationService.calculateFee(lineTotal);
            BigDecimal finalFee = feeResult.getFeeAmount();
            BigDecimal sellerNetAmount = feeResult.getSellerNetAmount();

            // Lưu OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productVariant(variant)
                    .productName(variant.getProduct().getName())
                    .variantName(variant.getName())
                    .productType(variant.getProduct().getProductType())
                    .deliveryType("INSTANT")
                    .unitPrice(variant.getPrice())
                    .quantity(itemReq.getQuantity())
                    .lineTotal(lineTotal)
                    .build();
            orderItemRepository.save(orderItem);

            for (DigitalAsset asset : assetsToSell) {
                asset.setStatus("SOLD");
                asset.setOrderItemId(orderItem.getId());
                asset.setIsDelivered(true);
                asset.setDeliveredAt(OffsetDateTime.now());
            }
            assetRepository.saveAll(assetsToSell);

            variant.setStockCount(variant.getStockCount() - itemReq.getQuantity());
            variantRepository.save(variant);

            // Tạo lịch nhả tiền
            HoldRelease holdRelease = HoldRelease.builder()
                    .wallet(sellerWallet)
                    .orderItemId(orderItem.getId())
                    .holdAmount(lineTotal)
                    .feeAmount(finalFee)
                    .sellerNetAmount(sellerNetAmount)
                    .status("HOLDING")
                    .scheduledReleaseAt(OffsetDateTime.now().plusDays(7)) // 7 ngày sau nhả tiền
                    .build();
            holdRelease = holdReleaseRepository.save(holdRelease);


            PlatformFeeLedger feeLedger = PlatformFeeLedger.builder()
                    .orderItemId(orderItem.getId())
                    .orderId(order.getId())
                    .shopId(targetShop.getId())
                    .sellerWalletId(sellerWallet.getId())
                    .feeConfigId(feeResult.getFeeConfigId())
                    .feeRateSnapshot(feeResult.getFeeRateSnapshot())
                    .saleAmount(lineTotal)
                    .feeAmount(finalFee)
                    .sellerNetAmount(sellerNetAmount)
                    .status("PENDING")
                    .feeIncurredAt(OffsetDateTime.now())
                    .holdReleaseId(holdRelease.getId())
                    .build();
            feeLedger = feeLedgerRepository.save(feeLedger);

            holdRelease.setFeeLedgerId(feeLedger.getId());
            holdReleaseRepository.save(holdRelease);
        }

        log.info(" Checkout INSTANT thành công - Order ID: {} | Tổng: {}", order.getId(), orderTotalAmount);

        return order.getId();
    }
}