package ru.ecosharing.auth_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Сущность, представляющая роль пользователя в базе данных.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING) // Храним имя роли как строку
    @Column(name = "name", length = 50, nullable = false, unique = true)
    private RoleName name;

    // Конструктор для удобного создания роли по Enum
    public Role(RoleName name) {
        this.name = name;
    }

    // Переопределение equals и hashCode для корректной работы с JPA и коллекциями
    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Role role = (Role) o;
        return getId() != null && Objects.equals(getId(), role.getId());
    }

    @Override
    public final int hashCode() {
        // Используем имя роли для хеш-кода, т.к. оно уникально
        return Objects.hash(name);
    }
}