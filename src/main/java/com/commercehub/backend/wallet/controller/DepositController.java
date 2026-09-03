package com.commercehub.backend.wallet.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.wallet.dto.request.DepositRequest;
import com.commercehub.backend.wallet.dto.response.DepositResponse;
import com.commercehub.backend.wallet.service.DepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/wallet/deposit")
@RequiredArgsConstructor
public class DepositController {

    private final DepositService depositService;

    // Lấy Secret Key của VNPay từ file cấu hình (application.yml hoặc .env)
    @Value("${vnpay.hash-secret}")
    private String vnpayHashSecret;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DepositResponse>>> getDepositHistory(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<DepositResponse> history = depositService.getMyDeposits(currentUser.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử nạp tiền thành công", history));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createDepositUrl(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody DepositRequest request) {
        
        String txCode = "VNPAY_" + UUID.randomUUID().toString().replace("-", "");

        depositService.createPendingDeposit(currentUser.getId(), request.getAmount(), txCode);

        String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=" + txCode;

        return ResponseEntity.ok(ApiResponse.success("Tạo link nạp tiền thành công", paymentUrl));
    }


    @GetMapping("/vnpay-ipn")
    public ResponseEntity<ApiResponse<Void>> vnpayIpnCallback(@RequestParam Map<String, String> params) {
        try {

            String vnp_SecureHash = params.get("vnp_SecureHash");
            if (vnp_SecureHash == null) {
                log.error(" VNPay IPN: Từ chối giao dịch do thiếu chữ ký (vnp_SecureHash)!");
                return ResponseEntity.badRequest().body(null);
            }


            params.remove("vnp_SecureHash");
            params.remove("vnp_SecureHashType");

            String hashData = params.entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                    .collect(Collectors.joining("&"));

            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(vnpayHashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] hashBytes = hmac512.doFinal(hashData.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            String calculatedHash = sb.toString();

            if (!calculatedHash.equalsIgnoreCase(vnp_SecureHash)) {
                log.error(" VNPay IPN CẢNH BÁO: Chữ ký không khớp! Nghi ngờ có kẻ tấn công nạp tiền khống.");
                return ResponseEntity.badRequest().body(null);
            }



            String txnRef = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");

            if ("00".equals(responseCode)) {
                // ĐỐI CHIẾU SỐ TIỀN: vnp_Amount = số tiền thật × 100 (theo spec VNPay).
                // Thiếu hoặc lệch số tiền → tuyệt đối không cộng ví.
                String vnpAmountStr = params.get("vnp_Amount");
                if (vnpAmountStr == null || vnpAmountStr.isBlank()) {
                    log.error(" VNPay IPN: Thiếu vnp_Amount - TxnRef: {}. Từ chối xử lý.", txnRef);
                    return ResponseEntity.badRequest().body(null);
                }
                java.math.BigDecimal paidAmount = new java.math.BigDecimal(vnpAmountStr).movePointLeft(2);

                depositService.processSuccess(txnRef, paidAmount);
                log.info(" VNPay IPN: Nạp tiền thành công - TxnRef: {}", txnRef);
            } else {
                depositService.processFailed(txnRef);
                log.warn(" VNPay IPN: Nạp tiền thất bại - TxnRef: {}, ResponseCode: {}", txnRef, responseCode);
            }

            return ResponseEntity.ok(ApiResponse.success("IPN processed", null));

        } catch (Exception e) {
            log.error(" Lỗi hệ thống khi giải mã chữ ký VNPay IPN", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }
}
