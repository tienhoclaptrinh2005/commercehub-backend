package com.commercehub.backend.auth.service;

import com.commercehub.backend.auth.dto.request.*;
import com.commercehub.backend.auth.dto.response.AuthResponse;
import com.commercehub.backend.auth.entity.RefreshToken;
import com.commercehub.backend.auth.repository.RefreshTokenRepository;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.security.JwtTokenProvider;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.RoleRepository;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.commercehub.backend.auth.mapper.AuthMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;

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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User newUser = authMapper.toUserEntity(request);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setUsername(generateUniqueUsername(request.getEmail()));
        Role buyerRole = roleRepository.findByName("BUYER")
                .orElseThrow(() -> new RuntimeException("Lỗi cấu hình: Không tìm thấy quyền BUYER trong hệ thống!"));

        if (newUser.getRoles() == null) {
            newUser.setRoles(new java.util.HashSet<>());
        }
        newUser.getRoles().add(buyerRole);

        userRepository.save(newUser);


        return login(new LoginRequest() {{
            setEmail(request.getEmail());
            setPassword(request.getPassword());
        }});
    }

    public AuthResponse login(LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();


        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        // Tạo Refresh Token dài hạn và lưu vào DB
        String refreshTokenString = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenString)
                .deviceId(request.getDeviceId())
                .expiresAt(OffsetDateTime.now().plusDays(7)) // Hạn sống 7 ngày
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
                .orElseThrow(() -> new RuntimeException("Refresh Token không hợp lệ hoặc đã bị thu hồi"));

        if (oldRefreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            oldRefreshToken.setRevoked(true);
            refreshTokenRepository.save(oldRefreshToken);
            throw new RuntimeException("Refresh Token đã hết hạn. Vui lòng đăng nhập lại!");
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


        String newAccessToken = jwtTokenProvider.generateTokenFromUsername(user.getEmail());

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
    public void logout(LogoutRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Token không hợp lệ!"));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }


    @Transactional
    public void changePassword(String identifier, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu xác nhận không khớp!");
        }

        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Mật khẩu cũ không chính xác!");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        try {
            // 1. Cấu hình máy giải mã Token của Google
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList("CLIENT_ID_CUA_BAN"))
                    .build();

            // 2. Kiểm tra tính hợp lệ của Token từ Google gửi sang
            GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken == null) {
                throw new RuntimeException("Token Google không hợp lệ hoặc đã hết hạn!");
            }

            // 3. Rút trích thông tin
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            // 4. Kiểm tra xem Email này đã có tài khoản chưa
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                // TÌNH HUỐNG A: Tài khoản mới tinh -> Đăng ký tự động
                user = new User();
                user.setEmail(email);
                user.setFullName(name != null ? name : "Người dùng Google");
                user.setAvatarUrl(pictureUrl);
                user.setUsername(generateUniqueUsername(email));
                user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

                Role buyerRole = roleRepository.findByName("BUYER")
                        .orElseThrow(() -> new RuntimeException("Lỗi cấu hình: Không tìm thấy quyền BUYER!"));
                user.setRoles(new java.util.HashSet<>(java.util.List.of(buyerRole)));

                userRepository.save(user);
            } else {
                // TÌNH HUỐNG B: Tài khoản đã tồn tại -> Kiểm tra trạng thái trước khi cấp Token
                if (!"ACTIVE".equals(user.getStatus())) {
                    throw new RuntimeException("Tài khoản đã bị khóa!");
                }
            }

            // 5. Sinh hệ thống Token của CommerceHub (Đã đồng bộ dùng Username làm chủ thể)
            String accessToken = jwtTokenProvider.generateTokenFromUsername(user.getEmail());

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

        } catch (Exception e) {
            throw new RuntimeException("Lỗi xác thực Google: " + e.getMessage());
        }
    }


    private String generateUniqueUsername(String email) {
        String baseName = email.contains("@") ? email.substring(0, email.indexOf('@')) : "user";
        baseName = baseName.replaceAll("[^a-zA-Z0-9]", "");

        String generatedUsername = baseName + "_" + (1000 + new java.util.Random().nextInt(9000));

        while (userRepository.existsByUsername(generatedUsername)) {
            generatedUsername = baseName + "_" + (1000 + new java.util.Random().nextInt(9000));
        }

        return generatedUsername;
    }
}