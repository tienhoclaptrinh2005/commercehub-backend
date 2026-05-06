package com.commercehub.backend.user.repository;

import com.commercehub.backend.user.entity.UserRole;
import com.commercehub.backend.user.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {


}