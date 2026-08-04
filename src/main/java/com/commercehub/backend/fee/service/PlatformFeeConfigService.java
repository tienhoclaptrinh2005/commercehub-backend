package com.commercehub.backend.fee.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.fee.dto.request.CreateFeeConfigRequest;
import com.commercehub.backend.fee.dto.request.UpdateFeeConfigRequest;
import com.commercehub.backend.fee.dto.response.FeeConfigResponse;
import com.commercehub.backend.fee.entity.PlatformFeeConfig;
import com.commercehub.backend.fee.mapper.FeeMapper;
import com.commercehub.backend.fee.repository.PlatformFeeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformFeeConfigService {

    private final PlatformFeeConfigRepository configRepository;
    private final FeeMapper feeMapper;

    @Transactional(readOnly = true)
    public List<FeeConfigResponse> getAllConfigs() {
        return configRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(feeMapper::toFeeConfigResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeeConfigResponse getActiveConfig() {
        PlatformFeeConfig config = configRepository.findByIsActiveTrue()
                .orElseThrow(() -> new AppException(ErrorCode.FEE_CONFIG_NOT_FOUND));
        return feeMapper.toFeeConfigResponse(config);
    }

    @Transactional
    public FeeConfigResponse createConfig(CreateFeeConfigRequest request, Long adminId) {
        // Validate chéo min/max
        if (request.getMinFeeAmount() != null && request.getMaxFeeAmount() != null
                && request.getMinFeeAmount().compareTo(request.getMaxFeeAmount()) > 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        // Deactivate config hiện tại bằng UPDATE nguyên tử (chống race 2 admin cùng tạo).
        // DB còn có partial unique index (is_active = true) làm chốt chặn cuối.
        int deactivated = configRepository.deactivateAllActive(OffsetDateTime.now());
        if (deactivated > 0) {
            log.info("Deactivated {} config phí cũ.", deactivated);
        }

        PlatformFeeConfig newConfig = PlatformFeeConfig.builder()
                .feeRate(request.getFeeRate())
                .minFeeAmount(request.getMinFeeAmount() != null
                        ? request.getMinFeeAmount() : java.math.BigDecimal.ZERO)
                .maxFeeAmount(request.getMaxFeeAmount())
                .description(request.getDescription())
                .isActive(true)
                .effectiveFrom(OffsetDateTime.now())
                .createdBy(adminId)
                .build();

        newConfig = configRepository.save(newConfig);
        log.info("Tạo config phí mới ID={}, feeRate={}%", newConfig.getId(),
                newConfig.getFeeRate().multiply(java.math.BigDecimal.valueOf(100)));
        return feeMapper.toFeeConfigResponse(newConfig);
    }


    @Transactional
    public FeeConfigResponse changeRate(UpdateFeeConfigRequest request, Long adminId) {
        CreateFeeConfigRequest createReq = CreateFeeConfigRequest.builder()
                .feeRate(request.getNewFeeRate())
                .minFeeAmount(request.getNewMinFeeAmount())
                .maxFeeAmount(request.getNewMaxFeeAmount())
                .description(request.getChangeReason())
                .build();
        return createConfig(createReq, adminId);
    }
}
