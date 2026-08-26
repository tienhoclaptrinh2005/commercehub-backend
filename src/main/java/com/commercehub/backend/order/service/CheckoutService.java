package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.request.CheckoutItemRequest;
import com.commercehub.backend.order.dto.request.CheckoutRequest;
import com.commercehub.backend.order.entity.CheckoutAttempt;
import com.commercehub.backend.order.repository.CheckoutAttemptRepository;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final String OPERATION_TYPE = "CHECKOUT";

    private final ProductVariantRepository variantRepository;
    private final InstantOrderService instantOrderService;
    private final PreOrderService preOrderService;
    private final CheckoutAttemptRepository checkoutAttemptRepository;
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<Long> processCheckout(Long buyerId, CheckoutRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        Map<Long, ProductVariant> variants = loadVariants(request.getItems());
        Set<Long> walletUserIds = new HashSet<>();
        walletUserIds.add(buyerId);
        variants.values().forEach(variant ->
                walletUserIds.add(variant.getProduct().getShop().getOwner().getId()));

        // Khóa tất cả ví buyer/seller theo wallet.id tăng dần. Cách này vừa tuần tự
        // hóa double-click của cùng buyer, vừa tránh deadlock khi hai seller mua
        // chéo sản phẩm của nhau trong cùng thời điểm.
        List<Wallet> lockedWallets = walletRepository.findAllByUserIdsWithLock(walletUserIds);
        Set<Long> lockedUserIds = lockedWallets.stream()
                .map(wallet -> wallet.getUser().getId())
                .collect(Collectors.toSet());
        if (!lockedUserIds.containsAll(walletUserIds)) {
            throw new AppException(ErrorCode.WALLET_NOT_FOUND);
        }

        String requestHash = hashRequest(request);
        CheckoutAttempt attempt = getOrStartAttempt(
                buyerId,
                request.getIdempotencyKey(),
                requestHash
        );
        if ("DONE".equals(attempt.getStatus())) {
            return readOrderIds(attempt.getResponseBody());
        }

        Map<String, List<CheckoutItemRequest>> groupedItems = new TreeMap<>();
        for (CheckoutItemRequest item : request.getItems()) {
            ProductVariant variant = variants.get(item.getProductVariantId());
            String groupKey = variant.getProduct().getShop().getId()
                    + "_" + variant.getProduct().getDeliveryType();
            groupedItems.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(item);
        }

        List<Long> createdOrderIds = new ArrayList<>();
        for (Map.Entry<String, List<CheckoutItemRequest>> entry : groupedItems.entrySet()) {
            CheckoutRequest subRequest = new CheckoutRequest();
            subRequest.setItems(entry.getValue());
            subRequest.setPaymentMethod(request.getPaymentMethod());
            subRequest.setCheckoutRequestId(attempt.getId());
            // Không lưu key trực tiếp trên từng order: một checkout có thể tách
            // thành nhiều order. Liên kết chuẩn là orders.checkout_request_id.
            subRequest.setIdempotencyKey(null);

            if (entry.getKey().endsWith("_INSTANT")) {
                createdOrderIds.add(instantOrderService.checkoutInstant(buyerId, subRequest));
            } else if (entry.getKey().endsWith("_PRE_ORDER")) {
                createdOrderIds.add(preOrderService.checkoutPreOrder(buyerId, subRequest));
            } else {
                throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
            }
        }

        attempt.setResponseBody(writeJson(createdOrderIds));
        attempt.setStatus("DONE");
        checkoutAttemptRepository.save(attempt);

        log.info("Checkout hoàn tất - user={} attempt={} orders={}",
                buyerId, attempt.getId(), createdOrderIds);
        return List.copyOf(createdOrderIds);
    }

    private Map<Long, ProductVariant> loadVariants(Collection<CheckoutItemRequest> items) {
        Set<Long> ids = items.stream()
                .map(CheckoutItemRequest::getProductVariantId)
                .collect(Collectors.toSet());
        Map<Long, ProductVariant> variants = variantRepository.findCheckoutVariants(ids).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (variants.size() != ids.size()) {
            throw new AppException(ErrorCode.RECORD_NOT_FOUND);
        }
        return variants;
    }

    private CheckoutAttempt getOrStartAttempt(Long buyerId, String key, String requestHash) {
        CheckoutAttempt existing = checkoutAttemptRepository
                .findForUpdate(buyerId, OPERATION_TYPE, key)
                .orElse(null);

        if (existing != null) {
            boolean expired = existing.getExpiresAt().isBefore(OffsetDateTime.now());
            if (!expired && !existing.getRequestHash().equals(requestHash)) {
                throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if (!expired && "DONE".equals(existing.getStatus())) {
                log.info("Checkout idempotency hit - user={} attempt={}", buyerId, existing.getId());
                return existing;
            }
            if (!expired) {
                throw new AppException(ErrorCode.CHECKOUT_ALREADY_PROCESSING);
            }

            existing.setRequestHash(requestHash);
            existing.setResponseBody(null);
            existing.setStatus("PROCESSING");
            existing.setExpiresAt(OffsetDateTime.now().plusHours(24));
            return checkoutAttemptRepository.save(existing);
        }

        try {
            return checkoutAttemptRepository.saveAndFlush(CheckoutAttempt.builder()
                    .keyValue(key)
                    .operationType(OPERATION_TYPE)
                    .userId(buyerId)
                    .requestHash(requestHash)
                    .status("PROCESSING")
                    .expiresAt(OffsetDateTime.now().plusHours(24))
                    .build());
        } catch (DataIntegrityViolationException exception) {
            // Phòng thủ thêm nếu một caller cũ không đi qua luồng khóa chuẩn.
            throw new AppException(ErrorCode.CHECKOUT_ALREADY_PROCESSING);
        }
    }

    private String hashRequest(CheckoutRequest request) {
        List<CanonicalCheckoutItem> canonicalItems = request.getItems().stream()
                .map(item -> new CanonicalCheckoutItem(
                        item.getProductVariantId(),
                        item.getQuantity(),
                        item.getBuyerInputs() == null ? "" : item.getBuyerInputs()
                ))
                .sorted(Comparator
                        .comparing(CanonicalCheckoutItem::productVariantId)
                        .thenComparing(CanonicalCheckoutItem::quantity)
                        .thenComparing(CanonicalCheckoutItem::buyerInputs))
                .toList();

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("paymentMethod", normalizePaymentMethod(request.getPaymentMethod()));
        canonical.put("items", canonicalItems);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }

    private String normalizePaymentMethod(String paymentMethod) {
        return paymentMethod == null ? "WALLET" : paymentMethod.trim().toUpperCase();
    }

    private List<Long> readOrderIds(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
        try {
            return List.of(objectMapper.readValue(responseBody, Long[].class));
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }

    private String writeJson(List<Long> orderIds) {
        try {
            return objectMapper.writeValueAsString(orderIds);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }

    private record CanonicalCheckoutItem(Long productVariantId, Integer quantity, String buyerInputs) {
    }
}
