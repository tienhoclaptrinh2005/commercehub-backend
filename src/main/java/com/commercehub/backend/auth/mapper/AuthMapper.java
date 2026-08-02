package com.commercehub.backend.auth.mapper;

import com.commercehub.backend.auth.dto.request.RegisterRequest;
import com.commercehub.backend.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthMapper {

    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "isEmailVerified", constant = "false")
    @Mapping(target = "isPhoneVerified", constant = "false")
    @Mapping(target = "accumulatedSpent", ignore = true)
    @Mapping(target = "accumulatedEarned", ignore = true)
    User toUserEntity(RegisterRequest request);

}