package com.commercehub.backend.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "level_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LevelConfig {

    @Id
    private Integer level;

    @Column(nullable = false , length =  50)
    private String label; // level  1 , 2 , 4 ,5 ...

    @Column(name = "min_spent", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal minSpent = BigDecimal.ZERO;

    @Column(name = "allowed_product_count", nullable = false)
    @Builder.Default
    Integer allowedProductCount = 0;

    @Column(length = 255)
    private String description;


}
