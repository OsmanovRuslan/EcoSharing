package ru.ecosharing.listing_service.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import ru.ecosharing.listing_service.dto.request.CreateListingRequest;
import ru.ecosharing.listing_service.dto.request.UpdateListingRequest;
import ru.ecosharing.listing_service.dto.response.ListingResponse;
import ru.ecosharing.listing_service.dto.response.ListingSummaryResponse;
import ru.ecosharing.listing_service.model.Listing;

import java.util.List;

@Mapper(componentModel = "spring", uses = {CategoryMapper.class, ListingMapperHelper.class})
public interface ListingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "category", source = "categoryId", qualifiedByName = "uuidToCategory")
    @Mapping(target = "moderationStatus", constant = "PENDING_MODERATION")
    @Mapping(target = "availabilityStatus", constant = "AVAILABLE")
    @Mapping(target = "viewCount", constant = "0")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastModeratedAt", ignore = true)
    @Mapping(target = "moderationComment", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    Listing toListing(CreateListingRequest dto);

    @Mapping(target = "owner", ignore = true) // Будет установлен в сервисе
    @Mapping(target = "isFavorite", ignore = true) // Будет установлен в сервисе
    ListingResponse toListingResponse(Listing listing);

    @Mapping(target = "ownerUserId", source = "userId")
    @Mapping(target = "categoryName", source = "category.name")
    ListingSummaryResponse toListingSummaryResponse(Listing listing);

    List<ListingSummaryResponse> toListingSummaryResponseList(List<Listing> listings);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "category", source = "categoryId", qualifiedByName = "uuidToCategoryNullable")
    @Mapping(target = "moderationStatus", ignore = true)
    @Mapping(target = "availabilityStatus", ignore = true)
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastModeratedAt", ignore = true)
    @Mapping(target = "moderationComment", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateListingFromDto(UpdateListingRequest dto, @MappingTarget Listing listing);
}