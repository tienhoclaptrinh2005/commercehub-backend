package com.commercehub.backend.voucher.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.voucher.dto.request.VoucherPreviewRequest;
import com.commercehub.backend.voucher.dto.request.VoucherRequest;
import com.commercehub.backend.voucher.dto.response.VoucherPreviewResponse;
import com.commercehub.backend.voucher.dto.response.VoucherResponse;
import com.commercehub.backend.voucher.entity.*;
import com.commercehub.backend.voucher.repository.VoucherRepository;
import com.commercehub.backend.voucher.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoucherService {
    private static final int MONEY_SCALE = 2;

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository usageRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> getSellerVouchers(Long sellerId, String keyword, int page, int size) {
        Shop shop = ownedShop(sellerId);
        Page<Voucher> vouchers = voucherRepository.findSellerVouchers(
                shop.getId(), normalizeKeyword(keyword),
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                        Sort.by("createdAt").descending())
        );
        return PageResponse.of(vouchers.map(this::toResponse));
    }

    @Transactional
    public VoucherResponse create(Long sellerId, VoucherRequest request) {
        Shop shop = ownedShop(sellerId);
        requireShopCanSell(shop);
        String code = normalizeCode(request.getCode());
        if (voucherRepository.existsByShopIdAndCodeIgnoreCase(shop.getId(), code)) {
            throw new AppException(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);
        }
        validateRequest(request);
        Voucher voucher = Voucher.builder()
                .shop(shop)
                .code(code)
                .description(normalizeDescription(request.getDescription()))
                .discountType(request.getDiscountType())
                .discountValue(money(request.getDiscountValue()))
                .maxDiscountAmount(nullableMoney(request.getMaxDiscountAmount()))
                .minOrderAmount(minimumOrderAmount(request.getMinOrderAmount()))
                .applyAllProducts(request.isApplyAllProducts())
                .products(loadOwnedProducts(shop.getId(), request.isApplyAllProducts(), request.getProductIds()))
                .startsAt(request.getStartsAt())
                .expiresAt(request.getExpiresAt())
                .usageLimit(request.getUsageLimit())
                .build();
        return toResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherResponse update(Long sellerId, Long voucherId, VoucherRequest request) {
        Shop shop = ownedShop(sellerId);
        requireShopCanSell(shop);
        Voucher voucher = voucherRepository.findOwnedById(shop.getId(), voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        validateRequest(request);
        String code = normalizeCode(request.getCode());
        if (!voucher.getCode().equalsIgnoreCase(code)
                && voucherRepository.existsByShopIdAndCodeIgnoreCase(shop.getId(), code)) {
            throw new AppException(ErrorCode.VOUCHER_CODE_ALREADY_EXISTS);
        }
        if (voucher.getUsedCount() > 0 && immutableFieldsChanged(voucher, request, code)) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_USED_CANNOT_EDIT);
        }
        if (request.getUsageLimit() < voucher.getUsedCount()) {
            throw new AppException(ErrorCode.VOUCHER_INVALID_VALUE);
        }

        voucher.setCode(code);
        voucher.setDescription(normalizeDescription(request.getDescription()));
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(money(request.getDiscountValue()));
        voucher.setMaxDiscountAmount(nullableMoney(request.getMaxDiscountAmount()));
        voucher.setMinOrderAmount(minimumOrderAmount(request.getMinOrderAmount()));
        voucher.setApplyAllProducts(request.isApplyAllProducts());
        voucher.setProducts(loadOwnedProducts(shop.getId(), request.isApplyAllProducts(), request.getProductIds()));
        voucher.setStartsAt(request.getStartsAt());
        voucher.setExpiresAt(request.getExpiresAt());
        voucher.setUsageLimit(request.getUsageLimit());
        return toResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherResponse setActive(Long sellerId, Long voucherId, boolean active) {
        Shop shop = ownedShop(sellerId);
        requireShopCanSell(shop);
        Voucher voucher = voucherRepository.findOwnedById(shop.getId(), voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        voucher.setActive(active);
        return toResponse(voucherRepository.save(voucher));
    }

    @Transactional(readOnly = true)
    public VoucherPreviewResponse preview(Long buyerId, VoucherPreviewRequest request) {
        List<VoucherLine> lines = previewLines(request);
        Voucher voucher = voucherRepository.findByShopIdAndNormalizedCode(
                        request.getShopId(), normalizeCode(request.getCode()))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        VoucherApplication application = calculateAndValidate(voucher, buyerId, lines, OffsetDateTime.now());
        return new VoucherPreviewResponse(voucher.getId(), voucher.getCode(), application.subtotalAmount(),
                application.discountAmount(), application.totalAmount());
    }

    /** Khóa voucher và giữ một lượt dùng trong transaction checkout hiện tại. */
    @Transactional
    public VoucherApplication reserve(Long buyerId, Long shopId, String code, List<VoucherLine> lines) {
        if (code == null || code.isBlank()) {
            BigDecimal subtotal = lines.stream().map(VoucherLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            return VoucherApplication.none(subtotal, lines.size());
        }
        Voucher voucher = voucherRepository.findForUpdate(shopId, normalizeCode(code))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        VoucherApplication application = calculateAndValidate(voucher, buyerId, lines, OffsetDateTime.now());
        voucher.setUsedCount(voucher.getUsedCount() + 1);
        voucherRepository.save(voucher);
        return application;
    }

    @Transactional
    public void confirmUsage(VoucherApplication application, Long buyerId, Order order) {
        if (application.voucher() == null) return;
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        usageRepository.save(VoucherUsage.builder()
                .voucher(application.voucher())
                .user(buyer)
                .order(order)
                .discountAmount(application.discountAmount())
                .build());
    }

    /** Hủy trước khi giao thì trả lượt; lịch sử vẫn giữ ở trạng thái RELEASED. */
    @Transactional
    public void releaseUsageForCancelledOrder(Long orderId) {
        VoucherUsage usage = usageRepository.findByOrderIdAndStatus(orderId, VoucherUsageStatus.APPLIED).orElse(null);
        if (usage == null) return;
        Voucher voucher = voucherRepository.findForUpdate(
                        usage.getVoucher().getShop().getId(), usage.getVoucher().getCode())
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        usage.setStatus(VoucherUsageStatus.RELEASED);
        usage.setReleasedAt(OffsetDateTime.now());
        voucher.setUsedCount(Math.max(0, voucher.getUsedCount() - 1));
        usageRepository.save(usage);
        voucherRepository.save(voucher);
    }

    private VoucherApplication calculateAndValidate(Voucher voucher, Long buyerId,
                                                     List<VoucherLine> lines, OffsetDateTime now) {
        if (!voucher.isActive()) throw new AppException(ErrorCode.VOUCHER_NOT_ACTIVE);
        if (now.isBefore(voucher.getStartsAt())) throw new AppException(ErrorCode.VOUCHER_NOT_STARTED);
        if (!now.isBefore(voucher.getExpiresAt())) throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        if (voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new AppException(ErrorCode.VOUCHER_USAGE_LIMIT_REACHED);
        }
        if (usageRepository.existsByVoucherIdAndUserIdAndStatus(voucher.getId(), buyerId, VoucherUsageStatus.APPLIED)) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_USED);
        }
        Set<Long> applicableProducts = voucher.getProducts().stream().map(Product::getId).collect(Collectors.toSet());
        if (!voucher.isApplyAllProducts()
                && lines.stream().anyMatch(line -> !applicableProducts.contains(line.productId()))) {
            throw new AppException(ErrorCode.VOUCHER_PRODUCT_NOT_APPLICABLE);
        }

        BigDecimal subtotal = lines.stream().map(VoucherLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new AppException(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);
        }
        BigDecimal discount = voucher.getDiscountType() == VoucherDiscountType.PERCENT
                ? subtotal.multiply(voucher.getDiscountValue()).divide(new BigDecimal("100"), MONEY_SCALE, RoundingMode.DOWN)
                : voucher.getDiscountValue();
        if (voucher.getMaxDiscountAmount() != null) discount = discount.min(voucher.getMaxDiscountAmount());
        discount = money(discount);
        if (discount.compareTo(BigDecimal.ZERO) <= 0 || discount.compareTo(subtotal) >= 0) {
            throw new AppException(ErrorCode.VOUCHER_INVALID_VALUE);
        }
        return new VoucherApplication(voucher, subtotal, discount, subtotal.subtract(discount),
                allocateDiscount(lines, subtotal, discount));
    }

    private List<BigDecimal> allocateDiscount(List<VoucherLine> lines, BigDecimal subtotal, BigDecimal discount) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal part = index == lines.size() - 1
                    ? discount.subtract(allocated)
                    : discount.multiply(lines.get(index).subtotal()).divide(subtotal, MONEY_SCALE, RoundingMode.DOWN);
            result.add(part);
            allocated = allocated.add(part);
        }
        return List.copyOf(result);
    }

    private List<VoucherLine> previewLines(VoucherPreviewRequest request) {
        String deliveryType = request.getDeliveryType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("INSTANT", "PRE_ORDER").contains(deliveryType)) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
        }
        Set<Long> ids = request.getItems().stream().map(VoucherPreviewRequest.Item::getProductVariantId).collect(Collectors.toSet());
        Map<Long, ProductVariant> variants = variantRepository.findCheckoutVariants(ids).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (variants.size() != ids.size()) throw new AppException(ErrorCode.RECORD_NOT_FOUND);
        List<VoucherLine> lines = new ArrayList<>();
        for (VoucherPreviewRequest.Item item : request.getItems()) {
            ProductVariant variant = variants.get(item.getProductVariantId());
            Product product = variant.getProduct();
            if (!product.getShop().getId().equals(request.getShopId())) {
                throw new AppException(ErrorCode.VOUCHER_SHOP_MISMATCH);
            }
            Shop shop = product.getShop();
            requireShopCanSell(shop);
            if (!deliveryType.equals(product.getDeliveryType())) {
                throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
            }
            if (!"ACTIVE".equals(product.getStatus()) || !"ACTIVE".equals(variant.getStatus())) {
                throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
            }
            lines.add(new VoucherLine(product.getId(), variant.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))));
        }
        return lines;
    }

    private Shop ownedShop(Long sellerId) {
        return shopRepository.findByOwnerId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
    }

    private void requireShopCanSell(Shop shop) {
        if (!"ACTIVE".equals(shop.getStatus())
                || !"ACTIVE".equals(shop.getOwner().getStatus())
                || !shop.getOwner().hasRole("SELLER")) {
            throw new AppException(ErrorCode.SHOP_UNAUTHORIZED);
        }
    }

    private Set<Product> loadOwnedProducts(Long shopId, boolean applyAll, Set<Long> productIds) {
        if (applyAll) return new LinkedHashSet<>();
        if (productIds == null || productIds.isEmpty()) {
            throw new AppException(ErrorCode.VOUCHER_PRODUCT_NOT_APPLICABLE);
        }
        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size()
                || products.stream().anyMatch(product -> !product.getShop().getId().equals(shopId)
                || "DELETED".equals(product.getStatus()))) {
            throw new AppException(ErrorCode.VOUCHER_PRODUCT_NOT_APPLICABLE);
        }
        return new LinkedHashSet<>(products);
    }

    private void validateRequest(VoucherRequest request) {
        if (!request.getStartsAt().isBefore(request.getExpiresAt())) {
            throw new AppException(ErrorCode.VOUCHER_INVALID_VALUE);
        }
        if (request.getMinOrderAmount() != null
                && request.getMinOrderAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.VOUCHER_INVALID_VALUE);
        }
        if (request.getDiscountType() == VoucherDiscountType.PERCENT
                && request.getDiscountValue().compareTo(new BigDecimal("100")) >= 0) {
            throw new AppException(ErrorCode.VOUCHER_INVALID_VALUE);
        }
        if (request.getDiscountType() == VoucherDiscountType.FIXED && request.getMaxDiscountAmount() != null) {
            throw new AppException(ErrorCode.VOUCHER_INVALID_VALUE);
        }
    }

    private boolean immutableFieldsChanged(Voucher voucher, VoucherRequest request, String code) {
        Set<Long> requestedProductIds = request.getProductIds() == null ? Set.of() : request.getProductIds();
        Set<Long> existingProductIds = voucher.getProducts().stream().map(Product::getId).collect(Collectors.toSet());
        return !voucher.getCode().equals(code)
                || voucher.getDiscountType() != request.getDiscountType()
                || voucher.getDiscountValue().compareTo(money(request.getDiscountValue())) != 0
                || !Objects.equals(voucher.getMaxDiscountAmount(), nullableMoney(request.getMaxDiscountAmount()))
                || voucher.getMinOrderAmount().compareTo(minimumOrderAmount(request.getMinOrderAmount())) != 0
                || voucher.isApplyAllProducts() != request.isApplyAllProducts()
                || (!request.isApplyAllProducts() && !existingProductIds.equals(requestedProductIds));
    }

    private VoucherResponse toResponse(Voucher voucher) {
        return VoucherResponse.builder()
                .id(voucher.getId()).code(voucher.getCode()).description(voucher.getDescription())
                .discountType(voucher.getDiscountType()).discountValue(voucher.getDiscountValue())
                .maxDiscountAmount(voucher.getMaxDiscountAmount()).minOrderAmount(voucher.getMinOrderAmount())
                .applyAllProducts(voucher.isApplyAllProducts())
                .productIds(voucher.getProducts().stream().map(Product::getId).collect(Collectors.toCollection(LinkedHashSet::new)))
                .startsAt(voucher.getStartsAt()).expiresAt(voucher.getExpiresAt())
                .usageLimit(voucher.getUsageLimit()).usedCount(voucher.getUsedCount())
                .active(voucher.isActive()).status(statusOf(voucher, OffsetDateTime.now()))
                .createdAt(voucher.getCreatedAt()).updatedAt(voucher.getUpdatedAt()).build();
    }

    private String statusOf(Voucher voucher, OffsetDateTime now) {
        if (!now.isBefore(voucher.getExpiresAt())) return "EXPIRED";
        if (voucher.getUsedCount() >= voucher.getUsageLimit()) return "EXHAUSTED";
        if (!voucher.isActive()) return "PAUSED";
        if (now.isBefore(voucher.getStartsAt())) return "SCHEDULED";
        return "ACTIVE";
    }

    private String normalizeCode(String value) {
        if (value == null) throw new AppException(ErrorCode.VOUCHER_NOT_FOUND);
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeKeyword(String value) { return value == null ? "" : value.trim(); }
    private String normalizeDescription(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BigDecimal money(BigDecimal value) { return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP); }
    private BigDecimal nullableMoney(BigDecimal value) { return value == null ? null : money(value); }
    private BigDecimal minimumOrderAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : money(value);
    }

    public record VoucherLine(Long productId, BigDecimal subtotal) { }

    public record VoucherApplication(Voucher voucher, BigDecimal subtotalAmount, BigDecimal discountAmount,
                                     BigDecimal totalAmount, List<BigDecimal> lineDiscounts) {
        public static VoucherApplication none(BigDecimal subtotal, int lineCount) {
            return new VoucherApplication(null, subtotal, BigDecimal.ZERO, subtotal,
                    Collections.nCopies(lineCount, BigDecimal.ZERO));
        }
    }
}
