# 01. Каркас проекта и окружение ✅

Задача уже сделана. Цель — разобраться, **почему** оно устроено так, прежде чем писать своё.

## Что прочитать и что понять

| Файл | Вопрос, на который надо ответить |
|---|---|
| `pom.xml` | Откуда берутся версии Spring AI (`spring-ai-bom`)? Почему у Spring AI-стартеров нет `<version>`? |
| `src/main/resources/application.properties` | Зачем `spring.ai.model.image=none` и остальные `none`? Что будет без них? |
| `src/main/resources/application-azure.properties` | Как один и тот же `spring-ai-starter-model-openai` работает с Azure? |
| `src/test/resources/application-test.properties` | Почему в тестах `spring.ai.model.chat=none` и `embedding=transformers`? |
| `compose.yaml` | Как Spring Boot узнаёт, что сервис `pgvector` — это его база? (подсказка: `labels`) |
| `src/test/java/.../TestcontainersConfiguration.java` | Что делает `@ServiceConnection`? Почему образ `pgvector/pgvector`, а не `postgres`? |
| `src/test/java/.../TestSecureRagJavaApplication.java` | Чем `spring-boot:test-run` отличается от `spring-boot:run`? |

## Эксперименты (5–10 минут каждый)

1. Закомментируй `spring.ai.model.audio.speech=none`, запусти `./mvnw test`. Прочитай ошибку в
   `target/surefire-reports/*.txt` (`grep '^Caused by'`). Верни строку.
2. Убери `@ActiveProfiles("test")` из `SecureRagJavaApplicationTests`. Что сломалось и почему?
3. Запусти `./mvnw spring-boot:test-run`, открой http://localhost:8080/actuator/health.
   Почему `401`? (ответ — в задаче 02).

## Готово, когда

- Ты можешь объяснить, какой бин `EmbeddingModel` создаётся в каждом профиле и почему.
- Понимаешь, откуда в тестах берётся база данных и откуда — при `spring-boot:run`.
