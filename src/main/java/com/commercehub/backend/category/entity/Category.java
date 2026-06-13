package com.commercehub.backend.category.entity;

import com.commercehub.backend.common.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Category  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
     Long id;

    @Column(nullable = false , unique = true, length = 100)
     String name;

    @Column(nullable = false , unique = true  , length = 150)
     String slug;


    @Column(name = "icon_url")
     String iconUrl;

    @Column(name = "is_active", columnDefinition = "boolean default true")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

}
