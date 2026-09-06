package com.commercehub.backend.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SePayCheckoutResponse {
    private String actionUrl;
    private String environment;
    private Map<String, String> fields;
}
