package com.commercehub.backend.cart.service;

import com.commercehub.backend.cart.dto.request.AddToCartRequest;
import com.commercehub.backend.cart.dto.request.CartCheckoutRequest;
import com.commercehub.backend.cart.dto.request.UpdateCartItemRequest;
import com.commercehub.backend.cart.dto.response.CartItemResponse;
import com.commercehub.backend.cart.dto.response.CartResponse;
import com.commercehub.backend.cart.entity.Cart;
import com.commercehub.backend.cart.entity.CartItem;
import com.commercehub.backend.cart.repository.CartItemRepository;
import com.commercehub.backend.cart.repository.CartRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.request.CheckoutItemRequest;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.order.service.CheckoutService;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final int MAX_QUANTITY_PER_ITEM = 1000;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final CheckoutService checkoutService;

    // ======================================================
    // XEM GIỎ HÀNG
    // ======================================================

    @Transactional(readOnly = true)
    public CartResponse getMyCart(Long userId) {
        Cart cart = cartRepository.findByUserIdWithItems(userId).orElse(null);
        if (cart == null || cart.getItems().isEmpty()) {
            return CartResponse.builder()
                    .cartId(cart != null ? cart.getId() : null)
                    .items(List.of())
                    .totalItems(0)
                    .totalQuantity(0)
                    .totalAmount(BigDecimal.ZERO)
                    .build();
        }

        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalQuantity = 0;

        for (CartItem item : cart.getItems()) {
            CartItemResponse res = toItemResponse(item);
            items.add(res);
            totalQuantity += item.getQuantity();
            if (Boolean.TRUE.equals(res.getAvailable())) {
                totalAmount = totalAmount.add(res.getLineTotal());
            }
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(items)
                .totalItems(items.size())
                .totalQuantity(totalQuantity)
                .totalAmount(totalAmount)
                .build();
    }

    // ======================================================
    // THÊM / SỬA / XÓA
    // ======================================================

    @Transactional
    public CartResponse addItem(Long userId, AddToCartRequest request) {
        ProductVariant variant = variantRepository.findById(request.getProductVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        Product product = variant.getProduct();

        // Không cho thêm hàng ngừng bán / shop khóa / hàng của chính mình
        if (!"ACTIVE".equals(variant.getStatus()) || !"ACTIVE".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        if (!"ACTIVE".equals(product.getShop().getStatus())
                || !"ACTIVE".equals(product.getShop().getOwner().getStatus())
                || !product.getShop().getOwner().hasRole("SELLER")) {
            throw new AppException(ErrorCode.SHOP_SUSPENDED);
        }
        if (product.getShop().getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_BUY_OWN_PRODUCT);
        }

        Cart cart = getOrCreateCart(userId);

        // Variant đã có trong giỏ → cộng dồn số lượng
        CartItem existing = cartItemRepository
                .findByCartIdAndProductVariantId(cart.getId(), variant.getId())
                .orElse(null);

        int newQuantity = request.getQuantity() + (existing != null ? existing.getQuantity() : 0);
        if (newQuantity > MAX_QUANTITY_PER_ITEM) {
            newQuantity = MAX_QUANTITY_PER_ITEM;
        }

        // INSTANT: không cho vượt tồn kho hiện tại
        if ("INSTANT".equals(product.getDeliveryType()) && newQuantity > variant.getStockCount()) {
            throw new AppException(ErrorCode.OUT_OF_STOCK);
        }

        if (existing != null) {
            existing.setQuantity(newQuantity);
            cartItemRepository.save(existing);
        } else {
            cartItemRepository.save(CartItem.builder()
                    .cart(cart)
                    .productVariant(variant)
                    .quantity(newQuantity)
                    .build());
        }

        return getMyCart(userId);
    }

    @Transactional
    public CartResponse updateItemQuantity(Long userId, Long itemId, UpdateCartItemRequest request) {
        CartItem item = cartItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        ProductVariant variant = item.getProductVariant();
        Product product = variant.getProduct();
        if (!"ACTIVE".equals(variant.getStatus())
                || !"ACTIVE".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        if (!"ACTIVE".equals(product.getShop().getStatus())
                || !"ACTIVE".equals(product.getShop().getOwner().getStatus())
                || !product.getShop().getOwner().hasRole("SELLER")) {
            throw new AppException(ErrorCode.SHOP_SUSPENDED);
        }
        if ("INSTANT".equals(variant.getProduct().getDeliveryType())
                && request.getQuantity() > variant.getStockCount()) {
            throw new AppException(ErrorCode.OUT_OF_STOCK);
        }

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return getMyCart(userId);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        CartItem item = cartItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
        cartItemRepository.delete(item);
        return getMyCart(userId);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findByUserId(userId)
                .ifPresent(cart -> cartItemRepository.deleteAllByCartId(cart.getId()));
    }

    // ======================================================
    // CHECKOUT TỪ GIỎ HÀNG
    // ======================================================

    /**
     * Checkout toàn bộ giỏ: build CheckoutRequest từ items trong giỏ rồi
     * giao cho CheckoutService (tự tách đơn theo shop + loại giao hàng,
     * trừ ví, hold tiền, tính phí sàn per-item như luồng checkout thường).
     * Checkout thành công → xóa sạch giỏ (cùng transaction).
     */
    @Transactional
    public List<Long> checkoutCart(Long userId, CartCheckoutRequest request) {
        Cart cart = cartRepository.findByUserIdWithItems(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_EMPTY));

        if (cart.getItems().isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        // Map buyerInputs (PRE_ORDER) theo variantId
        Map<Long, String> buyerInputsMap = new HashMap<>();
        if (request != null && request.getBuyerInputs() != null) {
            for (CartCheckoutRequest.CartBuyerInput input : request.getBuyerInputs()) {
                if (input.getProductVariantId() != null) {
                    buyerInputsMap.put(input.getProductVariantId(), input.getBuyerInputs());
                }
            }
        }

        List<CheckoutItemRequest> checkoutItems = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            CheckoutItemRequest checkoutItem = new CheckoutItemRequest();
            checkoutItem.setProductVariantId(item.getProductVariant().getId());
            checkoutItem.setQuantity(item.getQuantity());
            checkoutItem.setBuyerInputs(buyerInputsMap.get(item.getProductVariant().getId()));
            checkoutItems.add(checkoutItem);
        }

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setItems(checkoutItems);
        checkoutRequest.setPaymentMethod("WALLET");
        checkoutRequest.setIdempotencyKey(request != null ? request.getIdempotencyKey() : null);

        List<Long> orderIds = checkoutService.processCheckout(userId, checkoutRequest);

        // Checkout thành công (không exception) → dọn giỏ
        cartItemRepository.deleteAllByCartId(cart.getId());

        log.info("User {} checkout từ giỏ hàng thành công → {} đơn: {}", userId, orderIds.size(), orderIds);
        return orderIds;
    }

    // ======================================================
    // PRIVATE
    // ======================================================

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            return cartRepository.save(Cart.builder().user(user).build());
        });
    }

    private CartItemResponse toItemResponse(CartItem item) {
        ProductVariant variant = item.getProductVariant();
        Product product = variant.getProduct();

        boolean active = "ACTIVE".equals(variant.getStatus())
                && "ACTIVE".equals(product.getStatus())
                && "ACTIVE".equals(product.getShop().getStatus())
                && "ACTIVE".equals(product.getShop().getOwner().getStatus())
                && product.getShop().getOwner().hasRole("SELLER");
        boolean inStock = !"INSTANT".equals(product.getDeliveryType())
                || variant.getStockCount() >= item.getQuantity();

        BigDecimal lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

        return CartItemResponse.builder()
                .id(item.getId())
                .productVariantId(variant.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productSlug(product.getSlug())
                .variantName(variant.getName())
                .thumbnailUrl(product.getThumbnailUrl())
                .deliveryType(product.getDeliveryType())
                .productType(product.getProductType())
                .shopId(product.getShop().getId())
                .shopName(product.getShop().getName())
                .unitPrice(variant.getPrice())
                .quantity(item.getQuantity())
                .lineTotal(lineTotal)
                .stockCount(variant.getStockCount())
                .available(active && inStock)
                .build();
    }
}
