package com.commercehub.backend.product.service;

import com.commercehub.backend.product.entity.AssetDeliveryLog;
import com.commercehub.backend.product.entity.DigitalAsset;
import com.commercehub.backend.product.repository.AssetDeliveryLogRepository;
import com.commercehub.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssetDeliveryService {

    private final AssetDeliveryLogRepository deliveryLogRepository;

    @Transactional
    public AssetDeliveryLog logDelivery(DigitalAsset asset, Long orderItemId, User buyer, String snapshot, String method, String status, String error) {
        AssetDeliveryLog log = AssetDeliveryLog.builder()
                .asset(asset)
                .orderItemId(orderItemId)
                .buyer(buyer)
                .assetDataSnapshot(snapshot)
                .deliveryMethod(method)
                .status(status)
                .errorMessage(error)
                .build();
        return deliveryLogRepository.save(log);
    }
}