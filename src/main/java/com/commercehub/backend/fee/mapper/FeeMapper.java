package com.commercehub.backend.fee.mapper;

import com.commercehub.backend.fee.entity.PlatformFeeConfig;
import com.commercehub.backend.fee.entity.PlatformFeeLedger;
import com.commercehub.backend.fee.entity.ShopFeeSummary;
// import response DTOs khi tạo xong
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeeMapper {

}
