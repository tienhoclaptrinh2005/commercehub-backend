package com.commercehub.backend.user.service;

import com.commercehub.backend.user.dto.response.UserLevelResponse;
import com.commercehub.backend.user.mapper.UserMapper;
import com.commercehub.backend.user.repository.LevelConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserLevelService {

    private final LevelConfigRepository levelConfigRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public List<UserLevelResponse> getAllLevelConfigs() {
        return levelConfigRepository.findAll().stream()
                .map(userMapper::toUserLevelResponse)
                .collect(Collectors.toList());
    }
}