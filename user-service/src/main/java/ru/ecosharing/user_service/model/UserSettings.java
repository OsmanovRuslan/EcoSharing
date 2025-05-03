package ru.ecosharing.user_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
// Убираем userProfile из ToString
@ToString(exclude = "userProfile")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_settings") // Имя таблицы из твоего Liquibase (если создавал)
public class UserSettings {

    @Id
    // ID берется из связанного UserProfile через @MapsId
    @Column(name = "user_id") // Имя колонки PK и FK
    private UUID userId;

    // --- ИЗМЕНЕНИЕ: Настройка связи ---
    @OneToOne(fetch = FetchType.LAZY) // optional=false может вызвать проблемы при раздельном сохранении, убрал
    @MapsId // <<< Ключевая аннотация: говорит, что userId - это и PK, и FK, связанный с userProfile
    @JoinColumn(name = "user_id") // Имя колонки для связи (совпадает с PK)
    private UserProfile userProfile;

    // --- Поля настроек ---
    @Column(name = "enable_email_notifications", nullable = false)
    @Builder.Default
    private boolean enableEmailNotifications = true;

    @Column(name = "enable_telegram_notifications", nullable = false)
    @Builder.Default
    private boolean enableTelegramNotifications = true;

    @Column(name = "language", length = 5)
    @Builder.Default
    private String language = "ru";

    // --- Стандартные equals/hashCode по ID ---
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        UserSettings that = (UserSettings) o;
        return getUserId() != null && Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(userId);
    }
}