package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProductReviewVisibilityRequest {

    @NotNull(message = "Trạng thái hiển thị đánh giá không được để trống!")
    private Boolean visible;
}
