package com.commercehub.backend.user.service;

import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.mapper.UserMapper;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void updateUsername(Long userId, String newUsername) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));


        if (newUsername == null || newUsername.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_USERNAME_FORMAT);
        }

        String sanitizedUsername = newUsername.trim().toLowerCase().replaceAll("[^a-zA-Z0-9_.]", "");

        if (sanitizedUsername.length() < 3 || sanitizedUsername.length() > 100) {
            throw new AppException(ErrorCode.INVALID_USERNAME_FORMAT);
        }

        if (!sanitizedUsername.equals(user.getUsername())) {

            if (userRepository.existsByUsername(sanitizedUsername)) {
                throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }

            user.setUsername(sanitizedUsername);
            userRepository.save(user);
        }



    }


}