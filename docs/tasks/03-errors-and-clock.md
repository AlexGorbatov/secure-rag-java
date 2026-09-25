# 03. Обработка ошибок и `Clock`

## Зачем

Дальше сервисы будут бросать исключения «документ не найден», «файл не поддерживается», «нельзя
поделиться с группой». Нужен один механизм, который превращает их в понятный JSON-ответ с правильным
HTTP-статусом и **ничего не выдаёт наружу** (ни SQL, ни stack trace, ни текст документа).

`Clock` нужен, чтобы в тестах контролировать время (`createdAt`), а не зависеть от `Instant.now()`.

## Что создать

### `service/exception/` — свои исключения

| Класс | Когда бросать | HTTP |
|---|---|---|
| `DocumentNotFoundException(UUID id)` | документа нет **или он чужой** | 404 |
| `UnsupportedDocumentException(String detectedType)` | тип файла не из разрешённых | 415 |
| `DocumentTooLargeException` | (необязательно — Spring сам бросит `MaxUploadSizeExceededException`) | 413 |
| `GroupNotAllowedException(String group)` | пользователь пытается поделиться с чужой группой | 403 |
| `IngestionFailedException(UUID id, Throwable cause)` | не удалось распарсить / получить эмбеддинги | 422 или 502 |

Все наследуй от `RuntimeException`. Сообщение исключения — для логов, не для клиента.

### `web/ApiExceptionHandler`

```java
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail notFound(DocumentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Document not found");
    }
    // ... остальные
}
```

- `ProblemDetail` — стандартный формат ошибок (RFC 9457): `type`, `title`, `status`, `detail`.
- `detail` — **безопасный** текст. Никогда не `e.getMessage()` от чужих исключений.
- Для необработанных `Exception` — один обработчик: логируй с `log.error(..., e)`, клиенту отдавай
  500 с `detail = "Internal error"`.
- Для `MethodArgumentNotValidException` (ошибки `@Valid`) — 400 со списком полей.

### `config/ClockConfig`

```java
@Bean Clock clock() { return Clock.systemUTC(); }
```

В сервисах: `Instant.now(clock)` вместо `Instant.now()`.

## Подводные камни

- **404, а не 403, для чужого документа.** 403 подтверждает, что документ существует — это утечка.
- Не наследуй `ApiExceptionHandler` от `ResponseEntityExceptionHandler`, если не понимаешь, что он
  перехватывает — начни с явных `@ExceptionHandler`.
- 401 от Spring Security до `@RestControllerAdvice` не доходит — это нормально.

## Как проверить

Тест `web/ApiExceptionHandlerTest` (`@WebMvcTest` не подойдёт из-за security — проще временный
тестовый контроллер в тестовых исходниках, который бросает каждое исключение, + `@SpringBootTest`
+ `@AutoConfigureMockMvc` + `jwt()`):

- бросаем `DocumentNotFoundException` → `status 404`, `$.detail == "Document not found"`;
- бросаем `RuntimeException("SELECT * FROM secret")` → 500, в теле **нет** строки `SELECT`.

## Готово, когда

- Каждое своё исключение даёт нужный статус и безопасный `detail`.
- В теле 500 нет текста исходного исключения.
- `Clock` — бин, и ни один сервис не вызывает `Instant.now()` без него.
