package com.commercehub.backend.admin.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.dispute.dto.request.AdminResolveDisputeRequest;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.dto.response.AdminDisputeSummaryResponse;
import com.commercehub.backend.dispute.service.DisputeResolutionService;
import com.commercehub.backend.admin.service.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Validated
public class AdminDisputeController {

    private final DisputeResolutionService disputeResolutionService;
    private final AdminAuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DisputeResponse>>> list(
            @RequestParam(defaultValue = "QUEUE")
            String scope,
            @RequestParam(required = false)
            String status,
            @RequestParam(required = false)
            @Size(max = 50, message = "Từ khóa tìm kiếm tối đa 50 ký tự")
            String keyword,
            @RequestParam(defaultValue = "false")
            boolean overdue,
            Pageable pageable
    ) {

        Page<DisputeResponse> disputes = disputeResolutionService.getAll(
                        scope,
                        status,
                        keyword,
                        overdue,
                        pageable
                );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(disputes)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminDisputeSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(disputeResolutionService.getSummary()));
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<ApiResponse<DisputeResponse>> detail(
            @PathVariable Long disputeId
    ) {

        return ResponseEntity.ok(ApiResponse.success(
                disputeResolutionService.getById(
                        disputeId
                )
        ));
    }

    @PostMapping("/{disputeId}/resolve")
    @Transactional
    public ResponseEntity<ApiResponse<DisputeResponse>> resolve(
            @PathVariable Long disputeId,
            @Valid
            @RequestBody
            AdminResolveDisputeRequest request,
            HttpServletRequest http
    ) {

        Long adminId =
                SecurityUtils.getCurrentUserId();

        DisputeResponse before = disputeResolutionService.getById(disputeId);
        DisputeResponse resolved = disputeResolutionService.resolve(adminId, disputeId, request);
        auditService.record(adminId, "DISPUTE_RESOLVED", "DISPUTE", disputeId,
                java.util.Map.of("status", before.getStatus().name()),
                java.util.Map.of("status", resolved.getStatus().name(), "resolution", resolved.getResolution().name()),
                request.resolutionNote(), clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Đã giải quyết tranh chấp!", resolved));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
