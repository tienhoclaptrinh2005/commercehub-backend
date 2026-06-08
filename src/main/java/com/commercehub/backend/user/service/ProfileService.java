package com.commercehub.backend.user.service;


import com.commercehub.backend.user.dto.request.UpdateAvatarRequest;
import com.commercehub.backend.user.dto.request.UpdateProfileRequest;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.commercehub.backend.user.repository.UserRepository;
@Service
@RequiredArgsConstructor


public class ProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(String email){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nguười dùng !"));


        return ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .userLevel(user.getUserLevel())
                .accumulatedSpent(user.getAccumulatedSpent())
                .accumulatedEarned(user.getAccumulatedEarned())
                .isEmailVerified(user.getIsEmailVerified())
                .isPhoneVerified(user.getIsPhoneVerified())
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt())
                .build();
    }



    @Transactional
    public ProfileResponse updateMyProfile(String email , UpdateProfileRequest request){


        User user = userRepository.findByEmail(email)
                .orElseThrow(()-> new RuntimeException("Không tìm thấy thông tin người dùng !"));

        if (request.getFullName() != null  && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());

        }

        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String newPhone = request.getPhone().trim();
            userRepository.findByPhone(newPhone).ifPresent(existingUser-> {
                if (!existingUser.getId().equals(user.getId())){
                    throw new RuntimeException("Số đuện thoại đã được dùng bởi tài khoản khác  !");
                }
            });
            user.setPhone(newPhone);

        }

        if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        userRepository.save(user);

        return getMyProfile(email);




    }

    @Transactional
    public  ProfileResponse updateAvatar(String email , UpdateAvatarRequest request){

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin người dùng !"));

        if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }

        userRepository.save(user);

        return getMyProfile(email);


    }



}
