package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.product.dto.request.CreatePreOrderConfigRequest;
import com.commercehub.backend.product.entity.PreOrderConfig;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.repository.PreOrderConfigRepository;
import com.commercehub.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreOrderConfigService {

    private final PreOrderConfigRepository configRepository;
    private final ProductRepository productRepository;

    @Transactional
    public PreOrderConfig createOrUpdateConfig(Long sellerId, CreatePreOrderConfigRequest request) {

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if ("DELETED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (!product.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!"PRE_ORDER".equals(product.getDeliveryType())) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE_FOR_CONFIG);
        }

        PreOrderConfig config = configRepository.findByProductId(request.getProductId())
                .orElse(new PreOrderConfig());

        config.setProduct(product);

        if (request.getMaxProcessingHours() != null) {
            config.setMaxProcessingHours(request.getMaxProcessingHours());
        }

        config.setOrderInstructions(request.getOrderInstructions());
        config.setBuyerInputFields(request.getBuyerInputFields());

        if (request.getAutoRejectIfUnavailable() != null) {
            config.setAutoRejectIfUnavailable(request.getAutoRejectIfUnavailable());
        }

        return configRepository.save(config);
    }

    @Transactional(readOnly = true)
    public PreOrderConfig getConfigByProductId(Long productId) {
        return configRepository.findByProductId(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRE_ORDER_CONFIG_NOT_FOUND));
    }
}