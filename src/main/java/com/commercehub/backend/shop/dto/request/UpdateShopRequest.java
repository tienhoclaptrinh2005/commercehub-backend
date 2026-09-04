package com.commercehub.backend.shop.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateShopRequest {

    @Size(min = 5, max = 255, message = "Tên gian hàng phải từ 5 đến 255 ký tự!")
    private String name;

    private String description;
    private String shopCoverUrl;

}
