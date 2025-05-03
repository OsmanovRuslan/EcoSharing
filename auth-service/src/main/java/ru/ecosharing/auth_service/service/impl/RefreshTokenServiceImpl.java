package ru.ecosharing.auth_service.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ecosharing.auth_service.config.AppProperties; // Используем общие свойства
import ru.ecosharing.auth_service.exception.TokenRefreshException;
import ru.ecosharing.auth_service.model.RefreshToken;
import ru.ecosharing.auth_service.model.UserCredentials;
import ru.ecosharing.auth_service.repository.RefreshTokenRepository;
import ru.ecosharing.auth_service.repository.UserCredentialsRepository;
import ru.ecosharing.auth_service.security.JwtTokenProvider; // Нужен для создания строки токена
import ru.ecosharing.auth_service.service.RefreshTokenService;


import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Реализация сервиса для управления Refresh токенами.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final JwtTokenProvider jwtTokenProvider; // Для генерации строки токена
    private final AppProperties appProperties; // Для получения времени жизни

    /**
     * Находит Refresh токен по его строковому значению.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        log.trace("Поиск refresh токена: {}...", token.substring(0, Math.min(token.length(), 10)));
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Создает и сохраняет новый Refresh токен для пользователя.
     * Удаляет предыдущие токены этого пользователя для предотвращения накопления.
     */
    @Override
    @Transactional // Операция записи, нужна транзакция
    public RefreshToken createRefreshToken(UUID userId, String username) {
        log.debug("Создание нового refresh токена для пользователя ID: {}, username: {}", userId, username);
        // 1. Находим учетные данные пользователя
        UserCredentials userCredentials = userCredentialsRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    // Эта ситуация не должна возникать в нормальном потоке
                    log.error("Попытка создать refresh токен для несуществующего пользователя ID: {}", userId);
                    // Можно бросить более специфичное исключение
                    return new RuntimeException("Не найдены учетные данные для пользователя с ID: " + userId);
                });

        // 2. Удаляем все существующие refresh токены для этого пользователя
        int deletedCount = refreshTokenRepository.deleteByUserCredentials(userCredentials);
        if (deletedCount > 0) {
            log.debug("Удалено {} старых refresh токенов для пользователя ID: {}", deletedCount, userId);
            refreshTokenRepository.flush(); // Принудительно выполняем удаление перед вставкой
        }

        // 3. Создаем новый объект RefreshToken
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserCredentials(userCredentials);
        // Устанавливаем срок годности на основе настроек
        refreshToken.setExpiryDate(Instant.now().plusMillis(appProperties.getJwtRefreshExpirationMs()));
        // Генерируем уникальную строку токена (используем JWT провайдер для этого)
        refreshToken.setToken(jwtTokenProvider.createRefreshToken(userId, username));

        // 4. Сохраняем новый токен в базе данных
        refreshToken = refreshTokenRepository.save(refreshToken);
        log.info("Создан новый refresh токен для пользователя ID: {} со сроком действия до {}",
                userId, refreshToken.getExpiryDate());
        return refreshToken;
    }

    /**
     * Проверяет срок действия токена. Удаляет и бросает исключение, если истек.
     */
    @Override
    @Transactional // Нужна транзакция для возможного удаления
    public RefreshToken verifyExpiration(RefreshToken token) {
        // Сравниваем дату истечения с текущим моментом
        if (token.getExpiryDate().isBefore(Instant.now())) {
            // Токен истек
            refreshTokenRepository.delete(token); // Удаляем его из базы
            log.warn("Refresh токен ID {} (для пользователя ID {}) истек ({}) и был удален.",
                    token.getId(), token.getUserCredentials().getUserId(), token.getExpiryDate());
            // Бросаем исключение, которое будет обработано выше
            throw new TokenRefreshException(token.getToken(), "Refresh токен истек. Пожалуйста, войдите снова.");
        }
        // Если срок действия не истек, возвращаем тот же токен
        log.trace("Refresh токен ID {} валиден.", token.getId());
        return token;
    }

    /**
     * Удаляет все Refresh токены для указанного пользователя.
     * Используется при выходе из системы (logout).
     */
    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        log.debug("Удаление всех refresh токенов для пользователя ID: {}", userId);
        // Находим пользователя (не обязательно, но для логирования полезно)
        userCredentialsRepository.findByUserId(userId).ifPresent(userCredentials -> {
            int deletedCount = refreshTokenRepository.deleteByUserCredentials(userCredentials);
            if (deletedCount > 0) {
                log.info("Удалено {} refresh токенов для пользователя ID: {}", deletedCount, userId);
            } else {
                log.debug("Не найдено refresh токенов для удаления у пользователя ID: {}", userId);
            }
        });
        // Если пользователь не найден, ничего не делаем
    }

    /**
     * Удаляет конкретный Refresh токен по его строковому значению.
     * Может использоваться, если токен скомпрометирован или принудительно отозван.
     */
    @Override
    @Transactional
    public void deleteToken(String token) {
        log.debug("Попытка удаления refresh токена: {}...", token.substring(0, Math.min(token.length(), 10)));
        // Находим токен по строке
        refreshTokenRepository.findByToken(token).ifPresent(refreshToken -> {
            UUID userId = refreshToken.getUserCredentials().getUserId(); // Получаем ID пользователя для лога
            refreshTokenRepository.delete(refreshToken); // Удаляем найденный токен
            log.info("Refresh токен ID {} (для пользователя ID {}) удален по запросу.", refreshToken.getId(), userId);
        });
        // Если токен не найден, метод просто ничего не делает
    }
}