package ru.ecosharing.listing_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification; // Для передачи спецификации из параметров запроса
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.ecosharing.listing_service.dto.request.CreateListingRequest;
import ru.ecosharing.listing_service.dto.request.UpdateListingRequest;
import ru.ecosharing.listing_service.dto.response.ListingResponse;
import ru.ecosharing.listing_service.dto.response.ListingSummaryResponse;
import ru.ecosharing.listing_service.dto.response.MessageResponse;
import ru.ecosharing.listing_service.elasticsearch.document.ListingDocument; // Если спецификация для ES
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.security.JwtTokenProvider; // Для получения ID пользователя
import ru.ecosharing.listing_service.service.ListingService;
// Для Specification Resolver, если используется:
// import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
// import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
// import ru.ecosharing.listing_service.model.Listing; // Если спецификация для JPA

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    // --- Эндпоинты для объявлений текущего пользователя ("мои объявления") ---
    @PostMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ListingResponse> createMyListing(@Valid @RequestBody CreateListingRequest request) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("POST /api/listings/my - User {} creating listing: {}", currentUserId, request.getTitle());
        ListingResponse createdListing = listingService.createListing(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdListing);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ListingSummaryResponse>> getMyListings(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("GET /api/listings/my - User {} fetching their listings. Pageable: {}", currentUserId, pageable);
        Page<ListingSummaryResponse> listings = listingService.getMyListings(currentUserId, pageable);
        return ResponseEntity.ok(listings);
    }

    @PutMapping("/my/{listingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ListingResponse> updateMyListing(@PathVariable UUID listingId,
                                                           @Valid @RequestBody UpdateListingRequest request) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("PUT /api/listings/my/{} - User {} updating listing.", listingId, currentUserId);
        ListingResponse updatedListing = listingService.updateMyListing(currentUserId, listingId, request);
        return ResponseEntity.ok(updatedListing);
    }

    @DeleteMapping("/my/{listingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> deleteMyListing(@PathVariable UUID listingId) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.warn("DELETE /api/listings/my/{} - User {} attempting to delete listing.", listingId, currentUserId);
        listingService.deleteMyListing(currentUserId, listingId);
        return ResponseEntity.ok(new MessageResponse("Объявление ID " + listingId + " успешно удалено."));
    }

    @PatchMapping("/my/{listingId}/activate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ListingResponse> activateMyListing(@PathVariable UUID listingId) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("PATCH /api/listings/my/{}/activate - User {} activating listing.", listingId, currentUserId);
        ListingResponse activatedListing = listingService.activateMyListing(currentUserId, listingId);
        return ResponseEntity.ok(activatedListing);
    }

    @PatchMapping("/my/{listingId}/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ListingResponse> deactivateMyListing(@PathVariable UUID listingId) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("PATCH /api/listings/my/{}/deactivate - User {} deactivating listing.", listingId, currentUserId);
        ListingResponse deactivatedListing = listingService.deactivateMyListing(currentUserId, listingId);
        return ResponseEntity.ok(deactivatedListing);
    }


    // --- Публичные эндпоинты для просмотра и поиска ---
    @GetMapping("/{listingId}")
    public ResponseEntity<ListingResponse> getListingById(@PathVariable UUID listingId) {
        // ID текущего пользователя может быть null для анонимов
        UUID currentUserId = JwtTokenProvider.getCurrentUserId().orElse(null);
        log.info("GET /api/listings/{} - Fetching listing by ID. Current user: {}", listingId, currentUserId);
        ListingResponse listing = listingService.getListingById(listingId, currentUserId);
        return ResponseEntity.ok(listing);
    }

    @GetMapping
    public ResponseEntity<Page<ListingSummaryResponse>> searchListings(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) String locationText,
            @RequestParam(required = false) BigDecimal priceFrom,
            @RequestParam(required = false) BigDecimal priceTo,
            @RequestParam(required = false) AvailabilityStatus availabilityStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("GET /api/listings - Searching listings. Pageable: {}", pageable);
        Page<ListingSummaryResponse> listings = listingService.searchListings(categoryId, searchTerm, locationText, priceFrom, priceTo, availabilityStatus, pageable);
        return ResponseEntity.ok(listings);
    }

    // --- Эндпоинты для избранного ---
    // Их можно вынести в FavoriteController

    @PostMapping("/favorites/{listingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> addListingToFavorites(@PathVariable UUID listingId) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("POST /api/listings/favorites/{} - User {} adding listing to favorites.", listingId, currentUserId);
        listingService.addListingToFavorites(currentUserId, listingId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse("Объявление добавлено в избранное."));
    }

    @DeleteMapping("/favorites/{listingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> removeListingFromFavorites(@PathVariable UUID listingId) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("DELETE /api/listings/favorites/{} - User {} removing listing from favorites.", listingId, currentUserId);
        listingService.removeListingFromFavorites(currentUserId, listingId);
        return ResponseEntity.ok(new MessageResponse("Объявление удалено из избранного."));
    }

    @GetMapping("/favorites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ListingSummaryResponse>> getMyFavoriteListings(
            @PageableDefault(size = 10, sort = "addedAt", direction = Sort.Direction.DESC) Pageable pageable // Сортировка по дате добавления в избранное
    ) {
        UUID currentUserId = getCurrentUserIdOrThrow();
        log.info("GET /api/listings/favorites - User {} fetching their favorite listings. Pageable: {}", currentUserId, pageable);
        // В FavoriteListingRepository метод findFavoriteListingsByUserId уже сортирует по addedAt,
        // но Pageable может переопределить.
        Page<ListingSummaryResponse> favorites = listingService.getFavoriteListings(currentUserId, pageable);
        return ResponseEntity.ok(favorites);
    }

    // Эндпоинт для получения объявлений конкретного пользователя (публичный)
    @GetMapping("/user/{ownerUserId}")
    public ResponseEntity<Page<ListingSummaryResponse>> getListingsByOwner(
            @PathVariable UUID ownerUserId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/listings/user/{} - Fetching listings for owner. Pageable: {}", ownerUserId, pageable);
        Page<ListingSummaryResponse> listings = listingService.getListingsByOwner(ownerUserId, pageable);
        return ResponseEntity.ok(listings);
    }


    private UUID getCurrentUserIdOrThrow() {
        return JwtTokenProvider.getCurrentUserId()
                .orElseThrow(() -> {
                    log.warn("Attempted to access protected resource without authentication.");
                    // GlobalExceptionHandler должен поймать это, если SecurityContext пуст
                    // и вернуть 401, но для явности можно бросить здесь.
                    // Однако, @PreAuthorize("isAuthenticated()") уже должен это сделать.
                    // Если мы дошли сюда, и ID нет, это странно.
                    return new IllegalStateException("User ID not found in security context.");
                });
    }
}