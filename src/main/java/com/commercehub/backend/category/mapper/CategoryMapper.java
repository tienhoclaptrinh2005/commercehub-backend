package com.commercehub.backend.category.mapper;

import com.commercehub.backend.category.dto.request.CreateCategoryRequest;
import com.commercehub.backend.category.dto.request.UpdateCategoryRequest;
import com.commercehub.backend.category.dto.response.CategoryResponse;
import com.commercehub.backend.category.entity.Category;
import org.mapstruct.*;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CategoryMapper {

    Category toEntity(CreateCategoryRequest request);

    CategoryResponse toResponse(Category category);

    @Mapping(target = "name", ignore = true)
    @Mapping(target = "slug", ignore = true)
    void updateEntityFromRequest(UpdateCategoryRequest request, @MappingTarget Category category);
}