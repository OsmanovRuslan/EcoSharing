package ru.ecosharing.auth_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Сущность, хранящая минимальные учетные данные пользователя,
 * необходимые для аутентификации в Auth Service.
 */
@Getter
@Setter
@ToString(exclude = {"roles", "refreshTokens"}) // Исключаем коллекции для лаконичности логов
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_credentials", indexes = {
        // Индекс по telegram_id для быстрого поиска при входе через Telegram
        @Index(name = "idx_usercredentials_telegram_id", columnList = "telegram_id", unique = true)
})
public class UserCredentials {

    @Id
    // Используем UUID, сгенерированный здесь, который также будет PK в User Service
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "password_hash", length = 100, nullable = false) // Пароль ОБЯЗАТЕЛЕН
    private String passwordHash;

    @Column(name = "telegram_id", length = 50, unique = true)
    private String telegramId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default // Значение по умолчанию при использовании билдера
    private boolean isActive = true; // Статус активности учетной записи

    // Связь многие-ко-многим с ролями
    @ManyToMany(fetch = FetchType.EAGER) // Загружаем роли сразу, т.к. они нужны для JWT
    @JoinTable(
            name = "user_credential_roles", // Имя промежуточной таблицы
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // Связь один-ко-многим с refresh токенами
    @OneToMany(mappedBy = "userCredentials", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<RefreshToken> refreshTokens = new HashSet<>();

    // --- Вспомогательные методы ---
    public void addRole(Role role) {
        this.roles.add(role);
    }

    // --- Стандартные переопределения ---
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        UserCredentials that = (UserCredentials) o;
        // Сравнение по первичному ключу userId
        return getUserId() != null && Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public final int hashCode() {
        // Хеш-код на основе первичного ключа
        return Objects.hash(userId);
    }
}