package ru.ecosharing.listing_service.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModerationStatus {
    PENDING_MODERATION("Ожидает модерацию", "Объявление создано или изменено и ожидает проверки модератором."),
    ACTIVE("Активно", "Объявление прошло модерацию и видно всем пользователям."),
    INACTIVE("Неактивно", "Объявление скрыто создателем и не видно другим пользователям."),
    NEEDS_REVISION("Требует изменений", "Объявление отклонено модератором с указанием необходимых правок."),
    REJECTED("Отклонено", "Объявление окончательно отклонено модератором (например, из-за нарушения правил).");

    private final String displayName; // Имя для отображения в UI
    private final String description; // Подробное описание статуса
}