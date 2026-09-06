package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.wallet.service.SePayWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/sepay")
@RequiredArgsConstructor
public class SePayWebhookController {

    private final SePayWebhookService sePayWebhookService;

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Boolean>> receiveWebhook(
            @RequestHeader("X-SePay-Timestamp") String timestamp,
            @RequestHeader("X-SePay-Signature") String signature,
            @RequestBody byte[] rawBody) {
        sePayWebhookService.process(timestamp, signature, rawBody);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
