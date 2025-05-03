package ru.ecosharing.notification_service.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.ecosharing.notification_service.exception.TemplateNotFoundException;
import ru.ecosharing.notification_service.model.enums.NotificationChannel;
import ru.ecosharing.notification_service.model.enums.NotificationType;
import ru.ecosharing.notification_service.service.TemplateService;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реализация сервиса шаблонов, использующая ResourceBundle.
 * Загружает шаблоны из файлов .properties (например, templates_ru.properties, templates_en.properties).
 * Ключи в файлах: ТИП_УВЕДОМЛЕНИЯ.КАНАЛ (для текста) и ТИП_УВЕДОМЛЕНИЯ.subject (для темы).
 * Поддерживает кэширование ResourceBundle и fallback на язык по умолчанию.
 */
@Slf4j
@Service
public class TemplateServiceImpl implements TemplateService {

    // Базовое имя файла с шаблонами в classpath (без _ru, _en и .properties)
    private static final String BUNDLE_BASENAME = "templates";
    // Суффикс для ключа темы в properties файле
    private static final String SUBJECT_SUFFIX = ".subject";
    // Язык по умолчанию, если язык пользователя не задан или для него нет шаблона
    private static final String DEFAULT_LANG = "ru";

    // Кэш для ResourceBundle (Locale -> Bundle) для производительности
    private final Map<Locale, ResourceBundle> bundleCache = new ConcurrentHashMap<>();

    /**
     * Получает локализованную тему (заголовок) уведомления.
     */
    @Override
    public String getSubject(NotificationType type, String language) throws TemplateNotFoundException {
        if (type == null) {
            log.error("Попытка получить тему для null типа уведомления.");
            return "Уведомление от EcoSharing";
        }
        String key = type.name() + SUBJECT_SUFFIX; // Формируем ключ (например, RENTAL_REQUEST.subject)
        return getResourceString(key, language); // Получаем строку из ResourceBundle
    }

    /**
     * Получает локализованный шаблон текста уведомления.
     */
    @Override
    public String getTemplate(NotificationType type, NotificationChannel channel, String language) throws TemplateNotFoundException {
        if (type == null || channel == null) {
            log.error("Попытка получить шаблон для null типа ({}) или канала ({}) уведомления.", type, channel);
            throw new TemplateNotFoundException("Тип и канал уведомления не могут быть null при получении шаблона.");
        }
        // Формируем ключ (например, RENTAL_REQUEST.EMAIL или RENTAL_REQUEST.IN_APP)
        String key = type.name() + "." + channel.name();
        return getResourceString(key, language); // Получаем строку из ResourceBundle
    }

    /**
     * Форматирует шаблон, заменяя плейсхолдеры {{key}} значениями из карты.
     */
    @Override
    public String format(String template, Map<String, String> params) {
        if (template == null || template.isEmpty()) {
            log.warn("Попытка форматировать пустой или null шаблон.");
            return ""; // Возвращаем пустую строку
        }
        // Если параметров нет, возвращаем исходный шаблон
        if (params == null || params.isEmpty()) {
            return template;
        }

        String result = template;
        // Итерируемся по параметрам и заменяем плейсхолдеры
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            // Используем Objects.toString для безопасной обработки null значений в параметрах
            String value = Objects.toString(entry.getValue(), ""); // null заменяем на ""
            result = result.replace(placeholder, value);
        }
        // Логируем, если остались незамененные плейсхолдеры (полезно для отладки)
        if (result.contains("{{") && result.contains("}}")) {
            log.warn("В отформатированном шаблоне остались плейсхолдеры: {}", result);
        }
        return result;
    }

    /**
     * Внутренний метод для получения строки из ResourceBundle с учетом языка и кэша.
     * @param key Ключ строки в properties файле.
     * @param language Код языка (например, "ru", "en").
     * @return Найденная строка.
     * @throws TemplateNotFoundException Если строка не найдена ни для указанного, ни для дефолтного языка.
     */
    private String getResourceString(String key, String language) throws TemplateNotFoundException {
        // Определяем локаль для запроса
        Locale requestedLocale = Locale.forLanguageTag(language != null && !language.isBlank() ? language : DEFAULT_LANG);
        Locale defaultLocale = Locale.forLanguageTag(DEFAULT_LANG);

        ResourceBundle bundle = null;
        boolean useDefault = false;

        try {
            // Пытаемся получить бандл для запрошенного языка (из кэша или загрузить)
            bundle = getBundle(requestedLocale);
            if (bundle != null && bundle.containsKey(key)) {
                return bundle.getString(key); // Нашли в запрошенном языке
            }

            // Если не нашли и запрошенный язык не дефолтный, пробуем дефолтный язык
            if (!requestedLocale.equals(defaultLocale)) {
                log.warn("Ключ '{}' не найден для языка '{}', попытка использовать язык по умолчанию '{}'.", key, requestedLocale.toLanguageTag(), defaultLocale.toLanguageTag());
                useDefault = true;
                bundle = getBundle(defaultLocale); // Получаем дефолтный бандл
                if (bundle != null && bundle.containsKey(key)) {
                    return bundle.getString(key); // Нашли в дефолтном языке
                }
            }

            // Если не нашли нигде
            String errorMessage = String.format("Ключ шаблона '%s' не найден ни для языка '%s'%s.",
                    key, requestedLocale.toLanguageTag(), useDefault ? ", ни для языка по умолчанию '" + defaultLocale.toLanguageTag() + "'" : "");
            log.error(errorMessage);
            throw new TemplateNotFoundException(errorMessage);

        } catch (MissingResourceException e) {
            String errorLocaleTag = useDefault ? defaultLocale.toLanguageTag() : requestedLocale.toLanguageTag();
            log.error("Ошибка доступа к ResourceBundle для языка '{}': {}", errorLocaleTag, e.getMessage());
            throw new TemplateNotFoundException("Не найден файл шаблонов для языка: " + errorLocaleTag, e);
        }
    }

    /**
     * Получает ResourceBundle из кэша или загружает его.
     * @param locale Локаль (язык).
     * @return ResourceBundle или null, если файл не найден.
     */
    private ResourceBundle getBundle(Locale locale) {
        return bundleCache.computeIfAbsent(locale, loc -> {
            try {
                log.debug("Загрузка ResourceBundle для локали: {}", loc);
                return ResourceBundle.getBundle(BUNDLE_BASENAME, loc, new UTF8Control());
            } catch (MissingResourceException e) {
                log.warn("ResourceBundle для локали '{}' не найден.", loc);
                return null;
            }
        });
    }

    /**
     * Вспомогательный класс для ResourceBundle.Control, обеспечивающий чтение
     * .properties файлов в кодировке UTF-8.
     */
    public static class UTF8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IOException {
            // Формируем имя файла: baseName + "_" + language + ".properties"
            String bundleName = toBundleName(baseName, locale);
            // Формируем имя ресурса для ClassLoader'а
            String resourceName = toResourceName(bundleName, "properties");
            ResourceBundle bundle = null;
            InputStream stream = null;

            // Логика перезагрузки (если reload=true)
            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url != null) {
                    URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false); // Отключаем кэширование URLConnection
                        stream = connection.getInputStream();
                    }
                }
            } else {
                // Стандартная загрузка ресурса
                stream = loader.getResourceAsStream(resourceName);
            }

            // Если ресурс найден, читаем его как PropertyResourceBundle в UTF-8
            if (stream != null) {
                try {
                    // Используем InputStreamReader с UTF-8
                    bundle = new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
                } finally {
                    stream.close(); // Обязательно закрываем поток
                }
            } else {
                log.trace("Ресурс '{}' не найден для локали {}", resourceName, locale);
            }
            return bundle;
        }
    }
}