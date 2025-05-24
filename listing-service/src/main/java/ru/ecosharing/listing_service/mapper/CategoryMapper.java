package ru.ecosharing.listing_service.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import ru.ecosharing.listing_service.dto.request.CreateCategoryRequest;
import ru.ecosharing.listing_service.dto.request.UpdateCategoryRequest;
import ru.ecosharing.listing_service.dto.response.CategoryResponse;
import ru.ecosharing.listing_service.model.Category;

import java.util.List;

@Mapper(componentModel = "spring", uses = {ListingMapperHelper.class})
public interface CategoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", source = "parentId", qualifiedByName = "uuidToCategory")
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    Category toCategory(CreateCategoryRequest dto);

    @Mapping(target = "parentId", source = "parent.id")
    CategoryResponse toCategoryResponse(Category category);

    List<CategoryResponse> toCategoryResponseList(List<Category> categories);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", source = "parentId", qualifiedByName = "uuidToCategoryNullable")
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateCategoryFromDto(UpdateCategoryRequest dto, @MappingTarget Category category);
}