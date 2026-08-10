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
import com.commercehub.backend.product.service.AssetDeliveryService;
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

/**
 * Checkout INSTANT: giao hàng ngay, tiền buyer bị trừ và hold vào ví seller.
 *
 * Mô hình tiền PER-ITEM (mỗi OrderItem có đúng 1 HoldRelease + 1 FeeLedger):
 *   - lineTotal = feeAmount + sellerNetAmount (bất biến từng dòng)
 *   - Tổng hold của order = tổng lineTotal = totalAmount buyer đã trả
 *   → Khớp sổ 100%: ví buyer + hold seller + phí sàn = tiền nạp ban đầu.
 */
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
    private final HoldReleaseRepository holdReleaseRepository;
    private final FeeCalculationService feeCalculationService;
    private final PlatformFeeLedgerRepository feeLedgerRepository;
    private final AssetDeliveryService assetDeliveryService;

    @Transactional
    public Long checkoutInstant(Long buyerId, CheckoutRequest request) {

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        // Gộp các dòng trùng variant
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

        Shop targetShop = null;
        Long sellerId = null;

        List<ProductVariant> processedVariants = new ArrayList<>();
        List<List<DigitalAsset>> allocatedAssetsList = new ArrayList<>();

        BigDecimal orderTotalAmount = BigDecimal.ZERO;

        // ================= 1. VALIDATE + KHÓA KHO =================
        for (CheckoutItemRequest itemReq : consolidatedItems) {
            ProductVariant variant = variantRepository.findById(itemReq.getProductVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

            if (!"INSTANT".equals(variant.getProduct().getDeliveryType())) {
                throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
            }

            Shop currentShop = variant.getProduct().getShop();
            if (targetShop == null) {
                targetShop = currentShop;
                sellerId = targetShop.getOwner().getId();
            } else if (!targetShop.getId().equals(currentShop.getId())) {
                // 1 order chỉ thuộc 1 shop — chặn trộn sản phẩm nhiều shop
                throw new AppException(ErrorCode.ITEMS_MUST_BE_SAME_SHOP);
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

        // ================= 2. TẠO ORDER =================
        // Thanh toán hiện tại luôn qua VÍ (nạp trước - mua sau).
        Order order = Order.builder()
                .orderCode(OrderCodeGenerator.generate(targetShop.getId()))
                .user(buyer)
                .shop(targetShop)
                .deliveryType("INSTANT")
                .subtotalAmount(orderTotalAmount)
                .totalAmount(orderTotalAmount)
                .paymentMethod("WALLET")
                .paymentStatus("PAID")
                .status("DELIVERED")
                .placedAt(OffsetDateTime.now())
                .deliveredAt(OffsetDateTime.now())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        order = orderRepository.save(order);

        // ================= 3. LUÂN CHUYỂN TIỀN =================
        walletService.deductBalance(buyerId, orderTotalAmount, "ORDER_PAYMENT", order.getId(), "ORDER");
        Wallet sellerWallet = walletService.holdForSeller(sellerId, orderTotalAmount, order.getId());

        orderStatusService.logStatusChange(order, null, "DELIVERED", buyerId, "Checkout tức thì - giao hàng ngay");

        // ================= 4. TẠO ITEM + HOLD + LEDGER PER-ITEM =================
        OffsetDateTime scheduledReleaseAt = OffsetDateTime.now().plusDays(7);

        for (int i = 0; i < consolidatedItems.size(); i++) {
            CheckoutItemRequest itemReq = consolidatedItems.get(i);
            ProductVariant variant = processedVariants.get(i);
            List<DigitalAsset> assetsToSell = allocatedAssetsList.get(i);

            BigDecimal lineTotal = variant.getPrice().multiply(new BigDecimal(itemReq.getQuantity()));

            // Tính phí sàn cho dòng này (snapshot tại thời điểm thanh toán)
            FeeResult feeResult = feeCalculationService.calculateFee(lineTotal);

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
                    .feeConfigId(feeResult.getFeeConfigId())
                    .feeRateSnapshot(feeResult.getFeeRateSnapshot())
                    .feeAmount(feeResult.getFeeAmount())
                    .sellerNetAmount(feeResult.getSellerNetAmount())
                    .build();
            orderItem = orderItemRepository.save(orderItem);

            // 1 OrderItem ↔ 1 HoldRelease: khiếu nại/hoàn tiền được xử lý theo từng dòng
            HoldRelease holdRelease = HoldRelease.builder()
                    .wallet(sellerWallet)
                    .orderId(order.getId())
                    .orderItemId(orderItem.getId())
                    .holdAmount(lineTotal)
                    .feeAmount(feeResult.getFeeAmount())
                    .sellerNetAmount(feeResult.getSellerNetAmount())
                    .status("HOLDING")
                    .scheduledReleaseAt(scheduledReleaseAt)
                    .build();
            holdRelease = holdReleaseRepository.save(holdRelease);

            // 1 OrderItem ↔ 1 FeeLedger
            PlatformFeeLedger feeLedger = PlatformFeeLedger.builder()
                    .orderId(order.getId())
                    .orderItemId(orderItem.getId())
                    .shopId(targetShop.getId())
                    .sellerWalletId(sellerWallet.getId())
                    .feeConfigId(feeResult.getFeeConfigId())
                    .feeRateSnapshot(feeResult.getFeeRateSnapshot())
                    .saleAmount(lineTotal)
                    .feeAmount(feeResult.getFeeAmount())
                    .sellerNetAmount(feeResult.getSellerNetAmount())
                    .status("PENDING")
                    .feeIncurredAt(OffsetDateTime.now())
                    .holdReleaseId(holdRelease.getId())
                    .build();
            feeLedger = feeLedgerRepository.save(feeLedger);

            holdRelease.setFeeLedgerId(feeLedger.getId());
            holdReleaseRepository.save(holdRelease);

            // Giao hàng: đánh dấu asset đã bán + SNAPSHOT nguyên văn nội dung vào
            // asset_delivery_logs — buyer xem lại đơn đọc từ snapshot, không đọc lại kho
            for (DigitalAsset asset : assetsToSell) {
                asset.setStatus("SOLD");
                asset.setOrderItemId(orderItem.getId());
                asset.setIsDelivered(true);
                asset.setDeliveredAt(OffsetDateTime.now());
            }
            assetRepository.saveAll(assetsToSell);

            for (DigitalAsset asset : assetsToSell) {
                assetDeliveryService.logDelivery(asset, orderItem.getId(), buyer, "AUTO");
            }

            variant.setStockCount(variant.getStockCount() - itemReq.getQuantity());
            variantRepository.save(variant);
        }

        log.info("Checkout INSTANT thành công - Order ID: {} | Tổng: {}", order.getId(), orderTotalAmount);

        return order.getId();
    }
}
