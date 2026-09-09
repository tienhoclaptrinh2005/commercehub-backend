package com.commercehub.backend.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteProductImageRequest(
        @NotBlank(message = "Object key không được để trống")
        @Size(max = 500, message = "Object key tối đa 500 ký tự")
        String objectKey
) {
}
