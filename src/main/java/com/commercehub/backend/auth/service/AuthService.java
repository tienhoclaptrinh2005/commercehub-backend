package com.commercehub.backend.auth.service;

import com.commercehub.backend.auth.dto.request.*;
import com.commercehub.backend.auth.dto.response.AuthResponse;
import com.commercehub.backend.auth.entity.RefreshToken;
import com.commercehub.backend.auth.repository.RefreshTokenRepository;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.security.JwtTokenProvider;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.RoleRepository;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
            throw new RuntimeException("Email đã được sử dụng!");
        }
        User newUser = authMapper.toUserEntity(request);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        Role buyerRole = roleRepository.findByName("BUYER")
                .orElseThrow(() -> new RuntimeException("Lỗi cấu hình: Không tìm thấy quyền BUYER trong hệ thống!"));

        if (newUser.getRoles() == null) {
            newUser.setRoles(new java.util.HashSet<>());
        }
        newUser.getRoles().add(buyerRole);


        userRepository.save(newUser);

        // Đăng ký xong  tự động login luôn
        return login(new LoginRequest() {{
            setEmail(request.getEmail());
            setPassword(request.getPassword());
        }});
    }


    public AuthResponse login(LoginRequest request) {

        // xác minh tài khoản
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        // tạo token ngắn hạn
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        // taoj refresh Token dài hạn và lưu vào db
        String refreshTokenString = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenString)
                .deviceId(request.getDeviceId())
                .expiresAt(OffsetDateTime.now().plusDays(7)) // sống 7 day
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

    // hàm đổi token mới
    @Transactional
    public  AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Refresh Token không hợp lệ hoặc đã bị thu hồi "));
        if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new RuntimeException("Refresh Token đã hết hạn . Vui Lòng đăng nhập lại !");
        }

        User user = refreshToken.getUser();

        user.setLastActiveAt(OffsetDateTime.now());
        userRepository.save(user);

        String newAccessToken = jwtTokenProvider.generateTokenFromUsername(user.getEmail());

        return  AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }


    // đăh xuất
    @Transactional
    public void logout(LogoutRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Token không hợp lệ!"));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

    }


    @Transactional
    public void changePassword(String email , ChangePasswordRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu xác nhận không khớp !");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(()-> new RuntimeException("Không tìm thấy người dùng !"));

        if (!passwordEncoder.matches(request.getOldPassword() , user.getPasswordHash())){
            throw new RuntimeException("Mật khẩu cũ không chính xác ");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

    }


    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        try {
            // 1. Cấu hình máy giải mã Token của Google
            // (Lưu ý: Thay "CLIENT_ID_CUA_BAN" bằng ID dự án trên Google Cloud của bạn sau này)
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList("CLIENT_ID_CUA_BAN"))
                    .build();

            // 2. Kiểm tra tính hợp lệ của Token
            GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken == null) {
                throw new RuntimeException("Token Google không hợp lệ hoặc đã hết hạn!");
            }

            // 3. Rút trích thông tin từ Token chuẩn của Google
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            // 4. KỊCH BẢN HYBRID: Kiểm tra xem Email này đã tồn tại trong DB chưa
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                // TÌNH HUỐNG A: Lần đầu tiên tới hệ thống -> Tự động đăng ký
                user = new User();
                user.setEmail(email);
                user.setFullName(name != null ? name : "Người dùng Google");
                user.setAvatarUrl(pictureUrl);

                // Dùng cỗ máy tự động sinh Username (Cắt từ email)
                user.setUsername(generateUniqueUsername(email));

                // Sinh mật khẩu ngẫu nhiên dài loằng ngoằng (để bypass việc bắt buộc có pass)
                // Khách hàng có thể dùng tính năng "Quên mật khẩu" sau này để đổi cái pass này
                user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

                // Cấp quyền BUYER mặc định
                Role buyerRole = roleRepository.findByName("BUYER")
                        .orElseThrow(() -> new RuntimeException("Lỗi cấu hình: Không tìm thấy quyền BUYER!"));
                user.setRoles(new java.util.HashSet<>(java.util.List.of(buyerRole)));

                userRepository.save(user); // Lưu khách mới vào DB
            } else {
                // TÌNH HUỐNG B: Khách đã có tài khoản (Đăng ký tay hoặc từng Đăng nhập Google rồi)
                // -> Không làm gì cả, cứ thế đi tiếp xuống bước 5 để cấp Token!
            }

            // 5. Sinh hệ thống Token (Access + Refresh) của riêng CommerceHub cho User này
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

            // 6. Trả về kết quả cho Frontend
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

    // --- HÀM PHỤ TRỢ: Tự động sinh Username ---
    private String generateUniqueUsername(String email) {
        String baseName = email.contains("@") ? email.substring(0, email.indexOf('@')) : "user";
        baseName = baseName.replaceAll("[^a-zA-Z0-9]", "");

        String generatedUsername = baseName + "_" + (1000 + new java.util.Random().nextInt(9000));

        // Vòng lặp kiểm tra: Lỡ xui xẻo trùng với người có sẵn thì quay random lại
        while (userRepository.existsByUsername(generatedUsername)) {
            generatedUsername = baseName + "_" + (1000 + new java.util.Random().nextInt(9000));
        }

        return generatedUsername;
    }



}