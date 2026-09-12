package com.commercehub.backend.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PresignAvatarImageRequest(
        @NotBlank(message = "Tên file không được để trống")
        @Size(max = 255, message = "Tên file tối đa 255 ký tự")
        String fileName,

        @NotBlank(message = "Content-Type không được để trống")
        @Size(max = 100, message = "Content-Type không hợp lệ")
        String contentType,

        @Positive(message = "Dung lượng file phải lớn hơn 0")
        long fileSize
) {
}
