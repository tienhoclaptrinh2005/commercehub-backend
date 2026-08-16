package com.commercehub.backend.user.mapper;

import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.dto.response.UserLevelResponse;
import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.entity.LevelConfig;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {


    @Mapping(target = "userLevel", source = "userLevel.level")
    @Mapping(target = "roles", source = "roles", qualifiedByName = "toRoleNames")
    UserResponse toUserResponse(User user);

    @Mapping(target = "userLevel", source = "user.userLevel.level")
    @Mapping(target = "completedPurchaseCount", source = "completedPurchaseCount")
    @Mapping(target = "successfulSaleCount", source = "successfulSaleCount")
    @Mapping(target = "roles", source = "user.roles", qualifiedByName = "toRoleNames")
    UserResponse toUserResponse(
            User user,
            long completedPurchaseCount,
            long successfulSaleCount
    );

    @Mapping(target = "userLevel", source = "userLevel.level")
    @Mapping(target = "usernameChangeAllowed", expression = "java(user.getUsernameChangedAt() == null)")
    @Mapping(target = "roles", source = "roles", qualifiedByName = "toRoleNames")
    ProfileResponse toProfileResponse(User user);

    @Mapping(target = "userLevel", source = "user.userLevel.level")
    @Mapping(target = "completedPurchaseCount", source = "completedPurchaseCount")
    @Mapping(target = "successfulSaleCount", source = "successfulSaleCount")
    @Mapping(target = "usernameChangeAllowed", expression = "java(user.getUsernameChangedAt() == null)")
    @Mapping(target = "roles", source = "user.roles", qualifiedByName = "toRoleNames")
    ProfileResponse toProfileResponse(
            User user,
            long completedPurchaseCount,
            long successfulSaleCount
    );

    UserLevelResponse toUserLevelResponse(LevelConfig config);

    @Named("toRoleNames")
    default Set<String> toRoleNames(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }

        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
}
