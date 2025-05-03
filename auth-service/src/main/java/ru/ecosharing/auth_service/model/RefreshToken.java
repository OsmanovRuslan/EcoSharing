package ru.ecosharing.auth_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant; // Используем Instant для временных меток
import java.util.Objects;
import java.util.UUID;

/**
 * Сущность, представляющая refresh токен в базе данных.
 */
@Getter
@Setter
@ToString(exclude = "userCredentials") // Исключаем пользователя для избежания рекурсии
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens", indexes = {
        // Индекс по самому токену для быстрого поиска
        @Index(name = "idx_refreshtoken_token", columnList = "token", unique = true)
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Связь многие-к-одному с учетными данными пользователя
    @ManyToOne(fetch = FetchType.LAZY, optional = false) // Загружаем пользователя лениво
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false) // Связь по user_id
    private UserCredentials userCredentials;

    @Column(name = "token", nullable = false, unique = true, length = 512) // Увеличим длину для возможных JWT-токенов
    private String token; // Сама строка refresh токена

    @Column(name = "expiry_date", nullable = false) // Дата истечения срока действия
    private Instant expiryDate;

    // --- Стандартные переопределения ---
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        RefreshToken that = (RefreshToken) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        // Используем уникальный токен для хеш-кода
        return Objects.hash(token);
    }
}