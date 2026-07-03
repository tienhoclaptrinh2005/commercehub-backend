package com.commercehub.backend.product.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PreOrderConfigResponse {

    Long id;

    Long productId;

    Integer maxProcessingHours;

    String orderInstructions;

    String buyerInputFields;

    Boolean autoRejectIfUnavailable;
}