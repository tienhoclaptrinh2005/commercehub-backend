package com.commercehub.backend.order.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import com.commercehub.backend.order.entity.OrderStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderStatusLogResponse {
    Long id;
    OrderStatus fromStatus;
    OrderStatus toStatus;
    Long changedBy;
    String note;
    OffsetDateTime createdAt;
}
