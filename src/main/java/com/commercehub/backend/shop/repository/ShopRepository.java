package com.commercehub.backend.shop.repository;

import com.commercehub.backend.shop.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    Optional<Shop> findBySlug(String slug);
    boolean existsByName(String name);
    boolean existsBySlug(String slug);


    long countByOwnerId(Long ownerId);

    List<Shop> findAllByOwnerId(Long ownerId);
    Page<Shop> findByStatus(String status, Pageable pageable);

}