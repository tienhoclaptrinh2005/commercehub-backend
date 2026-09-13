package com.commercehub.backend.admin.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public void record(Long actorId, String action, String targetType, Long targetId,
                       Map<String, ?> oldValue, Map<String, ?> newValue, String reason,
                       String ipAddress, String userAgent) {
        User actor = userRepository.findByIdWithRoles(actorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String role = actor.getRoles().stream().findFirst().map(r -> r.getName()).orElse("UNKNOWN");
        jdbc.update("""
                INSERT INTO audit_logs(actor_id,actor_role,action,target_type,target_id,old_value,new_value,reason,ip_address,user_agent)
                VALUES (?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?)
                """, actorId, role, action, targetType, targetId, json(oldValue), json(newValue),
                blankToNull(reason), ipAddress, userAgent);
    }

    private String json(Map<String, ?> value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
