package ru.ecosharing.notification_service.service.impl;

import feign.FeignException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.notification_service.client.AuthServiceClient;
import ru.ecosharing.notification_service.client.UserServiceClient;
import ru.ecosharing.notification_service.dto.kafka.NotificationRequestKafkaDto;
import ru.ecosharing.notification_service.dto.UserProfileDto;
import ru.ecosharing.notification_service.dto.UserNotificationDetailsDto;
import ru.ecosharing.notification_service.dto.UserTelegramIdDto;
import ru.ecosharing.notification_service.model.UserNotification;
import ru.ecosharing.notification_service.exception.TemplateNotFoundException;
import ru.ecosharing.notification_service.mapper.NotificationMapper;
import ru.ecosharing.notification_service.model.enums.NotificationChannel;
import ru.ecosharing.notification_service.model.enums.NotificationType;
import ru.ecosharing.notification_service.repository.UserNotificationRepository;
import ru.ecosharing.notification_service.service.NotificationService;
import ru.ecosharing.notification_service.service.SseService;
import ru.ecosharing.notification_service.service.TemplateService;
import ru.ecosharing.notification_service.service.sender.Notifier;

import java.util.*;

/**
 * Реализация основного сервиса уведомлений.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final UserServiceClient userServiceClient;
    private final AuthServiceClient authServiceClient;
    private final TemplateService templateService;
    private final UserNotificationRepository notificationRepository;
    private final SseService sseService;
    private final List<Notifier> notifiers;

    // Карта для быстрого доступа к Notifier по каналу
    private final Map<NotificationChannel, Notifier> notifierMap = new EnumMap<>(NotificationChannel.class);

    /**
     * Инициализация карты отправителей после создания бина.
     */
    @PostConstruct
    public void initNotifierMap() {
        for (Notifier notifier : notifiers) {
            if (notifier.getChannel() != null) {
                notifierMap.put(notifier.getChannel(), notifier);
                log.info("Зарегистрирован отправитель для канала: {}", notifier.getChannel());
            } else {
                log.warn("Обнаружен Notifier без указания канала: {}", notifier.getClass().getName());
            }
        }
        log.info("Инициализация карты отправителей завершена. Активных каналов: {}", notifierMap.size());
    }

    /**
     * Обрабатывает входящий запрос на уведомление из Kafka.
     */
    @Override
    @Transactional
    public void processNotificationRequest(NotificationRequestKafkaDto request) {
        UUID userId = request.getUserId();
        NotificationType type = request.getNotificationType();
        Map<String, String> params = request.getParams() != null ? request.getParams() : Collections.emptyMap();
        log.info("Обработка запроса на уведомление типа '{}' для пользователя ID {}", type, userId);

        // 1. Получаем детали пользователя из User Service
        UserNotificationDetailsDto userDetails = getUserDetails(userId);
        if (userDetails == null) {
            log.warn("Не удалось получить детали для пользователя ID {}. Уведомление не будет отправлено.", userId);
            return;
        }

        // 2. Получаем telegramId (из User Service или Auth Service)
        // Используем вспомогательный метод, который инкапсулирует логику
        String telegramId = resolveTelegramId(userId, userDetails);

        // 3. Определяем язык пользователя
        String language = Optional.ofNullable(userDetails.getLanguage())
                .filter(s -> !s.isBlank())
                .orElse("ru"); // Язык по умолчанию

        // 4. Определяем активность каналов для пользователя
        boolean emailEnabled = userDetails.isEmailNotificationsEnabled();
        // Канал Telegram активен, только если настройка включена И удалось получить ID
        boolean isTelegramChannelActive = userDetails.isTelegramNotificationsEnabled() && telegramId != null;

        // 5. Создаем внутренний DTO профиля для передачи в Notifier'ы
        UserProfileDto recipientProfile = UserProfileDto.builder()
                .userId(userId)
                .email(userDetails.getEmail())
                .telegramId(telegramId)
                .firstName(userDetails.getFirstName())
                .build();

        // 6. Сохраняем уведомление для In-App ("колокольчик") и отправляем SSE
        // Мы сохраняем его в любом случае, если удалось получить детали пользователя,
        // чтобы оно было в истории, даже если внешние каналы отключены.
        UserNotification savedInAppNotification = saveAndNotifyInApp(
                recipientProfile, type, params, request.getTargetUrl(), language
        );

        // Если сохранить In-App не удалось (например, ошибка БД), то нет смысла отправлять дальше
        if (savedInAppNotification == null) {
            log.error("Не удалось сохранить In-App уведомление для пользователя ID {}. Внешняя отправка отменена.", userId);
            // Можно бросить исключение, чтобы Kafka Consumer попытался снова (если настроен ErrorHandler)
            // throw new RuntimeException("Failed to save In-App notification");
            return;
        }

        // 7. Отправляем через внешние каналы (Email, Telegram), если они включены
        // Передаем recipientProfile, т.к. он содержит email и telegramId
        processExternalChannel(NotificationChannel.EMAIL, emailEnabled, recipientProfile, type, params, request.getTargetUrl(), language);
        processExternalChannel(NotificationChannel.TELEGRAM, isTelegramChannelActive, recipientProfile, type, params, request.getTargetUrl(), request.isAttachWebAppButton(), language);

        log.info("Обработка уведомления типа '{}' для пользователя ID {} завершена.", type, userId);
    }

    /**
     * Сохраняет In-App уведомление и отправляет его через SSE.
     */
    private UserNotification saveAndNotifyInApp(UserProfileDto recipient, NotificationType type, Map<String, String> params, String targetUrl, String language) {
        log.debug("Сохранение In-App уведомления и отправка SSE для пользователя ID {}", recipient.getUserId());
        try {
            // Пытаемся получить шаблон для IN_APP. Если его нет, используем EMAIL как fallback
            String template;
            try {
                template = templateService.getTemplate(type, NotificationChannel.IN_APP, language);
            } catch (TemplateNotFoundException e) {
                log.warn("Шаблон IN_APP не найден для {}/{}, используем EMAIL шаблон.", type, language);
                template = templateService.getTemplate(type, NotificationChannel.EMAIL, language);
            }

            String formattedText = templateService.format(template, params);
            String subject = templateService.getSubject(type, language); // Тема может быть заголовком

            UserNotification notification = UserNotification.builder()
                    .userId(recipient.getUserId())
                    .notificationType(type)
                    .channel(NotificationChannel.IN_APP)
                    .subject(subject)
                    .message(formattedText)
                    .params(params.isEmpty() ? null : params)
                    .targetUrl(targetUrl)
                    .isRead(false)
                    .build();

            UserNotification savedNotification = notificationRepository.save(notification);
            log.info("In-App уведомление ID {} сохранено для пользователя ID {}", savedNotification.getId(), recipient.getUserId());

            // Отправляем через SSE
            sseService.sendNotificationToUser(recipient.getUserId(), NotificationMapper.toDto(savedNotification));

            return savedNotification;
        } catch (TemplateNotFoundException e) {
            // Если не найден ни IN_APP, ни EMAIL шаблон
            log.error("Не найдены подходящие шаблоны (IN_APP/EMAIL) для типа {} и языка {}. In-App уведомление не сохранено.", type, language, e);
            return null;
        } catch (Exception e) {
            log.error("Ошибка при сохранении или отправке In-App/SSE уведомления для пользователя ID {}: {}", recipient.getUserId(), e.getMessage(), e);
            return null; // Возвращаем null при ошибке сохранения/SSE
        }
    }

    /**
     * Обрабатывает отправку через внешние каналы (Email, Telegram).
     * Перегрузка для Email (attachWebAppButton не нужен).
     */
    private void processExternalChannel(NotificationChannel channel, boolean isEnabled,
                                        UserProfileDto recipientProfile, NotificationType type,
                                        Map<String, String> params, String targetUrl,
                                        String language) {
        processExternalChannel(channel, isEnabled, recipientProfile, type, params, targetUrl, false, language);
    }

    /**
     * Обрабатывает отправку через внешние каналы (Email, Telegram).
     * Основная логика.
     */
    private void processExternalChannel(NotificationChannel channel, boolean isEnabled,
                                        UserProfileDto recipientProfile, NotificationType type,
                                        Map<String, String> params, String targetUrl,
                                        boolean attachWebAppButton,
                                        String language) {
        UUID userId = recipientProfile.getUserId();
        log.debug("Проверка внешнего канала {} для пользователя ID {}. Активен: {}", channel, userId, isEnabled);

        if (!isEnabled) {
            log.debug("Внешний канал {} отключен или недоступен для пользователя ID {}", channel, userId);
            return; // Выходим, если канал неактивен
        }

        Notifier notifier = notifierMap.get(channel);
        if (notifier == null) {
            log.warn("Не найден отправитель (Notifier) для канала {}", channel);
            return; // Выходим, если нет отправителя
        }

        // Если канал активен и отправитель найден, пытаемся отправить
        try {
            String subject = templateService.getSubject(type, language);
            String template = templateService.getTemplate(type, channel, language);
            String formattedText = templateService.format(template, params);

            // Вызываем асинхронный метод send() отправителя
            notifier.send(
                    recipientProfile,
                    subject,
                    formattedText,
                    params,
                    targetUrl,
                    attachWebAppButton
            );
            log.debug("Задание на отправку через канал {} для пользователя ID {} передано исполнителю.", channel, userId);
        } catch (TemplateNotFoundException e) {
            log.error("Не найден шаблон для канала {} (тип {}, язык {}) для пользователя ID {}. Уведомление не отправлено.",
                    channel, type, language, userId, e);
        } catch (Exception e) {
            // Ловим другие возможные ошибки (например, при форматировании)
            log.error("Ошибка при подготовке к отправке уведомления через канал {} для пользователя ID {}: {}",
                    channel, userId, e.getMessage(), e);
        }
    }


    /**
     * Получает детали пользователя из User Service.
     */
    private UserNotificationDetailsDto getUserDetails(UUID userId) {
        try {
            log.debug("Запрос деталей пользователя ID {} из User Service...", userId);
            ResponseEntity<UserNotificationDetailsDto> response = userServiceClient.getUserNotificationDetails(userId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Успешно получены детали пользователя ID {} из User Service.", userId);
                return response.getBody();
            } else {
                log.warn("User Service вернул статус {} при запросе деталей уведомлений для пользователя ID {}", response.getStatusCode(), userId);
                return null;
            }
        } catch (FeignException.NotFound e) {
            log.warn("Пользователь ID {} не найден в User Service.", userId);
            return null;
        } catch (Exception e) {
            log.error("Ошибка при запросе деталей уведомлений пользователя ID {} из User Service: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Получает Telegram ID пользователя, пытаясь сначала из данных User Service,
     * а затем (если там null) из Auth Service.
     * @param userId ID пользователя.
     * @param userDetails Детали, полученные от User Service.
     * @return Строку с Telegram ID или null, если не найден нигде.
     */
    private String resolveTelegramId(UUID userId, UserNotificationDetailsDto userDetails) {
        // 1. Запрашиваем у Auth Service
        log.debug("TelegramId отсутствует в данных User Service для ID {}, запрос в Auth Service...", userId);
        try {
            ResponseEntity<UserTelegramIdDto> response = authServiceClient.getTelegramIdByUserId(userId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String telegramId = response.getBody().getTelegramId();
                if (telegramId != null && !telegramId.isBlank()) {
                    log.info("TelegramId {} получен из Auth Service для пользователя ID {}", telegramId, userId);
                    return telegramId;
                } else {
                    log.debug("Auth Service не вернул telegramId для пользователя ID {}", userId);
                    return null; // Auth Service нашел пользователя, но ID не привязан
                }
            } else {
                // Ошибка от Auth Service (не 2xx)
                log.warn("Auth Service вернул статус {} при запросе telegramId для пользователя ID {}", response.getStatusCode(), userId);
                return null;
            }
        } catch (FeignException.NotFound e) {
            // Пользователь не найден в Auth Service (маловероятно, если он есть в User Service)
            log.warn("Пользователь ID {} не найден в Auth Service при запросе telegramId.", userId);
            return null;
        } catch (Exception e) {
            // Любая другая ошибка при запросе к Auth Service
            log.error("Ошибка при запросе telegramId пользователя ID {} из Auth Service: {}", userId, e.getMessage());
            return null; // Возвращаем null при любой ошибке связи/обработки
        }
    }
}