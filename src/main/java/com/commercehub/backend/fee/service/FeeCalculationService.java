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

    /**
     * Tính phí sàn cho 1 sản phẩm/dịch vụ.
     *
     * Logic:
     *   1. Phí thô = saleAmount × feeRate (ví dụ: 100đ × 0.04 = 4đ)
     *   2. Áp dụng phí tối thiểu (minFeeAmount) nếu có
     *   3. Áp dụng phí tối đa (maxFeeAmount) nếu có
     *   4. Làm tròn số (HALF_UP) — 4.5đ → 5đ, 4.4đ → 4đ
     *   5. CHỐT CHẶN AN TOÀN: Phí TUYỆT ĐỐI KHÔNG vượt quá giá bán
     *      → Đảm bảo sellerNetAmount >= 0 trong mọi trường hợp
     *      → Kể cả admin gõ nhầm feeRate = 5.0 (500%) thay vì 0.05 (5%)
     *
     * Ví dụ thực tế với feeRate = 0.04 (4%):
     *   - Mail giá 100đ  → phí 4đ,   shop nhận 96đ
     *   - Mail giá 2.000đ → phí 80đ,  shop nhận 1.920đ
     *   - Bản quyền 1.000.000đ → phí 40.000đ, shop nhận 960.000đ
     */
    @Transactional(readOnly = true)
    public FeeResult calculateFee(BigDecimal saleAmount) {

        // 1. Lấy cấu hình phí sàn đang Active (feeRate = 0.04 cho 4%)
        PlatformFeeConfig activeConfig = feeConfigRepository.findByIsActiveTrue()
                .orElseThrow(() -> new AppException(ErrorCode.FEE_CONFIG_NOT_FOUND));

        // 2. Tính phí sàn thuần túy (phần trăm giá trị sản phẩm)
        BigDecimal finalFee = saleAmount.multiply(activeConfig.getFeeRate());

        // 3. Áp dụng phí tối thiểu (minFeeAmount) — nếu Admin cấu hình
        //    Ví dụ: minFeeAmount = 1đ → đơn 100đ sẽ luôn thu ít nhất 1đ dù 4% chỉ ra 4đ
        if (activeConfig.getMinFeeAmount() != null
                && activeConfig.getMinFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            finalFee = finalFee.max(activeConfig.getMinFeeAmount());
        }

        // 4. Áp dụng phí tối đa (maxFeeAmount) — nếu Admin cấu hình
        //    Ví dụ: maxFeeAmount = 50.000đ → đơn 10 triệu chỉ thu tối đa 50k
        if (activeConfig.getMaxFeeAmount() != null
                && activeConfig.getMaxFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            finalFee = finalFee.min(activeConfig.getMaxFeeAmount());
        }

        // 5. Làm tròn số (tránh lẻ tiền: 4.5đ → 5đ, 4.4đ → 4đ)
        finalFee = finalFee.setScale(0, RoundingMode.HALF_UP);

        // =========================================================================
        // ĐÃ FIX Bug #39: CHỐT CHẶN AN TOÀN
        // Phí sàn TUYỆT ĐỐI KHÔNG BAO GIỜ được vượt quá giá bán.
        // → Bảo vệ hệ thống khỏi lỗi admin gõ nhầm feeRate (ví dụ 5.0 thay vì 0.05)
        // → Đảm bảo sellerNetAmount >= 0 trong MỌI trường hợp
        // =========================================================================
        finalFee = finalFee.min(saleAmount);

        // 6. Tính tiền thực nhận của Shop (luôn >= 0 nhờ chốt chặn ở trên)
        BigDecimal sellerNetAmount = saleAmount.subtract(finalFee);

        return FeeResult.builder()
                .feeConfigId(activeConfig.getId())
                .feeRateSnapshot(activeConfig.getFeeRate())
                .feeAmount(finalFee)
                .sellerNetAmount(sellerNetAmount)
                .build();
    }
}