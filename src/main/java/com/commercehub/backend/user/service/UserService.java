package com.commercehub.backend.user.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.service.OrderStatisticsService;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.mapper.UserMapper;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_.]{3,100}$");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final OrderStatisticsService orderStatisticsService;
    private final ShopRepository shopRepository;

    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        long completedPurchaseCount = orderStatisticsService.countCompletedPurchases(user.getId());
        long successfulSaleCount = orderStatisticsService.countSuccessfulSalesByOwner(user.getId());

        UserResponse response = userMapper.toUserResponse(
                user,
                completedPurchaseCount,
                successfulSaleCount
        );

        shopRepository.findByOwnerId(user.getId())
                .filter(shop -> "ACTIVE".equals(shop.getStatus()))
                .map(Shop::getId)
                .ifPresent(response::setShopId);

        return response;
    }

    @Transactional
    public void updateUsername(Long userId, String newUsername) {
        if (newUsername == null) {
            throw new AppException(ErrorCode.INVALID_USERNAME_FORMAT);
        }

        String normalizedUsername = newUsername.trim().toLowerCase(Locale.ROOT);

        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new AppException(ErrorCode.INVALID_USERNAME_FORMAT);
        }

        User user = userRepository.findByIdForUsernameUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Gửi lại đúng username hiện tại là thao tác không thay đổi và không tiêu thụ lượt đổi.
        if (normalizedUsername.equals(user.getUsername())) {
            return;
        }

        if (user.getUsernameChangedAt() != null) {
            throw new AppException(ErrorCode.USERNAME_CHANGE_LIMIT_REACHED);
        }

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        user.setUsername(normalizedUsername);
        user.setUsernameChangedAt(OffsetDateTime.now());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Unique constraint là lớp bảo vệ cuối khi hai tài khoản chọn cùng username đồng thời.
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
    }
}
