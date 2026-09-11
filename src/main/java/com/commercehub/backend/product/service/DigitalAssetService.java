package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.SliceResponse;
import com.commercehub.backend.order.dto.response.DeliveredAssetResponse;
import com.commercehub.backend.product.dto.request.UploadDigitalAssetRequest;
import com.commercehub.backend.product.dto.response.DigitalAssetImportResponse;
import com.commercehub.backend.product.dto.response.DigitalAssetResponse;
import com.commercehub.backend.product.entity.DigitalAsset;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.AssetDeliveryLogRepository;
import com.commercehub.backend.product.repository.DigitalAssetRepository;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DigitalAssetService {

    private static final int MAX_INVENTORY_PAGE = 100;
    private static final int MAX_INVENTORY_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;

    private final DigitalAssetRepository digitalAssetRepository;
    private final AssetDeliveryLogRepository deliveryLogRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductMapper productMapper;

    // SELLER (Upload, Xem danh sách, Xóa)


    @Transactional
    public DigitalAssetImportResponse uploadAssets(Long sellerId, UploadDigitalAssetRequest request) {
        ProductVariant variant = getOwnedVariant(sellerId, request.getVariantId());
        if (!"INSTANT".equals(variant.getProduct().getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
        }
        if ("DELETED".equals(variant.getProduct().getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        int addedCount = 0;
        for (String rawData : request.getRawAssets()) {
            String content = rawData.strip();
            if (content.indexOf('\n') >= 0 || content.indexOf('\r') >= 0) {
                throw new AppException(ErrorCode.ASSET_LINE_INVALID);
            }
            String assetType = variant.getProduct().getProductType();
            String fingerprintIdentifier = extractAssetIdentifier(assetType, content);
            String assetIdentifier = "ACCOUNT".equals(assetType) ? fingerprintIdentifier : null;
            addedCount += digitalAssetRepository.insertAvailableAssetIfAbsent(
                    variant.getId(),
                    assetType,
                    content,
                    assetIdentifier,
                    sha256(assetType + "\n" + fingerprintIdentifier)
            );
        }
        if (addedCount > 0) {
            variantRepository.incrementStockCount(variant.getId(), addedCount);
        }
        int receivedCount = request.getRawAssets().size();
        return new DigitalAssetImportResponse(
                receivedCount,
                addedCount,
                receivedCount - addedCount
        );
    }

    @Transactional(readOnly = true)
    public SliceResponse<DigitalAssetResponse> getAssetsByVariant(
            Long sellerId, Long variantId, int page, int size) {
        getOwnedInstantVariant(sellerId, variantId);
        if (page < 0 || page > MAX_INVENTORY_PAGE || size < 1 || size > MAX_INVENTORY_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Slice<DigitalAssetResponse> assets = digitalAssetRepository
                .findByProductVariantIdAndStatus(variantId, "AVAILABLE", pageable)
                .map(productMapper::toDigitalAssetResponse);
        return SliceResponse.of(assets);
    }

    @Transactional
    public void deleteAsset(Long sellerId, Long assetId) {
        DigitalAsset asset = digitalAssetRepository.findById(assetId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!asset.getProductVariant().getProduct().getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        validateShopCanSell(asset.getProductVariant());
        String status = asset.getStatus();
        if ("SOLD".equals(asset.getStatus())) {
            throw new AppException(ErrorCode.CANNOT_DELETE_SOLD_ASSET);
        } else if (!"AVAILABLE".equals(status)) {
            throw new AppException(ErrorCode.ACCOUNT_IN_TRANSACTION_OR_LOCKED); // Hoặc tạo mã ErrorCode.ASSET_NOT_AVAILABLE
        }

        ProductVariant variant = asset.getProductVariant();
        variantRepository.incrementStockCount(variant.getId(), -1);
        digitalAssetRepository.delete(asset);
    }

    @Transactional
    public int deleteAllAvailableAssets(Long sellerId, Long variantId) {
        ProductVariant variant = getOwnedInstantVariant(sellerId, variantId);
        int deletedCount = digitalAssetRepository.deleteAvailableAssetsByVariantId(variantId);
        if (deletedCount > 0) {
            variantRepository.incrementStockCount(variant.getId(), -deletedCount);
        }
        return deletedCount;
    }

    @Transactional(readOnly = true)
    public void validateInventoryAccess(Long sellerId, Long variantId) {
        getOwnedInstantVariant(sellerId, variantId);
    }

    /** Ghi theo từng batch để tải kho lớn mà không giữ toàn bộ credential trong RAM. */
    @Transactional(readOnly = true)
    public void writeAvailableAssets(Long sellerId, Long variantId, OutputStream outputStream) throws IOException {
        getOwnedInstantVariant(sellerId, variantId);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            long afterId = 0L;
            while (true) {
                List<DigitalAsset> batch = digitalAssetRepository.findAvailableAssetsAfterId(
                        variantId,
                        afterId,
                        PageRequest.of(0, EXPORT_BATCH_SIZE)
                );
                if (batch.isEmpty()) {
                    break;
                }
                for (DigitalAsset asset : batch) {
                    String content = asset.getDeliveryContent() == null || asset.getDeliveryContent().isBlank()
                            ? asset.getAssetData()
                            : asset.getDeliveryContent();
                    writer.write(content);
                    writer.newLine();
                }
                writer.flush();
                afterId = batch.get(batch.size() - 1).getId();
                if (batch.size() < EXPORT_BATCH_SIZE) {
                    break;
                }
            }
        }
    }


    // BUYER


    /**
     * Nội dung đã giao của đơn INSTANT — đọc từ SNAPSHOT asset_delivery_logs,
     * không đọc lại kho (kho bị sửa/thu hồi sau khi bán không ảnh hưởng buyer).
     * Đơn cũ tạo trước khi có delivery log: fallback đọc từ digital_assets như trước.
     */
    @Transactional(readOnly = true)
    public List<DeliveredAssetResponse> getDeliveredAssetsByOrderId(Long orderId) {
        List<DeliveredAssetResponse> fromLogs = deliveryLogRepository.findByOrderId(orderId).stream()
                .map(log -> DeliveredAssetResponse.builder()
                        .id(log.getId())
                        .orderItemId(log.getOrderItemId())
                        .assetType(log.getAsset().getAssetType())
                        .content(log.getDeliveryContentSnapshot())
                        .deliveredAt(log.getDeliveredAt())
                        .build())
                .collect(Collectors.toList());
        if (!fromLogs.isEmpty()) {
            return fromLogs;
        }

        return digitalAssetRepository.findDeliveredAssetsByOrderId(orderId).stream()
                .map(asset -> DeliveredAssetResponse.builder()
                        .id(asset.getId())
                        .orderItemId(asset.getOrderItemId())
                        .assetType(asset.getAssetType())
                        .content(asset.getDeliveryContent() != null && !asset.getDeliveryContent().isBlank()
                                ? asset.getDeliveryContent() : asset.getAssetData())
                        .deliveredAt(asset.getDeliveredAt())
                        .build())
                .collect(Collectors.toList());
    }

    private void validateShopCanSell(ProductVariant variant) {
        if (!"ACTIVE".equals(variant.getProduct().getShop().getStatus())
                || !"ACTIVE".equals(variant.getProduct().getShop().getOwner().getStatus())
                || !variant.getProduct().getShop().getOwner().hasRole("SELLER")) {
            throw new AppException(ErrorCode.SHOP_UNAUTHORIZED);
        }
    }

    private ProductVariant getOwnedVariant(Long sellerId, Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
        if (!variant.getProduct().getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        validateShopCanSell(variant);
        return variant;
    }

    private ProductVariant getOwnedInstantVariant(Long sellerId, Long variantId) {
        ProductVariant variant = getOwnedVariant(sellerId, variantId);
        if (!"INSTANT".equals(variant.getProduct().getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
        }
        if ("DELETED".equals(variant.getProduct().getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return variant;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }

    /**
     * ACCOUNT dùng username trước dấu | làm định danh để cùng một tài khoản đổi
     * password vẫn không thể được nhập và bán lần hai. Các loại key/nội dung khác
     * không có cấu trúc username nên dùng nguyên dòng đã trim.
     */
    private String extractAssetIdentifier(String assetType, String content) {
        if (!"ACCOUNT".equals(assetType)) {
            return content;
        }
        int separatorIndex = content.indexOf('|');
        String username = (separatorIndex >= 0 ? content.substring(0, separatorIndex) : content)
                .strip()
                .toLowerCase(Locale.ROOT);
        if (username.isEmpty() || username.length() > 500) {
            throw new AppException(ErrorCode.ASSET_LINE_INVALID);
        }
        return username;
    }
}
