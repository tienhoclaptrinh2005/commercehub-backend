package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstantOrderService {

    private final ProductVariantRepository variantRepository;
    private final DigitalAssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository; // Đã thêm UserRepository
    private final OrderStatusService orderStatusService;

    // Wallet & Fee
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final HoldReleaseRepository holdReleaseRepository;
    private final FeeCalculationService feeCalculationService;
    private final PlatformFeeLedgerRepository feeLedgerRepository;

    @Transactional
    public Long checkoutInstant(Long buyerId, CheckoutRequest request) {


        ProductVariant variant = variantRepository.findById(request.getProductVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"INSTANT".equals(variant.getProduct().getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
        }

        Long sellerId = variant.getProduct().getShop().getOwner().getId();
        if (buyerId.equals(sellerId)) {
            throw new AppException(ErrorCode.CANNOT_BUY_OWN_PRODUCT);
        }

        if (!"ACTIVE".equals(variant.getProduct().getShop().getStatus())) {
            throw new AppException(ErrorCode.SHOP_SUSPENDED);
        }

        if (!"ACTIVE".equals(variant.getProduct().getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }

        if (!"ACTIVE".equals(variant.getStatus())) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));


        List<DigitalAsset> assetsToSell = assetRepository.findAvailableAssetsWithLock(variant.getId(), request.getQuantity());
        if (assetsToSell.size() < request.getQuantity()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION); // Hết hàng
        }

        BigDecimal quantity = new BigDecimal(request.getQuantity());
        BigDecimal totalAmount = variant.getPrice().multiply(quantity);
        FeeResult feeResult = feeCalculationService.calculateFee(totalAmount);

        BigDecimal finalFee = feeResult.getFeeAmount();
        BigDecimal sellerNetAmount = feeResult.getSellerNetAmount();

        if (totalAmount.compareTo(finalFee.add(sellerNetAmount)) != 0) {
            log.error("Fee invariant violated! total={}, fee={}, net={}", totalAmount, finalFee, sellerNetAmount);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }


        walletService.deductBalance(buyerId, totalAmount, "ORDER_PAYMENT", null, "ORDER");
        walletService.holdForSeller(sellerId, totalAmount, null);

        Order order = Order.builder()
                .orderCode(OrderCodeGenerator.generate(variant.getProduct().getShop().getId()))
                .user(buyer)
                .shop(variant.getProduct().getShop())
                .deliveryType("INSTANT")
                .subtotalAmount(totalAmount)
                .totalAmount(totalAmount)
                .paymentMethod("WALLET")
                .paymentStatus("PAID")
                .status("DELIVERED")
                .placedAt(OffsetDateTime.now())
                .deliveredAt(OffsetDateTime.now())
                .build();
        orderRepository.save(order);

        orderStatusService.logStatusChange(
                order,
                null,
                "DELIVERED",
                buyerId,
                "Checkout tức thì - giao hàng ngay"
        );

        // Tạo OrderItem
        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .productVariant(variant)
                .productName(variant.getProduct().getName())
                .variantName(variant.getName())
                .productType(variant.getProduct().getProductType())
                .deliveryType("INSTANT")
                .unitPrice(variant.getPrice())
                .quantity(request.getQuantity())
                .lineTotal(totalAmount)
                .build();
        orderItemRepository.save(orderItem);

        // Cập nhật Asset -> SOLD
        for (DigitalAsset asset : assetsToSell) {
            asset.setStatus("SOLD");
            asset.setOrderItemId(orderItem.getId());
            asset.setIsDelivered(true);
            asset.setDeliveredAt(OffsetDateTime.now());
        }
        assetRepository.saveAll(assetsToSell);

        // HoldRelease (Tiền bị giữ)
        Wallet sellerWallet = walletRepository.findByUserId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        HoldRelease holdRelease = HoldRelease.builder()
                .wallet(sellerWallet)
                .orderItemId(orderItem.getId())
                .holdAmount(totalAmount)
                .feeAmount(finalFee)
                .sellerNetAmount(sellerNetAmount)
                .status("HOLDING")
                .scheduledReleaseAt(OffsetDateTime.now().plusDays(7)) // 7 ngày sau nhả tiền
                .build();
        holdRelease = holdReleaseRepository.save(holdRelease);

        // Tạo Platform Fee Ledger (Sổ cái phí)
        PlatformFeeLedger feeLedger = PlatformFeeLedger.builder()
                .orderItemId(orderItem.getId())
                .orderId(order.getId())
                .shopId(variant.getProduct().getShop().getId())
                .sellerWalletId(sellerWallet.getId())
                .feeConfigId(feeResult.getFeeConfigId())
                .feeRateSnapshot(feeResult.getFeeRateSnapshot())
                .saleAmount(totalAmount)
                .feeAmount(finalFee)
                .sellerNetAmount(sellerNetAmount)
                .status("PENDING")
                .feeIncurredAt(OffsetDateTime.now())
                .holdReleaseId(holdRelease.getId()) // Lưu ID hold release
                .build();
        feeLedger = feeLedgerRepository.save(feeLedger);

        // Cập nhật ngược Fee Ledger ID vào Hold Release (Circular reference)
        holdRelease.setFeeLedgerId(feeLedger.getId());
        holdReleaseRepository.save(holdRelease);

        log.info(" Checkout INSTANT thành công - Order ID: {} | Tổng: {} | Phí sàn: {}", order.getId(), totalAmount, finalFee);

        return order.getId();
    }
}