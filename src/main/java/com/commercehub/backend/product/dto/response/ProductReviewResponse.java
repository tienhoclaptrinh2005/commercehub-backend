package com.commercehub.backend.product.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductReviewResponse {
    Long id;
    Long productId;
    Long userId;
    String reviewerName;
    String reviewerAvatar;
    Integer rating;
    String comment;
    Boolean isVisible;
    OffsetDateTime createdAt;
}
