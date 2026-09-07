package com.commercehub.backend.dashboard.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.dashboard.dto.response.SellerDashboardResponse;
import com.commercehub.backend.dashboard.service.SellerDashboardService;
import com.commercehub.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerDashboardController {

    private final SellerDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<SellerDashboardResponse>> getDashboard(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String month
    ) {
        SellerDashboardResponse response = dashboardService.getDashboard(currentUser.getId(), month);
        return ResponseEntity.ok(ApiResponse.success("Lấy tổng quan bán hàng thành công", response));
    }
}
