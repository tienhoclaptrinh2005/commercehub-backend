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

public class LevelConfig {

    @Id
    private Integer level;

    @Column(nullable = false , length =  50)
    private String label; // level  1 , 2 , 4 ,5 ...

    @Column(name = "min_spent" , nullable = false)
    private BigDecimal minSpent;        //số tiền chi tiêu tối thiểu


    @Column(name="allowed_shop_count" , nullable = false )
    private Integer allowedShopCount; // số lg gian hàng shop đc phép mở

    private String description;


}
