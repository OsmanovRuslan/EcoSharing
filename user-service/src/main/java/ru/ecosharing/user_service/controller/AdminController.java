package ru.ecosharing.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// Импорты для Specification Resolver
import net.kaczmarzyk.spring.data.jpa.domain.Equal;
import net.kaczmarzyk.spring.data.jpa.domain.LikeIgnoreCase;
import net.kaczmarzyk.spring.data.jpa.web.annotation.And;
import net.kaczmarzyk.spring.data.jpa.web.annotation.Spec;
// ---
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable; // Для пагинации
import org.springframework.data.jpa.domain.Specification; // Для спецификаций JPA
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Для защиты на уровне метода
import org.springframework.web.bind.annotation.*;
import ru.ecosharing.user_service.dto.request.AdminUpdateUserRequest; // DTO для обновления админом
import ru.ecosharing.user_service.dto.response.MessageResponse; // DTO простого сообщения
import ru.ecosharing.user_service.dto.response.UserProfileResponse; // DTO полного профиля
import ru.ecosharing.user_service.dto.response.UserSettingsResponse; // DTO настроек
import ru.ecosharing.user_service.dto.response.UserSummaryResponse; // DTO краткого профиля
import ru.ecosharing.user_service.model.UserProfile; // Сущность нужна для Specification
import ru.ecosharing.user_service.service.UserService; // Интерфейс сервиса

import java.util.UUID;

/**
 * REST контроллер для административных операций над пользователями.
 * Доступ ко всем эндпоинтам требует роли 'ADMIN'.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users") // Базовый путь для админских операций с пользователями
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Защита всех методов контроллера на уровне класса
public class AdminController {

    private final UserService userService; // Внедряем основной сервис

    /**
     * Ищет пользователей по заданным критериям с пагинацией и сортировкой.
     * Использует библиотеку specification-arg-resolver для удобного маппинга
     * параметров запроса в JPA Specification.
     * Пример запроса: GET /api/admin/users?username=test&email=example.com&isActive=true&page=0&size=20&sort=createdAt,desc
     * @param spec Спецификация, автоматически созданная из параметров запроса.
     * @param pageable Параметры пагинации и сортировки.
     * @return ResponseEntity со страницей UserSummaryResponse.
     */
    @GetMapping
    public ResponseEntity<Page<UserSummaryResponse>> searchUsers(
            // Аннотация @And объединяет условия поиска через логическое И
            @And({
                    // @Spec описывает маппинг параметра запроса на поле сущности и тип сравнения
                    @Spec(path = "username", params = "username", spec = LikeIgnoreCase.class), // username LIKE '%:username%' (ignore case)
                    @Spec(path = "email", params = "email", spec = LikeIgnoreCase.class),       // email LIKE '%:email%' (ignore case)
                    @Spec(path = "firstName", params = "firstName", spec = LikeIgnoreCase.class),// firstName LIKE '%:firstName%' (ignore case)
                    @Spec(path = "lastName", params = "lastName", spec = LikeIgnoreCase.class), // lastName LIKE '%:lastName%' (ignore case)
                    @Spec(path = "isActive", params = "isActive", spec = Equal.class)           // isActive = :isActive (true/false)
                    // Можно добавить другие поля для фильтрации
            }) Specification<UserProfile> spec, // Spring MVC автоматически создаст спецификацию
            Pageable pageable // Spring MVC автоматически создаст объект Pageable
    ) {
        log.info("GET /api/admin/users - Поиск пользователей администратором. Фильтры: {}, Пагинация: {}", spec, pageable);
        Page<UserSummaryResponse> userPage = userService.searchUsers(spec, pageable);
        return ResponseEntity.ok(userPage);
    }

    /**
     * Получает полный профиль пользователя по его ID (для администратора).
     * @param userId ID пользователя.
     * @return ResponseEntity с UserProfileResponse.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfileAdmin(@PathVariable UUID userId) {
        log.info("GET /api/admin/users/{} - Запрос полного профиля администратором", userId);
        UserProfileResponse profile = userService.getUserProfileAdmin(userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * Получает настройки пользователя по его ID (для администратора).
     * @param userId ID пользователя.
     * @return ResponseEntity с UserSettingsResponse.
     */
    @GetMapping("/{userId}/settings")
    public ResponseEntity<UserSettingsResponse> getUserSettingsAdmin(@PathVariable UUID userId) {
        log.info("GET /api/admin/users/{}/settings - Запрос настроек пользователя администратором", userId);
        UserSettingsResponse settings = userService.getUserSettingsAdmin(userId);
        return ResponseEntity.ok(settings);
    }

    /**
     * Обновляет профиль пользователя от имени администратора.
     * Может изменять поля, недоступные для редактирования самому пользователю (например, isActive).
     * @param userId ID пользователя для обновления.
     * @param request DTO с полями для обновления.
     * @return ResponseEntity с обновленным UserProfileResponse.
     */
    @PutMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> updateUserProfileAdmin(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUpdateUserRequest request) { // Валидируем DTO
        log.info("PUT /api/admin/users/{} - Обновление профиля администратором", userId);
        UserProfileResponse updatedProfile = userService.updateUserProfileAdmin(userId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * Устанавливает статус активности (активирует/деактивирует) пользователя.
     * Более явный эндпоинт, чем использование PUT с полным DTO.
     * @param userId ID пользователя.
     * @param isActive Новый статус активности (true/false).
     * @return ResponseEntity с обновленным UserProfileResponse.
     */
    @PatchMapping("/{userId}/status") // Используем PATCH для частичного обновления (статуса)
    public ResponseEntity<UserProfileResponse> setUserStatus(
            @PathVariable UUID userId,
            @RequestParam boolean isActive) { // Получаем статус из параметра запроса
        log.info("PATCH /api/admin/users/{}/status - Установка статуса {} администратором", userId, isActive);
        // Создаем DTO только для изменения статуса
        AdminUpdateUserRequest request = new AdminUpdateUserRequest();
        request.setIsActive(isActive);
        // Вызываем тот же метод сервиса, что и для PUT
        UserProfileResponse updatedProfile = userService.updateUserProfileAdmin(userId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * Удаляет пользователя.
     * ВНИМАНИЕ: Физическое удаление! Используйте с осторожностью.
     * @param userId ID пользователя для удаления.
     * @return ResponseEntity с сообщением об успехе.
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<MessageResponse> deleteUserAdmin(@PathVariable UUID userId) {
        log.warn("DELETE /api/admin/users/{} - Инициация УДАЛЕНИЯ пользователя администратором!", userId);
        userService.deleteUserAdmin(userId);
        // Возвращаем простое сообщение об успехе
        return ResponseEntity.ok(new MessageResponse("Пользователь ID " + userId + " успешно удален."));
    }

    // Эндпоинт для сброса пароля (если пароль хранится в User Service, что у нас не так)
    // или для инициирования сброса в Auth Service (потребует Feign клиента к Auth Service)
    /*
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<MessageResponse> resetPasswordAdmin(@PathVariable UUID userId) {
        log.info("POST /api/admin/users/{}/reset-password - Инициация сброса пароля администратором", userId);
        // Логика сброса пароля (например, вызов Auth Service)
        // authServiceClient.resetPassword(userId);
        return ResponseEntity.ok(new MessageResponse("Запрос на сброс пароля для пользователя ID " + userId + " отправлен."));
    }
    */

    // Эндпоинт для управления ролями (если нужно вызывать Auth Service)
    /*
    @PatchMapping("/{userId}/roles")
    public ResponseEntity<UserProfileResponse> updateUserRolesAdmin(
                @PathVariable UUID userId,
                @RequestBody RoleUpdateRequest request) { // Пример DTO для обновления ролей
         log.info("PATCH /api/admin/users/{}/roles - Обновление ролей {} для пользователя администратором", userId, request);
         // Вызов Auth Service для обновления ролей
         // authServiceClient.updateRoles(userId, request);
         // Получаем обновленный профиль (роли изменятся в токене при следующем входе)
         UserProfileResponse profile = userService.getUserProfileAdmin(userId);
         return ResponseEntity.ok(profile);
    }
    */
}
