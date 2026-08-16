package com.commercehub.backend.category.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    Category parent;

    @Builder.Default
    @OneToMany(mappedBy = "parent")
    List<Category> children = new ArrayList<>();

    @Builder.Default
    @Column(name = "is_active", columnDefinition = "boolean default true")
     Boolean isActive = true;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
     Integer sortOrder = 0;

}
