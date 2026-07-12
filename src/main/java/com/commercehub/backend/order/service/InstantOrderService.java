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
import com.commercehub.backend.fee.entity.PlatformFeeConfig;
import com.commercehub.backend.fee.entity.PlatformFeeLedger;
import com.commercehub.backend.fee.repository.PlatformFeeConfigRepository;
import com.commercehub.backend.fee.repository.PlatformFeeLedgerRepository;
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
import java.math.RoundingMode;
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

    // Wallet & Fee
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final HoldReleaseRepository holdReleaseRepository;
    private final PlatformFeeConfigRepository feeConfigRepository;
    private final PlatformFeeLedgerRepository feeLedgerRepository;

    @Transactional
    public Long checkoutInstant(Long buyerId, CheckoutRequest request) {

        // 1. Kiểm tra Variant & Product
        ProductVariant variant = variantRepository.findById(request.getProductVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!"INSTANT".equals(variant.getProduct().getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
        }

        Long sellerId = variant.getProduct().getShop().getOwner().getId();
        if (buyerId.equals(sellerId)) {
            throw new AppException(ErrorCode.CANNOT_REVIEW_OWN_PRODUCT); // Không tự mua hàng
        }

        // 2. Khóa dòng Asset bằng SKIP LOCKED (Chống Race Condition)
        List<DigitalAsset> assetsToSell = assetRepository.findAvailableAssetsWithLock(variant.getId(), request.getQuantity());
        if (assetsToSell.size() < request.getQuantity()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION); // Hết hàng
        }

        // 3. Lấy cấu hình phí sàn đang Active
        PlatformFeeConfig activeFeeConfig = feeConfigRepository.findByIsActiveTrue()
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION)); // Chưa cấu hình phí

        // 4. Tính toán tiền bạc & Phí
        BigDecimal quantity = new BigDecimal(request.getQuantity());
        BigDecimal totalAmount = variant.getPrice().multiply(quantity);

        // Tính phí: raw_fee = sale_amount * fee_rate_snapshot
        BigDecimal rawFee = totalAmount.multiply(activeFeeConfig.getFeeRate());
        BigDecimal feeAfterMin = rawFee.max(activeFeeConfig.getMinFeeAmount());

        BigDecimal finalFee;
        if (activeFeeConfig.getMaxFeeAmount() != null) {
            finalFee = feeAfterMin.min(activeFeeConfig.getMaxFeeAmount());
        } else {
            finalFee = feeAfterMin;
        }
        finalFee = finalFee.setScale(0, RoundingMode.HALF_UP);
        BigDecimal sellerNetAmount = totalAmount.subtract(finalFee);

        // 5. Kế toán dòng tiền (Trừ buyer, Hold seller)
        walletService.deductBalance(buyerId, totalAmount, "ORDER_PAYMENT", null, "ORDER");
        walletService.holdForSeller(sellerId, totalAmount, null);

        // 6. Tạo Order (DELIVERED ngay lập tức)
        Order order = Order.builder()
                .orderCode(OrderCodeGenerator.generate())
                .user(variantRepository.findById(buyerId).get().getShop().getOwner()) // Mock lấy user
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

        // 7. Tạo HoldRelease (Tiền bị giữ)
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

        // 8. Tạo Platform Fee Ledger (Sổ cái phí)
        PlatformFeeLedger feeLedger = PlatformFeeLedger.builder()
                .orderItemId(orderItem.getId())
                .orderId(order.getId())
                .shopId(variant.getProduct().getShop().getId())
                .sellerWalletId(sellerWallet.getId())
                .feeConfigId(activeFeeConfig.getId())
                .feeRateSnapshot(activeFeeConfig.getFeeRate())
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

        log.info("🛒 Checkout INSTANT thành công - Order ID: {} | Tổng: {} | Phí sàn: {}", order.getId(), totalAmount, finalFee);

        return order.getId();
    }
}