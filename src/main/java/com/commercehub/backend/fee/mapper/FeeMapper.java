package com.commercehub.backend.fee.mapper;

import com.commercehub.backend.fee.dto.response.*;
import com.commercehub.backend.fee.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeeMapper {

    FeeConfigResponse toFeeConfigResponse(PlatformFeeConfig config);

    FeeLedgerResponse toFeeLedgerResponse(PlatformFeeLedger ledger);

    FeeLogResponse toFeeLogResponse(PlatformFeeLog log);

    ShopFeeSummaryResponse toShopFeeSummaryResponse(ShopFeeSummary summary);

    FeeBreakdownResponse toFeeBreakdownResponse(PlatformFeeLedger ledger);
}
