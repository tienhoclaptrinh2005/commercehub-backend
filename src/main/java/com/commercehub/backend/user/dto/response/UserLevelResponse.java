package com.commercehub.backend.user.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserLevelResponse {
    Integer level;
    String label;
    BigDecimal minSpent;
    Integer allowedShopCount;
    String description;
}