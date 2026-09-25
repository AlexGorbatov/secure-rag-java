# 11. Эксплуатация: Actuator, логи, Azure, Entra ID

## Зачем

Приложение работает локально. Перед реальным окружением нужно закрыть то, что для разработки
допустимо, а в продакшене опасно.

## Подзадачи

### 11.1 Actuator

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
management.endpoint.health.probes.enabled=true
```

Никогда не открывай `env`, `configprops`, `heapdump`, `threaddump` — там секреты и текст документов.
Тест: `GET /actuator/env` → 404 (не открыт), `GET /actuator/health` без токена → 200 без деталей.

### 11.2 Логи

- Логируй `documentId`, `sub`, число чанков, время ответа модели.
- **Не** логируй: токены, текст документов, чанки, промпты, ответы модели.
- Проверь `grep -rn "log\." src/main/java` — нет ли `getText()`, `prompt`, `answer` в логах.

### 11.3 Таймауты и лимиты модели

- Таймаут HTTP-клиента OpenAI (смотри свойства `spring.ai.openai.*timeout*` в IDE-автодополнении).
- Ограничь `topK` и длину вопроса (уже в DTO), максимальный размер ответа модели
  (`spring.ai.openai.chat.max-tokens`).

### 11.4 Профиль Azure OpenAI

- Создай в Azure деплойменты чат-модели и `text-embedding-3-small`.
- Добавь в `application-azure.properties` `spring.ai.openai.embedding.dimensions=384`.
- Запуск: `./mvnw spring-boot:run -Dspring-boot.run.profiles=azure` с переменными из `.env.example`.
- Проверь, что загрузка и чат работают так же, как с OpenAI — код менять не нужно.

### 11.5 Microsoft Entra ID вместо Keycloak

- Регистрация приложения в Entra ID, **App roles** (`user`), выдача групп в токене
  (Token configuration → groups claim).
- Переменные:
  - `OAUTH2_ISSUER_URI=https://login.microsoftonline.com/<tenant-id>/v2.0`
  - `OAUTH2_AUDIENCE=<application-id-uri или client-id>`
  - `SECURERAG_CLAIMS_ROLES=roles`
- Внимание: Entra ID кладёт в `groups` **идентификаторы** групп (GUID), а не имена. Права в
  `document_groups` тогда тоже должны храниться как GUID — реши это до переключения.

### 11.6 Выключить Swagger в проде

`OPENAPI_ENABLED=false`.

## Как сделано в проекте

- **Actuator:** `include=health,info`, `show-details=never`, `show-components=never`, пробы
  liveness/readiness. Без токена открыты `/actuator/health` и `/actuator/health/**` (только UP/DOWN).
  `ActuatorExposureTest` (12): `env`, `configprops`, `heapdump`, `threaddump`, `loggers`, `mappings`,
  `beans`, `metrics` — 404 даже с токеном.
- **Логи:** логирование промптов, ответов и результатов поиска в наблюдениях Spring AI явно
  выключено. `LogHygieneTest` проходит загрузку → поиск → чат → сбой модели и проверяет, что в логе
  есть id документа, но нет текста документа, вопроса и ответа.
- **Лимиты модели:** `spring.ai.openai.timeout` / `chat.timeout` = 30s, `max-retries` = 2,
  `chat.max-completion-tokens` = 800 (всё переопределяется переменными `OPENAI_*`).
- **Сбои провайдера:** поиск (и чат, который начинается с поиска) при недоступных эмбеддингах или
  векторной БД → **503** «временно недоступно» (`SearchUnavailableException`), чат-модель → **502**.
  Найдено при ручной проверке профиля `azure`: до этого поиск отвечал 500. `SearchOutageTest` (2).
- **Azure:** профиль проверен запуском с фиктивными переменными — приложение стартует, схема
  pgvector валидна; настоящий вызов в Azure не выполнялся (нет ресурса).
- **Entra ID:** профиль `entra` (`application-entra.properties`): issuer, audience, `roles`-claim,
  адреса входа Swagger UI. `EntraProfileTest` (3). В Entra ID `groups` — это GUID групп, поэтому
  документы расшариваются по GUID. Две ловушки: в манифесте API нужен
  `"accessTokenAcceptedVersion": 2` (иначе issuer v1 `sts.windows.net` → 401); у пользователя в
  >200 группах claim `groups` не приходит — он видит только свои документы (безопасный отказ).
- **Swagger в проде:** `OPENAPI_ENABLED=false`.

## Готово, когда

- Actuator отдаёт только `health` и `info`.
- В логах нет содержимого документов.
- Одно и то же приложение без изменений кода работает с профилями default/azure и с
  Keycloak/Entra ID.
