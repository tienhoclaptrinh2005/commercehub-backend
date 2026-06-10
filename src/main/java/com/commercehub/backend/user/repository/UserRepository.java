package com.commercehub.backend.user.repository;

import com.commercehub.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);

    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);




}