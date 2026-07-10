package com.commercehub.backend.wallet.service;

import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldReleaseService {

    private final HoldReleaseRepository holdReleaseRepository;
    private final HoldReleaseProcessor holdReleaseProcessor;

    public void processDueReleases(OffsetDateTime now) {
        List<HoldRelease> dueReleases = holdReleaseRepository.findDueReleases(now);

        for (HoldRelease hr : dueReleases) {
            try {

                holdReleaseProcessor.processSingle(hr);
            } catch (Exception e) {

                log.error("Lỗi khi xử lý giải phóng tiền HoldRelease ID {}: {}", hr.getId(), e.getMessage());
            }
        }
    }
}