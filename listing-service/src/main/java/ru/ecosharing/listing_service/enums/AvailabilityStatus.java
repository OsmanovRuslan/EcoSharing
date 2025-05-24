package ru.ecosharing.listing_service.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AvailabilityStatus {
    AVAILABLE("Свободно", "Объявление доступно для аренды или покупки."),
    RENTED("В аренде / Занято", "Объявление в настоящее время арендовано, забронировано или продано и недоступно.");

    private final String displayName;
    private final String description;
}