package com.commercehub.backend.user.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.service.OrderStatisticsService;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.event.AvatarImageReplacedEvent;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.user.dto.request.UpdateProfileRequest;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.mapper.UserMapper;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final OrderStatisticsService orderStatisticsService;
    private final ShopRepository shopRepository;
    private final MediaUrlService mediaUrlService;
    private final ApplicationEventPublisher eventPublisher;


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
            String nextFullName = request.getFullName().trim();
            if (user.hasRole("SELLER") && !nextFullName.equals(user.getFullName())) {
                throw new AppException(ErrorCode.SELLER_IDENTITY_LOCKED);
            }
            user.setFullName(nextFullName);
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

        userRepository.save(user);
        return buildProfileResponse(user);
    }

    @Transactional
    public ProfileResponse replaceAvatar(Long userId, String objectKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        String normalizedObjectKey = mediaUrlService.normalizeOwnedAvatarImageReference(objectKey, userId);
        String oldReference = user.getAvatarUrl();
        user.setAvatarUrl(normalizedObjectKey);
        userRepository.save(user);

        if (oldReference != null
                && !oldReference.equals(normalizedObjectKey)
                && mediaUrlService.isOwnedAvatarImageReference(oldReference, userId)) {
            eventPublisher.publishEvent(new AvatarImageReplacedEvent(userId, oldReference));
        }
        return buildProfileResponse(user);
    }

    private ProfileResponse buildProfileResponse(User user) {
        long completedPurchaseCount = orderStatisticsService.countCompletedPurchases(user.getId());
        long successfulSaleCount = orderStatisticsService.countSuccessfulSalesByOwner(user.getId());

        ProfileResponse response = userMapper.toProfileResponse(
                user,
                completedPurchaseCount,
                successfulSaleCount
        );
        response.setAvatarUrl(mediaUrlService.toPublicUrl(user.getAvatarUrl()));
        shopRepository.findByOwnerId(user.getId()).ifPresent(shop -> {
            response.setShopId(shop.getId());
            response.setShopStatus(shop.getStatus());
        });
        return response;
    }
}
