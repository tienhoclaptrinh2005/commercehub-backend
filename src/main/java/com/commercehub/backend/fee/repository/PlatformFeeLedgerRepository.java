package com.commercehub.backend.fee.repository;

import com.commercehub.backend.fee.entity.PlatformFeeLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformFeeLedgerRepository extends JpaRepository<PlatformFeeLedger, Long> {
}