package com.commercehub.backend.auth.service;

import com.commercehub.backend.auth.dto.request.*;
import com.commercehub.backend.auth.dto.response.AuthResponse;
import com.commercehub.backend.auth.entity.RefreshToken;
import com.commercehub.backend.auth.repository.RefreshTokenRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.security.JwtTokenProvider;
import com.commercehub.backend.user.entity.LevelConfig;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.LevelConfigRepository;
import com.commercehub.backend.user.repository.RoleRepository;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // Thêm dòng này
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.commercehub.backend.auth.mapper.AuthMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.util.Collections;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;
    private final RoleRepository roleRepository;
    private final LevelConfigRepository levelConfigRepository;

    @Value("${google.client-id:xxxxxxxx.googleusercontent.com}")
    private String googleClientId;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User newUser = authMapper.toUserEntity(request);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setUsername(generateUniqueUsername(request.getEmail()));

        Role buyerRole = roleRepository.findByName("BUYER")
                .orElseThrow(() -> {
                    log.error("CRITICAL ERROR: Không tìm thấy quyền 'BUYER' trong bảng Roles khi đăng ký User mới!");
                    return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                });

        if (newUser.getRoles() == null) {
            newUser.setRoles(new java.util.HashSet<>());
        }
        newUser.getRoles().add(buyerRole);

        LevelConfig defaultLevel = levelConfigRepository.findById(1)
                .orElseThrow(() -> {
                    log.error("CRITICAL ERROR: Không tìm thấy Level 1 (Mặc định) trong bảng level_configs!");
                    return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                });
        newUser.setUserLevel(defaultLevel);

        newUser.setLastActiveAt(OffsetDateTime.now());

        userRepository.save(newUser);


        Authentication authentication = createAuthentication(newUser);
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        String refreshTokenString = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(newUser)
                .token(refreshTokenString)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenString)
                .userId(newUser.getId())
                .username(newUser.getUsername())
                .email(newUser.getEmail())
                .fullName(newUser.getFullName())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        String refreshTokenString = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenString)
                .deviceId(request.getDeviceId())
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        user.setLastActiveAt(OffsetDateTime.now());
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenString)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken oldRefreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (oldRefreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            oldRefreshToken.setRevoked(true);
            refreshTokenRepository.save(oldRefreshToken);
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = oldRefreshToken.getUser();

        oldRefreshToken.setRevoked(true);
        refreshTokenRepository.save(oldRefreshToken);

        String newRefreshTokenString = UUID.randomUUID().toString();
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshTokenString)
                .deviceId(oldRefreshToken.getDeviceId())
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshToken);

        user.setLastActiveAt(OffsetDateTime.now());
        userRepository.save(user);

        Authentication authentication = createAuthentication(user);
        String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenString)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    @Transactional
    public void logout(LogoutRequest request, Long currentUserId) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!refreshToken.getUser().getId().equals(currentUserId)) {
            log.warn("Cảnh báo bảo mật: User ID {} cố gắng thu hồi Refresh Token của User ID {}", currentUserId, refreshToken.getUser().getId());
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }



    @Transactional
    public void changePassword(String identifier, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        if (request.getNewPassword().equals(request.getOldPassword())) {
            throw new AppException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if ("GOOGLE".equals(user.getProvider())) {
            throw new AppException(ErrorCode.CANNOT_CHANGE_GOOGLE_PASSWORD);
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUser(user);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken == null) {
                log.warn("Cảnh báo: Token Google không hợp lệ hoặc đã hết hạn được gửi lên!");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                Role buyerRole = roleRepository.findByName("BUYER")
                        .orElseThrow(() -> {
                            log.error("CRITICAL ERROR (Google Login): Không tìm thấy quyền 'BUYER' trong bảng Roles!");
                            return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                        });
                LevelConfig defaultLevel = levelConfigRepository.findById(1)
                        .orElseThrow(() -> {
                            log.error("CRITICAL ERROR (Google Login): Không tìm thấy Level 1 trong bảng level_configs!");
                            return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                        });
                user = User.builder()
                        .email(email)
                        .fullName(name != null ? name : "Người dùng Google")
                        .avatarUrl(pictureUrl)
                        .username(generateUniqueUsername(email))
                        .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .roles(new java.util.HashSet<>(java.util.List.of(buyerRole)))
                        .userLevel(defaultLevel)
                        .status("ACTIVE")
                        .isEmailVerified(true)
                        .provider("GOOGLE")
                        .build();

                userRepository.save(user);
            } else {
                if (!"ACTIVE".equals(user.getStatus())) {
                    throw new AppException(ErrorCode.UNAUTHORIZED);
                }
            }

            Authentication authentication = createAuthentication(user);
            String accessToken = jwtTokenProvider.generateAccessToken(authentication);

            String refreshTokenString = UUID.randomUUID().toString();
            RefreshToken refreshToken = RefreshToken.builder()
                    .user(user)
                    .token(refreshTokenString)
                    .deviceId(request.getDeviceId())
                    .expiresAt(OffsetDateTime.now().plusDays(7))
                    .revoked(false)
                    .build();
            refreshTokenRepository.save(refreshToken);

            user.setLastActiveAt(OffsetDateTime.now());
            userRepository.save(user);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshTokenString)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .build();
        } catch (AppException e) {
            throw e;

        } catch (Exception e) {
            log.error("Lỗi xác thực Google không xác định: ", e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    private String generateUniqueUsername(String email) {
        String baseName = email.contains("@") ? email.substring(0, email.indexOf('@')) : "user";
        baseName = baseName.replaceAll("[^a-zA-Z0-9]", "");

        String uniqueSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        String username =  baseName + "_" + uniqueSuffix; // VD: tienvu_8a7b6c5d

        while (userRepository.existsByUsername(username)) {
            uniqueSuffix = java.util.UUID.randomUUID().toString().substring(0, 8);
            username = baseName + "_" + uniqueSuffix;
        }

        return username;
    }


    private Authentication createAuthentication(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());
        CustomUserDetails userDetails = new CustomUserDetails(user,authorities,user.getId());

        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

}