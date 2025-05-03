package ru.ecosharing.telegram_bot_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import ru.ecosharing.telegram_bot_service.dto.SubscriptionRequestDto;
import ru.ecosharing.telegram_bot_service.model.SubscriptionPeriod;

/**
 * Клиент для взаимодействия с сервисом подписок
 */
@FeignClient(name = "subscription-service")
public interface SubscriptionServiceClient {

    /**
     * Получает стоимость подписки для указанного периода
     *
     * @param period Период подписки
     * @return Стоимость подписки в копейках/центах
     */
    @GetMapping("/api/subscriptions/amount")
    int getSubscriptionAmount(@RequestParam SubscriptionPeriod period);

    /**
     * Активирует подписку для пользователя
     *
     * @param request Информация о подписке
     * @return true, если подписка успешно активирована
     */
    @PostMapping("/api/subscriptions/activate")
    boolean activateSubscription(@RequestBody SubscriptionRequestDto request);

    /**
     * Активирует подписку для пользователя (удобный метод)
     */
    default boolean activateSubscription(
            String userId,
            SubscriptionPeriod period,
            Integer amount,
            String currency,
            String paymentId) {

        SubscriptionRequestDto request = new SubscriptionRequestDto();
        request.setUserId(userId);
        request.setPeriod(period);
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setPaymentId(paymentId);

        return activateSubscription(request);
    }
}