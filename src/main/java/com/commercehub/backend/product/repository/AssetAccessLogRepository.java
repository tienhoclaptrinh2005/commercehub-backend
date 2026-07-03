package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.AssetAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetAccessLogRepository extends JpaRepository<AssetAccessLog, Long> {
}