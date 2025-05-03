package ru.ecosharing.auth_service.service.impl;

import feign.FeignException; // Исключение Feign
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User; // Используем User из Spring Security
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.auth_service.client.UserServiceClient; // Feign клиент
import ru.ecosharing.auth_service.dto.response.UserCredentialsResponse; // DTO ответа User Service
import ru.ecosharing.auth_service.model.UserCredentials;
import ru.ecosharing.auth_service.repository.UserCredentialsRepository;


import java.util.stream.Collectors;

/**
 * Реализация UserDetailsService, которая загружает данные пользователя для Spring Security.
 * Взаимодействует с User Service для получения ID и статуса, а затем с локальной базой
 * для получения хеша пароля и ролей.
 */
@Slf4j
@Service("userDetailsService") // Явно указываем имя бина
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserCredentialsRepository userCredentialsRepository; // Репозиторий для локальных данных
    private final UserServiceClient userServiceClient; // Feign клиент для User Service

    /**
     * Загружает данные пользователя по его логину (username или email).
     * Вызывается Spring Security во время аутентификации.
     * @param login Логин пользователя (может быть username или email).
     * @return UserDetails с данными пользователя.
     * @throws UsernameNotFoundException если пользователь не найден или неактивен.
     */
    @Override
    @Transactional(readOnly = true) // Транзакция только на чтение
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        log.debug("Попытка загрузки пользователя по логину: {}", login);

        // 1. Запрос к User Service для получения userId, username и статуса по логину
        UserCredentialsResponse userProfile;
        try {
            ResponseEntity<UserCredentialsResponse> response = userServiceClient.findUserByLogin(login);
            // Проверяем успешность ответа и наличие тела
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("User Service не вернул данные для логина: {}", login);
                throw new UsernameNotFoundException("Пользователь не найден с логином: " + login);
            }
            userProfile = response.getBody();
            log.debug("User Service вернул: userId={}, username={}, isActive={}",
                    userProfile.getUserId(), userProfile.getUsername(), userProfile.isActive());

        } catch (FeignException.NotFound e) {
            // User Service вернул 404
            log.warn("User Service не нашел пользователя по логину: {}", login);
            throw new UsernameNotFoundException("Пользователь не найден с логином: " + login);
        } catch (FeignException e) {
            // Другие ошибки Feign (сетевые, ошибки сервера User Service)
            log.error("Ошибка Feign при запросе к User Service для логина {}: статус={}, тело={}",
                    login, e.status(), e.contentUTF8(), e);
            throw new UsernameNotFoundException("Ошибка связи с сервисом пользователей для логина: " + login);
        } catch (Exception e) {
            // Неожиданные ошибки
            log.error("Неожиданная ошибка при запросе к User Service для логина {}:", login, e);
            throw new UsernameNotFoundException("Внутренняя ошибка при поиске пользователя: " + login);
        }

        // 2. Проверка статуса активности пользователя из User Service
        if (!userProfile.isActive()) {
            log.warn("Пользователь {} (ID: {}) деактивирован в User Service.", userProfile.getUsername(), userProfile.getUserId());
            // Бросаем исключение, чтобы прервать аутентификацию
            throw new UsernameNotFoundException("Учетная запись пользователя " + userProfile.getUsername() + " деактивирована.");
            // В Spring Security это будет обработано как DisabledException, если настроено
        }

        // 3. Загрузка учетных данных (пароль, роли, локальный статус) из локальной базы Auth Service по userId
        UserCredentials credentials = userCredentialsRepository.findByUserId(userProfile.getUserId())
                .orElseThrow(() -> {
                    // Эта ситуация критическая - профиль есть, а учетных данных нет!
                    log.error("КРИТИЧЕСКАЯ ОШИБКА: Профиль пользователя {} (ID: {}) существует, но учетные данные отсутствуют в Auth Service!",
                            userProfile.getUsername(), userProfile.getUserId());
                    // Не даем войти такому пользователю
                    return new UsernameNotFoundException("Ошибка консистентности данных для пользователя: " + userProfile.getUsername());
                });

        // 4. Дополнительная проверка статуса активности в Auth Service (на случай локальной блокировки)
        if (!credentials.isActive()) {
            log.warn("Учетная запись пользователя {} (ID: {}) деактивирована локально в Auth Service.",
                    userProfile.getUsername(), userProfile.getUserId());
            throw new UsernameNotFoundException("Учетная запись пользователя " + userProfile.getUsername() + " деактивирована.");
        }

        // 5. Создание объекта UserDetails для Spring Security
        return new User(
                userProfile.getUsername(), // Используем актуальный username из User Service
                credentials.getPasswordHash(), // Хеш пароля из локальной базы (он не может быть null)
                credentials.getRoles().stream() // Получаем роли из локальной базы
                        .map(role -> new SimpleGrantedAuthority(role.getName().getRole())) // Преобразуем в GrantedAuthority
                        .collect(Collectors.toList()) // Собираем в список
                // Остальные флаги состояния аккаунта (можно использовать для более детального контроля)
                // credentials.isActive(), // enabled (уже проверили выше)
                // true, // accountNonExpired
                // true, // credentialsNonExpired
                // true // accountNonLocked
        );
    }
}