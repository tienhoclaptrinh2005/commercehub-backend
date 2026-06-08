package com.commercehub.backend.user.service;

import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.mapper.UserMapper;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toUserResponse)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại!"));
    }
}