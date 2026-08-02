package com.commercehub.backend.order.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderStatusLogResponse {
    Long id;
    String fromStatus;
    String toStatus;
    Long changedBy;
    String note;
    OffsetDateTime createdAt;
}