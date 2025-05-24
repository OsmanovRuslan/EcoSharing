package ru.ecosharing.listing_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.ecosharing.listing_service.dto.request.ModerateListingRequest;
import ru.ecosharing.listing_service.dto.response.ListingResponse;
import ru.ecosharing.listing_service.dto.response.ModerationListingResponse;
import ru.ecosharing.listing_service.security.JwtTokenProvider;
import ru.ecosharing.listing_service.service.ModerationService;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/moderation/listings") // Базовый путь для модерации
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')") // Все методы требуют роль модератора или админа
public class ModerationController {

    private final ModerationService moderationService;

    @GetMapping("/pending")
    public ResponseEntity<Page<ModerationListingResponse>> getPendingModerationListings(
            @PageableDefault(size = 10, sort = "createdAt,asc") Pageable pageable) { // Сначала старые
        UUID moderatorId = getCurrentUserIdOrThrow(); // Для логирования, кто запросил
        log.info("GET /api/moderation/listings/pending - Moderator {} fetching pending listings. Pageable: {}", moderatorId, pageable);
        Page<ModerationListingResponse> listings = moderationService.getPendingModerationListings(pageable);
        return ResponseEntity.ok(listings);
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ModerationListingResponse> getListingForModeration(@PathVariable UUID listingId) {
        UUID moderatorId = getCurrentUserIdOrThrow();
        log.info("GET /api/moderation/listings/{} - Moderator {} fetching listing for moderation.", listingId, moderatorId);
        ModerationListingResponse listing = moderationService.getListingForModeration(listingId);
        return ResponseEntity.ok(listing);
    }

    @PostMapping("/{listingId}/approve")
    public ResponseEntity<ListingResponse> approveListing(@PathVariable UUID listingId) {
        UUID moderatorId = getCurrentUserIdOrThrow();
        log.info("POST /api/moderation/listings/{}/approve - Moderator {} approving listing.", listingId, moderatorId);
        ListingResponse approvedListing = moderationService.approveListing(listingId, moderatorId);
        return ResponseEntity.ok(approvedListing);
    }

    @PostMapping("/{listingId}/revise")
    public ResponseEntity<ListingResponse> sendListingForRevision(
            @PathVariable UUID listingId,
            @Valid @RequestBody ModerateListingRequest request) {
        UUID moderatorId = getCurrentUserIdOrThrow();
        log.info("POST /api/moderation/listings/{}/revise - Moderator {} sending listing for revision.", listingId, moderatorId);
        ListingResponse revisedListing = moderationService.sendListingForRevision(listingId, moderatorId, request);
        return ResponseEntity.ok(revisedListing);
    }

    @PostMapping("/{listingId}/reject")
    public ResponseEntity<ListingResponse> rejectListing(
            @PathVariable UUID listingId,
            @Valid @RequestBody ModerateListingRequest request) {
        UUID moderatorId = getCurrentUserIdOrThrow();
        log.warn("POST /api/moderation/listings/{}/reject - Moderator {} rejecting listing.", listingId, moderatorId);
        ListingResponse rejectedListing = moderationService.rejectListing(listingId, moderatorId, request);
        return ResponseEntity.ok(rejectedListing);
    }

    private UUID getCurrentUserIdOrThrow() {
        return JwtTokenProvider.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Moderator/Admin ID not found in security context. This should not happen."));
    }
}