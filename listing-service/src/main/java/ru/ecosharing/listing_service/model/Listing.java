package ru.ecosharing.listing_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.proxy.HibernateProxy;
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.enums.ModerationStatus;
import ru.ecosharing.listing_service.enums.PriceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "listings", indexes = {
        @Index(name = "idx_listing_user_id", columnList = "user_id"),
        @Index(name = "idx_listing_category_id", columnList = "category_id"),
        @Index(name = "idx_listing_moderation_status", columnList = "moderation_status"),
        @Index(name = "idx_listing_availability_status", columnList = "availability_status"),
        @Index(name = "idx_listing_price", columnList = "price"),
        @Index(name = "idx_listing_location", columnList = "location_text")
})
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @ToString.Exclude
    private Category category;

    @Column(name = "main_image_url", length = 512)
    private String mainImageUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "listing_additional_images", joinColumns = @JoinColumn(name = "listing_id"))
    @Column(name = "image_url", length = 512)
    @OrderColumn
    private List<String> additionalImageUrls;

    @Column(name = "location_text", length = 512)
    private String locationText;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 20)
    private PriceType priceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 30)
    private ModerationStatus moderationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 20)
    private AvailabilityStatus availabilityStatus;

    @Column(name = "moderation_comment", columnDefinition = "TEXT")
    private String moderationComment;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_moderated_at")
    private LocalDateTime lastModeratedAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Listing listing = (Listing) o;
        return getId() != null && Objects.equals(getId(), listing.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}