package com.commercehub.backend.shop.repository;


import com.commercehub.backend.shop.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    @EntityGraph(attributePaths = {"owner"})
    Optional<Shop> findBySlug(String slug);
    boolean existsByName(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsBySlug(String slug);

    Optional<Shop> findByOwnerId(Long ownerId);

    List<Shop> findAllByOwnerId(Long ownerId);


    @EntityGraph(attributePaths = {"owner"})
    Page<Shop> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT s FROM Shop s JOIN s.owner.roles ownerRole " +
            "WHERE s.status = 'ACTIVE' " +
            "AND s.owner.status = 'ACTIVE' " +
            "AND ownerRole.name = 'SELLER'")
    Page<Shop> findAllPublicActive(Pageable pageable);



    @Override
    @EntityGraph(attributePaths = {"owner"})
    Page<Shop> findAll(Pageable pageable);
}
