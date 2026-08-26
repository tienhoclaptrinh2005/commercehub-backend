package com.commercehub.backend.admin.controller;

import com.commercehub.backend.common.util.SecurityUtils;
import com.commercehub.backend.dispute.dto.request.AdminResolveDisputeRequest;
import com.commercehub.backend.dispute.dto.response.DisputeResponse;
import com.commercehub.backend.dispute.service.DisputeResolutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminDisputeController {

    private final DisputeResolutionService disputeResolutionService;

    @GetMapping
    public ResponseEntity<Page<DisputeResponse>> list(
            @RequestParam(required = false)
            String status,
            Pageable pageable
    ) {

        return ResponseEntity.ok(
                disputeResolutionService.getAll(
                        status,
                        pageable
                )
        );
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeResponse> detail(
            @PathVariable Long disputeId
    ) {

        return ResponseEntity.ok(
                disputeResolutionService.getById(
                        disputeId
                )
        );
    }

    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<DisputeResponse> resolve(
            @PathVariable Long disputeId,
            @Valid
            @RequestBody
            AdminResolveDisputeRequest request
    ) {

        Long adminId =
                SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(
                disputeResolutionService.resolve(
                        adminId,
                        disputeId,
                        request
                )
        );
    }
}
