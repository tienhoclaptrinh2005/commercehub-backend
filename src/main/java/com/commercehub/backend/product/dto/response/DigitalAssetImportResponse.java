package com.commercehub.backend.product.dto.response;

/** Kết quả nạp kho; duplicateCount gồm cả dòng trùng trong file và dữ liệu đã có/đã bán. */
public record DigitalAssetImportResponse(
        int receivedCount,
        int addedCount,
        int duplicateCount
) {
}
