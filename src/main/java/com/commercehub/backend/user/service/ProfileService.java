package com.commercehub.backend.user.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.service.OrderStatisticsService;
import com.commercehub.backend.user.dto.request.UpdateAvatarRequest;
import com.commercehub.backend.user.dto.request.UpdateProfileRequest;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.mapper.UserMapper;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final OrderStatisticsService orderStatisticsService;


    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return buildProfileResponse(user);
    }

    @Transactional
    public ProfileResponse updateMyProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String newPhone = request.getPhone().trim();

            if (!newPhone.equals(user.getPhone())) {
                userRepository.findByPhone(newPhone).ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(user.getId())) {
                        throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
                    }
                });
                user.setPhone(newPhone);
                user.setIsPhoneVerified(false);
            }
        }

        if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }

        userRepository.save(user);
        return buildProfileResponse(user);
    }

    @Transactional
    public ProfileResponse updateAvatar(String email, UpdateAvatarRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }

        userRepository.save(user);
        return buildProfileResponse(user);
    }

    private ProfileResponse buildProfileResponse(User user) {
        long completedPurchaseCount = orderStatisticsService.countCompletedPurchases(user.getId());
        long successfulSaleCount = orderStatisticsService.countSuccessfulSalesByOwner(user.getId());

        return userMapper.toProfileResponse(user, completedPurchaseCount, successfulSaleCount);
    }
}
