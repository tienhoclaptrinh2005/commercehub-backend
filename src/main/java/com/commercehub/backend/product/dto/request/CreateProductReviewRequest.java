package com.commercehub.backend.product.dto.request;

import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateProductReviewRequest {

    @NotNull(message = "ID Sản phẩm không được để trống!")
    Long productId;

    @NotNull(message = "Vui lòng chọn số sao đánh giá!")
    @Min(value = 1, message = "Đánh giá tối thiểu là 1 sao!")
    @Max(value = 5, message = "Đánh giá tối đa là 5 sao!")
    Integer rating;

    @Size(max = 1000, message = "Nội dung bình luận không được vượt quá 1000 ký tự!")
    String comment;

    @NotNull(message = "Order item dùng để đánh giá không được để trống!")
    Long orderItemId;
}
