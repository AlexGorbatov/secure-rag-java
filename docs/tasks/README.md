# План реализации secure-rag-java

Проект разбит на задачи, которые делаются по порядку: каждая опирается на предыдущие и
заканчивается зелёным `./mvnw verify`. В каждой задаче описано:

- **Зачем** — что появится в приложении и какую идею ты на этом поймёшь;
- **Что создать** — файлы, пакеты, классы, их методы и кто кого вызывает;
- **Ключевые вызовы** — точные API Spring / Spring AI (сверены с версиями из `pom.xml`);
- **Подводные камни** — где обычно ошибаются;
- **Как проверить** — тесты и ручная проверка через curl / Swagger;
- **Готово, когда** — критерии завершения.

Код целиком не приводится — только сигнатуры, SQL-схема и ключевые вызовы. Писать — тебе.

## Задачи

| # | Задача | Статус | Что поймёшь |
|---|---|---|---|
| 01 | [Каркас проекта и окружение](01-bootstrap.md) | ✅ сделано | профили, Testcontainers, Docker Compose |
| 02 | [Keycloak, JWT и `/api/v1/me`](02-security-and-me.md) | ✅ сделано | как токен превращается в права |
| 03 | [Обработка ошибок и `Clock`](03-errors-and-clock.md) | ✅ сделано | `ProblemDetail`, `@RestControllerAdvice` |
| 04 | [Таблица `documents`: миграция, entity, repository](04-documents-schema.md) | ✅ сделано | Flyway, JPA, `ddl-auto=validate` |
| 05 | [Векторное хранилище pgvector](05-vector-store.md) | ✅ сделано | эмбеддинги, размерность, `VectorStore` |
| 06 | [Загрузка и индексация документа](06-ingestion.md) | ✅ сделано | Tika → чанки → эмбеддинги, транзакции |
| 07 | [Список, просмотр и удаление документов](07-documents-api.md) | ✅ сделано | фильтр доступа в SQL, 404 вместо 403 |
| 08 | [Поиск с фильтром доступа](08-retrieval.md) | ✅ сделано | **главная идея проекта** — фильтр внутри векторного поиска |
| 09 | [Чат с цитатами](09-chat.md) | ✅ сделано | `ChatClient`, промпт, prompt injection |
| 10 | [Матрица тестов доступа](10-access-tests.md) | ✅ сделано | как доказать, что Bob не видит Alice |
| 11 | [Эксплуатация: Actuator, логи, Azure, Entra ID](11-operations.md) | ✅ сделано | что нужно перед реальным запуском |

## Раскладка пакетов

```
com.altronixsoft.securerag
├── config/       @Configuration, @ConfigurationProperties
├── web/          @RestController
│   └── dto/      request/response records (HTTP-контракт)
├── service/      бизнес-логика, @Transactional
├── repository/   Spring Data JPA
├── model/        JPA entity и внутренние типы (Entitlements, …)
└── startup/      задачи при старте (если понадобятся)
```

Тесты лежат в том же пакете, что и тестируемый класс.

## Общая схема, к которой идём

```
                    ┌───────────── Spring Security (JWT) ─────────────┐
HTTP ──► web/*Controller ──► service/* ──► repository/DocumentRepository ──► documents
                                   │
                                   ├──► Spring AI VectorStore ──► vector_store (pgvector)
                                   │          └──► EmbeddingModel (OpenAI / ONNX)
                                   └──► Spring AI ChatClient ──► ChatModel (OpenAI / Azure)
```

Права доступа проверяются в трёх местах: фильтр Spring Security (кто ты), `EntitlementsResolver`
(что тебе можно — только из токена), `WHERE` в SQL (какие строки ты видишь). Модель стоит **после**
всех трёх и не может на них повлиять.

## Как работать над задачей

1. Прочитай задачу целиком, прежде чем писать код.
2. Начни с теста — он показывает, что именно ты делаешь.
3. Пиши маленькими шагами, запускай `./mvnw test -Dtest=ИмяТеста` после каждого.
4. В конце — `./mvnw verify` и ручная проверка из раздела «Как проверить».
5. Отметь задачу ✅ в таблице выше.

## Полезные команды

```bash
docker compose up -d                   # Postgres + Keycloak
./mvnw spring-boot:test-run            # приложение без API-ключей (ONNX, без чат-модели)
OPENAI_API_KEY=... ./mvnw spring-boot:run
./mvnw test -Dtest=DocumentRepositoryTest
./mvnw clean verify                    # после переноса классов между пакетами — обязательно clean
```

Токен для curl:

```bash
TOKEN=$(curl -s http://localhost:8180/realms/securerag/protocol/openid-connect/token \
  -d grant_type=password -d client_id=securerag-web -d username=alice -d password=alice \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/me
```

Swagger UI: http://localhost:8080/swagger-ui.html → **Authorize** → `login` → войти как alice (`client_secret` оставить пустым).
