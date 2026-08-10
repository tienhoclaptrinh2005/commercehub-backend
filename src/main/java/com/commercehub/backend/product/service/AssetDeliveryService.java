package com.commercehub.backend.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.commercehub.backend.product.entity.AssetDeliveryLog;
import com.commercehub.backend.product.entity.DigitalAsset;
import com.commercehub.backend.product.repository.AssetDeliveryLogRepository;
import com.commercehub.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssetDeliveryService {

    private final AssetDeliveryLogRepository deliveryLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Chụp lại nguyên văn nội dung giao cho khách tại thời điểm bán.
     * Buyer xem lại đơn sẽ đọc từ log này — kho digital_assets bị sửa/thu hồi
     * sau đó cũng không ảnh hưởng nội dung khách đã mua.
     *
     * KHÔNG log deliveryContent ra file log ứng dụng (chứa mật khẩu/key).
     */
    @Transactional
    public AssetDeliveryLog logDelivery(DigitalAsset asset, Long orderItemId, User buyer, String method) {
        String content = asset.getDeliveryContent() != null && !asset.getDeliveryContent().isBlank()
                ? asset.getDeliveryContent()
                : asset.getAssetData();

        AssetDeliveryLog log = AssetDeliveryLog.builder()
                .asset(asset)
                .orderItemId(orderItemId)
                .buyer(buyer)
                .deliveryContentSnapshot(content)
                .assetDataSnapshot(toJson(Map.of("raw", asset.getAssetData() == null ? "" : asset.getAssetData())))
                .deliveryMethod(method)
                .status("SUCCESS")
                .build();
        return deliveryLogRepository.save(log);
    }

    // Cột asset_data_snapshot là jsonb — phải là JSON hợp lệ, không nhét raw text trực tiếp
    private String toJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
