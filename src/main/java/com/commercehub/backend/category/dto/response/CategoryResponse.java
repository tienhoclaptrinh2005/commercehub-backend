package com.commercehub.backend.category.dto.response;


import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryResponse {
     Long id;
     String name;
     String slug;
     String iconUrl;
     Boolean isActive;
     Integer sortOrder;
     Long parentId;
     String parentName;
     @Builder.Default
     List<CategoryResponse> children = new ArrayList<>();

}
