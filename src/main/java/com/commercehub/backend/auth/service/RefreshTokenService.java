package com.commercehub.backend.auth.service;

import com.commercehub.backend.auth.entity.RefreshToken;
import com.commercehub.backend.auth.repository.RefreshTokenRepository;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${jwt.refreshExpiration}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public RefreshToken createRefreshToken(Long userId , String deviceId , String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng "));

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshTokenDurationMs / 1000))
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    // tìm token trong db
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByTokenAndRevokedFalse(token);
    }

    //check hạn token
    public RefreshToken verifyExpiration(RefreshToken token){
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new  RuntimeException("Refresh token đã hết hạn . Vui lòng đăng nhập lại ");
        }
        return token;
    }

    // thu hồi token khi đăng xuất
    @Transactional

    public void revokeToken(String tokenString) {
        refreshTokenRepository.findByTokenAndRevokedFalse(tokenString).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

}