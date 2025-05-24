package ru.ecosharing.listing_service.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PriceType {
    PER_DAY("За день", "Цена указана за один день аренды."),
    PER_WEEK("За неделю", "Цена указана за одну неделю аренды."),
    PER_MONTH("За месяц", "Цена указана за один месяц аренды."),
    FOR_SALE("За покупку", "Цена указана за полную покупку товара.");

    private final String displayName;
    private final String description;
}