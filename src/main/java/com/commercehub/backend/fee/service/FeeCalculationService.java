package com.commercehub.backend.fee.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.dto.FeeResult;
import com.commercehub.backend.fee.entity.PlatformFeeConfig;
import com.commercehub.backend.fee.repository.PlatformFeeConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class FeeCalculationService {

    private final PlatformFeeConfigRepository feeConfigRepository;

    @Transactional(readOnly = true)
    public FeeResult calculateFee(BigDecimal saleAmount) {
        // 1. Lấy cấu hình đang Active
        PlatformFeeConfig activeConfig = feeConfigRepository.findByIsActiveTrue()
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION)); // TODO: Tạo mã ERROR_FEE_CONFIG_NOT_FOUND sau

        // 2. Tính phí thô
        BigDecimal rawFee = saleAmount.multiply(activeConfig.getFeeRate());

        // 3. Áp dụng Min/Max fee
        BigDecimal feeAfterMin = rawFee.max(activeConfig.getMinFeeAmount());
        BigDecimal finalFee;
        if (activeConfig.getMaxFeeAmount() != null) {
            finalFee = feeAfterMin.min(activeConfig.getMaxFeeAmount());
        } else {
            finalFee = feeAfterMin;
        }

        // 4. Làm tròn số
        finalFee = finalFee.setScale(0, RoundingMode.HALF_UP);
        BigDecimal sellerNetAmount = saleAmount.subtract(finalFee);

        return FeeResult.builder()
                .feeConfigId(activeConfig.getId())
                .feeRateSnapshot(activeConfig.getFeeRate())
                .feeAmount(finalFee)
                .sellerNetAmount(sellerNetAmount)
                .build();
    }
}