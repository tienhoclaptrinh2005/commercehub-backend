package com.commercehub.backend.product.service;

import com.commercehub.backend.product.entity.AssetAccessLog;
import com.commercehub.backend.product.entity.DigitalAsset;
import com.commercehub.backend.product.repository.AssetAccessLogRepository;
import com.commercehub.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssetAccessLogService {

    private final AssetAccessLogRepository accessLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccess(DigitalAsset asset, User user, String ipAddress, String userAgent) {
        AssetAccessLog log = AssetAccessLog.builder()
                .asset(asset)
                .user(user)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        accessLogRepository.save(log);
    }
}