package com.commercehub.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openApiPublishesNormalizedRoutes() throws Exception {
        String responseBody = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode paths = objectMapper.readTree(responseBody).path("paths");

        assertTrue(paths.has("/api/v1/checkout"));
        assertTrue(paths.has("/api/v1/seller/products/assets/inventory"));
        assertTrue(paths.has("/api/v1/admin/fee-configs"));
        assertTrue(paths.has("/api/v1/seller/fees"));
        assertTrue(paths.has("/api/v1/wallet/deposits"));
        assertTrue(paths.has("/api/v1/wallet/deposits/{transactionCode}"));
        assertTrue(paths.has("/api/v1/payments/sepay/webhook"));

        assertFalse(paths.has("/api/v1/checkout/checkout"));
        assertFalse(paths.has("/api/v1/seller/products/products/assets/inventory"));
        assertFalse(paths.has("/api/admin/fee-configs"));
        assertFalse(paths.has("/api/seller/fees"));
        assertFalse(paths.has("/api/v1/wallet/deposit/vnpay-ipn"));
        assertFalse(paths.has("/api/v1/wallet/deposit/sepay-ipn"));
    }

    @Test
    void publicUnknownResourceUsesStandardNotFoundEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/products/__missing__"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void invalidPathVariableUsesStandardBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/products/not-a-number/variants"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
