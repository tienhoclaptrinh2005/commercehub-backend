package com.commercehub.backend.user.repository;

import com.commercehub.backend.user.entity.LevelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LevelConfigRepository extends JpaRepository<LevelConfig, Integer> {

}