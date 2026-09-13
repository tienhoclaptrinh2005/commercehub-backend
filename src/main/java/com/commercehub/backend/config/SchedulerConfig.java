package com.commercehub.backend.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Bật tính năng @Scheduled cho toàn bộ ứng dụng + ShedLock distributed lock.
 *
 * ShedLock đảm bảo khi chạy NHIỀU instance của app, mỗi job định kỳ
 * (nhả tiền T+7, auto-cancel đơn quá hạn) chỉ được đúng 1 instance thực thi —
 * tránh xử lý tiền trùng lặp.
 *
 * Yêu cầu bảng "shedlock" tồn tại trong DB (xem sql/2026-08-money-fixes.sql).
 */
@Configuration
@ConditionalOnProperty(name = "spring.task.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // Dùng giờ của DB để các instance đồng bộ tuyệt đối
                        .build()
        );
    }
}
