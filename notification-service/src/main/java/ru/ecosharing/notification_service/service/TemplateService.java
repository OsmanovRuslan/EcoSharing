package ru.ecosharing.notification_service.service;

import ru.ecosharing.notification_service.model.enums.NotificationChannel;
import ru.ecosharing.notification_service.model.enums.NotificationType;
import ru.ecosharing.notification_service.exception.TemplateNotFoundException;

import java.util.Map;

/**
 * Интерфейс сервиса для работы с шаблонами уведомлений.
 * Позволяет получать и форматировать тексты и темы уведомлений
 * для разных типов, каналов и языков.
 */
public interface TemplateService {

    /**
     * Получает локализованную тему (заголовок) уведомления для указанного типа.
     * Используется преимущественно для Email уведомлений.
     *
     * @param type Тип уведомления.
     * @param language Код языка (например, "ru", "en").
     * @return Строка с темой уведомления.
     * @throws TemplateNotFoundException если тема для данного типа и языка не найдена.
     */
    String getSubject(NotificationType type, String language) throws TemplateNotFoundException;

    /**
     * Получает локализованный шаблон текста уведомления для указанного типа, канала и языка.
     *
     * @param type Тип уведомления.
     * @param channel Канал доставки (EMAIL, TELEGRAM, IN_APP).
     * @param language Код языка.
     * @return Строка с шаблоном уведомления (может содержать плейсхолдеры).
     * @throws TemplateNotFoundException если шаблон для данной комбинации не найден.
     */
    String getTemplate(NotificationType type, NotificationChannel channel, String language) throws TemplateNotFoundException;

    /**
     * Форматирует предоставленный шаблон, заменяя плейсхолдеры вида {{key}}
     * на соответствующие значения из карты параметров.
     * Если параметр в карте null, плейсхолдер заменяется на пустую строку.
     *
     * @param template Строка шаблона с плейсхолдерами.
     * @param params Карта с параметрами (ключ плейсхолдера -> значение). Может быть null или пустой.
     * @return Отформатированная строка или оригинальный шаблон, если params null/пустой.
     */
    String format(String template, Map<String, String> params);
}