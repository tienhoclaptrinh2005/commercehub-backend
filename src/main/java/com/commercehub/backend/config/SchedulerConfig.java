package com.commercehub.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật tính năng @Scheduled cho toàn bộ ứng dụng.
 * Các lớp Scheduler (VD: HoldReleaseScheduler) sẽ dùng @Scheduled để chạy định kỳ.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
