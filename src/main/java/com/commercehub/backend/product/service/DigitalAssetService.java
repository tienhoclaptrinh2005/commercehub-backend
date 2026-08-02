package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.response.DeliveredAssetResponse;
import com.commercehub.backend.product.dto.request.UploadDigitalAssetRequest;
import com.commercehub.backend.product.dto.response.DigitalAssetResponse;
import com.commercehub.backend.product.entity.DigitalAsset;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.DigitalAssetRepository;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DigitalAssetService {

    private final DigitalAssetRepository digitalAssetRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductMapper productMapper;

    // SELLER (Upload, Xem danh sách, Xóa)


    @Transactional
    public int uploadAssets(Long sellerId, UploadDigitalAssetRequest request) {
        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!variant.getProduct().getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (!"INSTANT".equals(variant.getProduct().getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_ASSET);
        }
        if ("DELETED".equals(variant.getProduct().getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        List<String> rawAssets = request.getRawAssets();
        int addedCount = 0;

        for (String rawData : rawAssets) {
            if (rawData == null || rawData.trim().isEmpty()) continue;

            DigitalAsset asset = DigitalAsset.builder()
                    .productVariant(variant)
                    .assetType(variant.getProduct().getProductType())
                    // Lưu ý: Database column là JSONB, nếu rawData là JSON String thì JPA sẽ tự map (tùy cấu hình Dialect)
                    .assetData(rawData.trim())
                    .status("AVAILABLE")
                    .build();

            digitalAssetRepository.save(asset);
            addedCount++;
        }

        variantRepository.incrementStockCount(variant.getId(), addedCount);

        return addedCount;
    }

    @Transactional(readOnly = true)
    public List<DigitalAssetResponse> getAssetsByVariant(Long sellerId, Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!variant.getProduct().getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return digitalAssetRepository.findByProductVariantIdOrderByCreatedAtDesc(variantId).stream()
                .map(productMapper::toDigitalAssetResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAsset(Long sellerId, Long assetId) {
        DigitalAsset asset = digitalAssetRepository.findById(assetId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!asset.getProductVariant().getProduct().getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
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


    // BUYER


    @Transactional(readOnly = true)
    public List<DeliveredAssetResponse> getDeliveredAssetsByOrderId(Long orderId) {
        return digitalAssetRepository.findDeliveredAssetsByOrderId(orderId).stream()
                .map(asset -> DeliveredAssetResponse.builder()
                        .id(asset.getId())
                        .orderItemId(asset.getOrderItemId())
                        .assetType(asset.getAssetType())
                        .assetData(asset.getAssetData())
                        .build())
                .collect(Collectors.toList());
    }
}