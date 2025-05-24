package ru.ecosharing.user_service.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.user_service.dto.request.*;
import ru.ecosharing.user_service.dto.response.*;
import ru.ecosharing.user_service.model.UserProfile;
import ru.ecosharing.user_service.model.UserSettings;
import ru.ecosharing.user_service.exception.ResourceNotFoundException;
import ru.ecosharing.user_service.exception.UserServiceException;
import ru.ecosharing.user_service.mapper.UserMapper;
import ru.ecosharing.user_service.repository.UserProfileRepository;
import ru.ecosharing.user_service.repository.UserSettingsRepository;
import ru.ecosharing.user_service.service.UserService;

import java.util.Optional;
import java.util.UUID;

/**
 * Реализация интерфейса UserService.
 * (Версия с добавленным методом deleteUserAdmin)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserSettingsRepository userSettingsRepository;

    // --- Существующие методы ... ---
    @Override
    @Transactional
    public void createUserProfile(CreateUserProfileRequest request) { /* ... */
        UUID userId = request.getUserId();
        log.info("Создание профиля для пользователя ID: {}", userId);
        if (userProfileRepository.existsById(userId) ||
                userProfileRepository.existsByUsername(request.getUsername()) ||
                userProfileRepository.existsByEmail(request.getEmail())) {
            throw new UserServiceException("Профиль пользователя с таким ID, username или email уже существует.");
        }
        UserProfile userProfile = UserMapper.toUserProfile(request);
        try {
            userProfileRepository.save(userProfile);
            log.info("Профиль для пользователя ID: {} успешно создан.", userId);
        } catch (Exception e) {
            throw new UserServiceException("Не удалось сохранить профиль пользователя.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityCheckResponse checkAvailability(AvailabilityCheckRequest r) { /* ... */
        log.debug("Проверка доступности: username={}, email={}", r.getUsername(), r.getEmail());
        boolean u = !userProfileRepository.existsByUsername(r.getUsername());
        boolean e = !userProfileRepository.existsByEmail(r.getEmail());
        return new AvailabilityCheckResponse(u, e);
    }

    @Override
    @Transactional(readOnly = true)
    public UserCredentialsResponse getUserCredentialsByLogin(String l) { /* ... */
        log.debug("Поиск учетных данных по логину: {}", l);
        UserProfile up = userProfileRepository.findByUsernameOrEmail(l, l)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден с логином или email: " + l));
        return UserMapper.toUserCredentialsResponse(up);
    }

    @Override
    @Transactional(readOnly = true)
    public UserCredentialsResponse getUserCredentialsById(UUID id) { /* ... */
        log.debug("Поиск учетных данных по ID: {}", id);
        UserProfile up = findUserProfileById(id);
        return UserMapper.toUserCredentialsResponse(up);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID id) { /* ... */
        log.debug("Запрос профиля для пользователя ID: {}", id);
        UserProfile up = findUserProfileById(id);
        return UserMapper.toUserProfileResponse(up);
    }

    @Override
    @Transactional
    public UserProfileResponse updateCurrentUserProfile(UUID id, UpdateUserProfileRequest r) { /* ... */
        log.info("Обновление профиля для пользователя ID: {}", id);
        UserProfile up = findUserProfileById(id);
        Optional.ofNullable(r.getFirstName()).ifPresent(up::setFirstName);
        Optional.ofNullable(r.getLastName()).ifPresent(up::setLastName);
        Optional.ofNullable(r.getPhone()).ifPresent(up::setPhone);
        Optional.ofNullable(r.getAbout()).ifPresent(up::setAbout);
        Optional.ofNullable(r.getAvatarUrl()).ifPresent(up::setAvatarUrl);
        UserProfile uup = userProfileRepository.save(up);
        log.info("Профиль пользователя ID: {} успешно обновлен.", id);
        return UserMapper.toUserProfileResponse(uup);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSettingsResponse getCurrentUserSettings(UUID id) { /* ... */
        log.debug("Запрос настроек для пользователя ID: {}", id);
        UserSettings us = findUserSettingsByUserId(id);
        return UserMapper.toUserSettingsResponse(us);
    }

    @Override
    @Transactional
    public UserSettingsResponse updateCurrentUserSettings(UUID id, UpdateUserSettingsRequest r) { /* ... */
        log.info("Обновление настроек для пользователя ID: {}", id);
        UserSettings us = findUserSettingsByUserId(id);
        Optional.ofNullable(r.getEnableEmailNotifications()).ifPresent(us::setEnableEmailNotifications);
        Optional.ofNullable(r.getEnableTelegramNotifications()).ifPresent(us::setEnableTelegramNotifications);
        Optional.ofNullable(r.getLanguage()).ifPresent(us::setLanguage);
        UserSettings uus = userSettingsRepository.save(us);
        log.info("Настройки пользователя ID: {} успешно обновлены.", id);
        return UserMapper.toUserSettingsResponse(uus);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicUserProfile(UUID id) { /* ... */
        log.debug("Запрос публичного профиля для пользователя ID: {}", id);
        UserProfile up = findUserProfileById(id);
        if (!up.isActive()) {
            throw new ResourceNotFoundException("Пользователь не найден или неактивен.");
        }
        return UserMapper.toPublicUserProfileResponse(up);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserSummaryResponse> searchUsers(Specification<UserProfile> spec, Pageable p) { /* ... */
        log.info("Поиск пользователей администратором. Page: {}, Size: {}", p.getPageNumber(), p.getPageSize());
        Page<UserProfile> up = userProfileRepository.findAll(spec, p);
        return up.map(UserMapper::toUserSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse getUserProfileAdmin(UUID id) { /* ... */
        log.info("Администратор запрашивает профиль пользователя ID: {}", id);
        return getCurrentUserProfile(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserSettingsResponse getUserSettingsAdmin(UUID id) { /* ... */
        log.info("Администратор запрашивает настройки пользователя ID: {}", id);
        return getCurrentUserSettings(id);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse updateUserProfileAdmin(UUID id, AdminUpdateUserRequest r) { /* ... */
        log.info("Администратор обновляет профиль пользователя ID: {}", id);
        UserProfile up = findUserProfileById(id);
        Optional.ofNullable(r.getFirstName()).ifPresent(up::setFirstName);
        Optional.ofNullable(r.getLastName()).ifPresent(up::setLastName);
        Optional.ofNullable(r.getPhone()).ifPresent(up::setPhone);
        Optional.ofNullable(r.getAbout()).ifPresent(up::setAbout);
        Optional.ofNullable(r.getAvatarUrl()).ifPresent(up::setAvatarUrl);
        Optional.ofNullable(r.getIsActive()).ifPresent(isActive -> {
            if (up.isActive() != isActive) {
                up.setActive(isActive);
                log.info("Администратор изменил статус активности пользователя ID {} на {}", id, isActive);
                // <<< ВАЖНО: Здесь может потребоваться вызов Auth Service для деактивации учетных данных,
                // если флаг isActive в Auth Service должен синхронизироваться >>>
            }
        });
        UserProfile uup = userProfileRepository.save(up);
        log.info("Профиль пользователя ID: {} обновлен администратором.", id);
        return UserMapper.toUserProfileResponse(uup);
    }

    @Override
    @Transactional(readOnly = true) // Операция только на чтение
    public UserNotificationDetailsDto getUserNotificationDetails(UUID userId) {
        log.debug("Запрос данных для уведомлений пользователя ID: {}", userId);

        UserProfile userProfile = findUserProfileById(userId);
        UserSettings userSettings = findUserSettingsByUserId(userId);

        return UserMapper.toUserNotificationDetailsDto(userProfile, userSettings);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')") // Требуем роль ADMIN для удаления
    public void deleteUserAdmin(UUID userId) {
        log.warn("Инициировано ФИЗИЧЕСКОЕ УДАЛЕНИЕ пользователя ID: {} администратором.", userId);

        // 1. Находим профиль пользователя (выбросит исключение, если не найден)
        UserProfile userProfile = findUserProfileById(userId);

        // 2. Выполняем удаление.
        // Благодаря CascadeType.ALL и orphanRemoval=true на связи с UserSettings,
        // настройки будут удалены каскадно.
        // Если бы были адреса или другие связанные сущности с CascadeType.REMOVE/ALL,
        // они бы тоже удалились.
        try {
            userProfileRepository.delete(userProfile);
            log.info("Пользователь ID: {} успешно ФИЗИЧЕСКИ удален из User Service.", userId);

            // <<< ВАЖНО: Необходимо также удалить учетные данные в Auth Service! >>>
            // Это потребует вызова Auth Service (например, через Feign).
            // try {
            //     authServiceClient.deleteUserCredentials(userId); // Пример вызова
            //     log.info("Учетные данные для пользователя ID {} удалены в Auth Service.", userId);
            // } catch (Exception e) {
            //     log.error("Ошибка при удалении учетных данных в Auth Service для пользователя ID {}: {}", userId, e.getMessage(), e);
            //     // Решить, что делать в этом случае:
            //     // - Откатить транзакцию? (пользователь не удалится из User Service)
            //     // - Оставить как есть? (останутся "осиротевшие" учетные данные в Auth Service)
            //     // Лучше использовать Saga pattern для таких распределенных транзакций.
            //     // Пока просто логируем ошибку.
            // }

        } catch (Exception e) {
            log.error("Ошибка при физическом удалении пользователя ID {} из User Service: {}", userId, e.getMessage(), e);
            throw new UserServiceException("Не удалось удалить пользователя.", e);
        }
    }

    // --- Вспомогательные приватные методы ---
    private UserProfile findUserProfileById(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Профиль пользователя не найден с ID: " + userId));
    }

    private UserSettings findUserSettingsByUserId(UUID userId) {
        // Проверяем, существует ли профиль, перед поиском настроек
        findUserProfileById(userId); // Выбросит исключение, если профиля нет
        // Ищем настройки
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.error("КРИТИЧЕСКАЯ ОШИБКА: Настройки не найдены для существующего пользователя ID: {}", userId);
                    // Эта ошибка указывает на проблему целостности данных
                    return new UserServiceException("Настройки пользователя не найдены для ID: " + userId);
                });
    }
}