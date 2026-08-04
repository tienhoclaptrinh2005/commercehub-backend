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

    /** Scale chuẩn của mọi số tiền trong hệ thống (khớp với các cột NUMERIC(18,2)). */
    private static final int MONEY_SCALE = 2;

    /**
     * Tính phí sàn cho 1 dòng sản phẩm (line item).
     *
     * Logic:
     *   1. Phí thô = saleAmount × feeRate (ví dụ: 100đ × 0.04 = 4đ)
     *   2. Áp dụng phí tối thiểu (minFeeAmount) nếu có
     *   3. Áp dụng phí tối đa (maxFeeAmount) nếu có
     *   4. LÀM TRÒN LÊN (CEILING) về đồng nguyên — 3.2đ → 4đ, 780.0đ → 780đ
     *      → Sàn không bao giờ thất thoát phần lẻ; số tiền luôn là VND nguyên.
     *   5. CHỐT CHẶN AN TOÀN: Phí TUYỆT ĐỐI KHÔNG vượt quá giá bán
     *      → Đảm bảo sellerNetAmount >= 0 trong mọi trường hợp
     *
     * Kết quả trả về luôn ở scale 2 để thống nhất với các entity tiền:
     *   feeAmount + sellerNetAmount = saleAmount (khớp 100% từng đồng).
     *
     * Ví dụ (feeRate = 0.04 = 4% — mặc định v8):
     *   - Món 19.500đ → phí 780đ, shop nhận 18.720đ
     *   - Món 100đ    → phí 4đ,   shop nhận 96đ
     */
    @Transactional(readOnly = true)
    public FeeResult calculateFee(BigDecimal saleAmount) {

        if (saleAmount == null || saleAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.INVALID_AMOUNT);
        }

        // 1. Lấy cấu hình phí sàn đang Active
        PlatformFeeConfig activeConfig = feeConfigRepository.findByIsActiveTrue()
                .orElseThrow(() -> new AppException(ErrorCode.FEE_CONFIG_NOT_FOUND));

        // 2. Tính phí sàn thuần túy (phần trăm giá trị sản phẩm)
        BigDecimal finalFee = saleAmount.multiply(activeConfig.getFeeRate());

        // 3. Áp dụng phí tối thiểu (minFeeAmount) — nếu Admin cấu hình
        if (activeConfig.getMinFeeAmount() != null
                && activeConfig.getMinFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            finalFee = finalFee.max(activeConfig.getMinFeeAmount());
        }

        // 4. Áp dụng phí tối đa (maxFeeAmount) — nếu Admin cấu hình
        if (activeConfig.getMaxFeeAmount() != null
                && activeConfig.getMaxFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            finalFee = finalFee.min(activeConfig.getMaxFeeAmount());
        }

        // 5. Làm tròn LÊN về đồng nguyên (1.5đ → 2đ), sau đó đưa về scale 2 chuẩn
        finalFee = finalFee.setScale(0, RoundingMode.CEILING).setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);

        // =========================================================================
        //  CHỐT CHẶN AN TOÀN
        // Phí sàn TUYỆT ĐỐI KHÔNG BAO GIỜ được vượt quá giá bán.
        // → Bảo vệ hệ thống khỏi lỗi admin gõ nhầm feeRate (ví dụ 5.0 thay vì 0.05)
        // → Đảm bảo sellerNetAmount >= 0 trong MỌI trường hợp
        // =========================================================================
        finalFee = finalFee.min(saleAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

        // 6. Tiền thực nhận của Shop — bất biến: fee + net = saleAmount
        BigDecimal sellerNetAmount = saleAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP).subtract(finalFee);

        return FeeResult.builder()
                .feeConfigId(activeConfig.getId())
                .feeRateSnapshot(activeConfig.getFeeRate())
                .feeAmount(finalFee)
                .sellerNetAmount(sellerNetAmount)
                .build();
    }
}
