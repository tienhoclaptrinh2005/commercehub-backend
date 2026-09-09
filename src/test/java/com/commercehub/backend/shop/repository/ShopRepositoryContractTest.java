package com.commercehub.backend.shop.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import static org.assertj.core.api.Assertions.assertThat;

class ShopRepositoryContractTest {

    @Test
    void findByOwnerIdLoadsOwnerAndRolesBeforeTheRepositorySessionCloses() throws NoSuchMethodException {
        EntityGraph entityGraph = ShopRepository.class
                .getMethod("findByOwnerId", Long.class)
                .getAnnotation(EntityGraph.class);

        assertThat(entityGraph).isNotNull();
        assertThat(entityGraph.attributePaths())
                .containsExactlyInAnyOrder("owner", "owner.roles");
    }
}
