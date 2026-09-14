package com.commercehub.backend.admin.service;

import com.commercehub.backend.dispute.service.DisputeResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional(readOnly = true)
class AdminQueryServiceIntegrationTest {
    @Autowired
    private AdminQueryService service;

    @Autowired
    private DisputeResolutionService disputeResolutionService;

    @Test
    void adminDashboardAndEveryInventoryQueryExecuteAgainstCurrentSchema() {
        assertThat(service.dashboard()).isNotNull();
        assertThat(service.users("", "", "", 0, 5).getData()).isNotNull();
        assertThat(service.shops("", "", 0, 5).getData()).isNotNull();
        assertThat(service.products("", "", null, null, 0, 5).getData()).isNotNull();
        assertThat(service.categories()).isNotNull();
        assertThat(service.deposits("", "", "", 0, 5).getData()).isNotNull();
        assertThat(service.withdrawals("", "", 0, 5).getData()).isNotNull();
        assertThat(service.transactions("", "", 0, 5).getData()).isNotNull();
        assertThat(service.auditLogs("", "", "", 0, 5).getData()).isNotNull();
        assertThat(disputeResolutionService.getAll(
                null,
                null,
                null,
                false,
                PageRequest.of(0, 5)
        ).getContent()).isNotNull();
    }
}
