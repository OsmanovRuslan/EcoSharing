package ru.ecosharing.listing_service.elasticsearch.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import ru.ecosharing.listing_service.enums.AvailabilityStatus;
import ru.ecosharing.listing_service.enums.ModerationStatus;
import ru.ecosharing.listing_service.enums.PriceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "listings_idx") // Имя индекса в Elasticsearch
public class ListingDocument {

    @Id // ID документа в Elasticsearch, будем использовать ID объявления
    private String id; // В ES ID обычно строковый, можно использовать UUID.toString()

    // --- Основные поля для поиска и фильтрации ---
    @Field(type = FieldType.Text, analyzer = "standard") // Полнотекстовый поиск
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword) // Точное совпадение, фильтрация
    private UUID categoryId;

    @Field(type = FieldType.Text) // Может быть и Keyword, если поиск по точному совпадению города
    private String locationText;

    @Field(type = FieldType.Double) // Числовой тип для цен
    private BigDecimal price;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Keyword)
    private PriceType priceType;

    @Field(type = FieldType.Keyword)
    private ModerationStatus moderationStatus; // Важно для фильтрации "только активные"

    @Field(type = FieldType.Keyword)
    private AvailabilityStatus availabilityStatus;

    // --- Поля для сортировки и дополнительной информации ---
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createdAt;

    @Field(type = FieldType.Integer)
    private Integer viewCount;

    // --- Информация о владельце (для отображения в результатах поиска) ---
    @Field(type = FieldType.Keyword)
    private UUID ownerUserId;

    @Field(type = FieldType.Text) // Или Keyword, если только точный поиск по username
    private String ownerUsername;

    @Field(type = FieldType.Keyword) // URL обычно keyword
    private String ownerAvatarUrl; // Опционально, для показа аватара в результатах

    // --- Прочие поля, которые могут быть полезны ---
    @Field(type = FieldType.Keyword) // URL изображений
    private String mainImageUrl;

    @Field(type = FieldType.Keyword) // Коллекция URL-ов
    private List<String> additionalImageUrls;

    // Если категории имеют имена, и мы хотим искать/фильтровать по имени категории в ES
    @Field(type = FieldType.Text, analyzer = "standard")
    private String categoryName; // Денормализованное имя категории

}