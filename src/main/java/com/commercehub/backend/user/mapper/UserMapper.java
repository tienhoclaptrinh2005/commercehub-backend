package com.commercehub.backend.user.mapper;

import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.dto.response.UserLevelResponse;
import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.entity.LevelConfig;
import com.commercehub.backend.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {


    @Mapping(target = "userLevel", source = "userLevel.level")
    UserResponse toUserResponse(User user);

    @Mapping(target = "userLevel", source = "userLevel.level")
    ProfileResponse toProfileResponse(User user);

    UserLevelResponse toUserLevelResponse(LevelConfig config);
}