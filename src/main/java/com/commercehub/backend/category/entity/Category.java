package com.commercehub.backend.category.entity;

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

    @Builder.Default
    @Column(name = "is_active", columnDefinition = "boolean default true")
     Boolean isActive = true;

    @Column(name = "sort_order")
     Integer sortOrder;

}
