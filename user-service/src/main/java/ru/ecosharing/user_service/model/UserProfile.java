package ru.ecosharing.user_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal; // Используем BigDecimal для рейтинга
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@ToString(exclude = "userSettings")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_profiles", indexes = { // Имя таблицы из твоего Liquibase
        @Index(name = "idx_userprofile_username", columnList = "username", unique = true),
        @Index(name = "idx_userprofile_email", columnList = "email", unique = true)
})
public class UserProfile {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId; // Этот ID приходит извне (от Auth Service)

    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    @Column(name = "email", length = 100, unique = true, nullable = false)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "about", length = 500)
    private String about;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "rating", precision = 3, scale = 2) // Для BigDecimal
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO; // Используем BigDecimal

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserSettings userSettings;

    private LocalDateTime lastLoginAt;

    public void setUserSettings(UserSettings userSettings) {
        if (userSettings == null) {
            if (this.userSettings != null) {
                this.userSettings.setUserProfile(null);
            }
        } else {
            userSettings.setUserProfile(this);
        }
        this.userSettings = userSettings;
    }

    // --- Стандартные equals/hashCode по ID ---
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        UserProfile that = (UserProfile) o;
        return getUserId() != null && Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(userId);
    }
}