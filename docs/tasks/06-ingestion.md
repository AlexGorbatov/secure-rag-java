# 06. Загрузка и индексация документа — `POST /api/v1/documents`

## Зачем

Пользователь загружает PDF/DOCX, приложение извлекает текст, режет на чанки, строит эмбеддинги и
кладёт чанки в `vector_store` **вместе с правами доступа**. Это самый длинный сценарий в проекте —
здесь ты поймёшь транзакции, работу с файлами и то, как права попадают в векторный индекс.

## Кто кого вызывает

```
web/DocumentController.upload(MultipartFile file, List<String> groups, @AuthenticationPrincipal Jwt jwt)
 ├─ service/EntitlementsResolver.resolve(jwt) → Entitlements
 └─ service/DocumentIngestionService.ingest(file, groups, entitlements)      ← БЕЗ @Transactional
     ├─ 1. проверить группы: каждая из groups ∈ entitlements.groups()  иначе GroupNotAllowedException
     ├─ 2. service/DocumentParser.parse(file)                              ← Tika
     │      ├─ определить РЕАЛЬНЫЙ тип: new Tika().detect(inputStream, filename)
     │      ├─ не из списка разрешённых → UnsupportedDocumentException
     │      └─ new TikaDocumentReader(file.getResource()).get() → List<Document> (текст)
     ├─ 3. service/DocumentService.createProcessing(...)                   ← @Transactional
     │      └─ DocumentRepository.save(new DocumentEntity(id, sub, ...))   → INSERT documents
     ├─ 4. на страницы metadata: document_id, owner, allowed_groups, title
     │      TokenTextSplitter.apply(pages) → чанки (metadata копируется в каждый)
     ├─ 5. VectorStore.add(chunks)                                         → эмбеддинги + INSERT vector_store
     ├─ 6. service/DocumentService.markReady(id, chunks.size())            ← @Transactional
     └─ ошибка на 4–5 → vectorStore.delete(фильтр document_id == id) + DocumentService.markFailed(id)
 ← 201 Created, Location: /api/v1/documents/{id}, тело DocumentResponse
```

## Что создать

| Файл | Содержимое |
|---|---|
| `web/DocumentController` | `@PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)`, параметры `@RequestPart MultipartFile file`, `@RequestParam(defaultValue = "") List<String> groups` |
| `web/dto/DocumentResponse` | `record(UUID id, String title, String status, int chunkCount, Set<String> allowedGroups, Instant createdAt)` + `static from(DocumentEntity)` |
| `service/DocumentParser` | `ParsedDocument parse(MultipartFile)`; `record ParsedDocument(String title, String contentType, List<Document> pages)` |
| `service/DocumentService` | `@Transactional` методы: `createProcessing`, `markReady`, `markFailed` |
| `service/DocumentIngestionService` | оркестрация шагов 1–6, **без** `@Transactional` |
| `config/IngestionConfig` | бин `TokenTextSplitter` |

## Ключевые вызовы

```java
// Tika: реальный тип файла, а не то, что прислал клиент
String detected = new org.apache.tika.Tika().detect(file.getInputStream(), file.getOriginalFilename());

// Tika: текст
List<Document> pages = new TikaDocumentReader(file.getResource()).get();

// Чанки
TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(500).withMinChunkSizeChars(200).build();
List<Document> chunks = splitter.apply(pages);

// Метаданные: проще всего положить их на страницы ДО нарезки — splitter копирует metadata
// исходного документа в каждый чанк. Проверь это в тесте: у каждого чанка должен быть document_id.
page.getMetadata().put("document_id", id.toString());
page.getMetadata().put("owner", entitlements.subject());
page.getMetadata().put("allowed_groups", List.copyOf(groups));
page.getMetadata().put("title", title);

// Удалить чанки документа
var b = new FilterExpressionBuilder();
vectorStore.delete(b.eq("document_id", id.toString()).build());
```

Разрешённые типы: `application/pdf`,
`application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`,
`text/markdown`.

```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

## Подводные камни

- **Почему `DocumentIngestionService` без `@Transactional`.** Эмбеддинги — сетевой вызов на
  секунды. Держать всё это время открытую транзакцию и соединение с БД нельзя. Поэтому две короткие
  транзакции в `DocumentService`.
- **Self-invocation.** Если вызвать `@Transactional`-метод из того же класса (`this.markReady()`),
  транзакции **не будет** — прокси Spring обходится. Поэтому транзакционные методы — в отдельном
  бине `DocumentService`.
- **Владелец — только из токена.** В запросе не должно быть параметра `owner`.
- **Группы — только свои.** Alice не может расшарить документ на `engineering`, если сама не в ней.
- **Не строй путь к файлу из `getOriginalFilename()`** — файл вообще не нужно сохранять на диск.
- **Не логируй текст документа.** Логируй `id`, тип, размер, число чанков.

## Как проверить

Тесты:
- `service/DocumentIngestionServiceTest` (`@SpringBootTest` + Testcontainers + `test`-профиль):
  - `ingestsTextFileAndStoresChunksWithAcl` — загрузить `MockMultipartFile("file", "policy.txt",
    "text/plain", bytes)`; после — в `documents` статус `READY`, `chunkCount > 0`, а
    `vectorStore.similaritySearch(...)` находит чанк с `metadata.owner == sub`.
  - `rejectsSharingWithForeignGroup` — alice шарит на `engineering` → `GroupNotAllowedException`,
    в `documents` ничего не появилось.
  - `rejectsUnsupportedType` — файл с байтами PNG, но именем `.txt` → `UnsupportedDocumentException`.
- `web/DocumentControllerTest`: без токена 401; с `jwt()` → 201 и заголовок `Location`.

Вручную:

```bash
curl -H "Authorization: Bearer $TOKEN" -F file=@README.md -F groups=all-staff localhost:8080/api/v1/documents
```

## Как сделано в проекте

| Файл | Роль |
|---|---|
| `config/IngestionProperties` | `securerag.ingestion.*`: размер чанка (200 токенов — влезает в ONNX-модель), лимит текста (2 млн символов), длина названия |
| `config/IngestionConfig` | бины `Tika` и `TokenTextSplitter` |
| `service/ChunkMetadata` | константы ключей метаданных (`document_id`, `owner`, `allowed_groups`, `title`) — их же будет читать поиск в задаче 08 |
| `service/DocumentParser` | тип по байтам (Tika), список разрешённых, текст с лимитом (`BodyContentHandler(max)` — защищает от zip-бомб), название без пути |
| `service/DocumentService` | короткие транзакции: `createProcessing`, `markReady`, `markFailed` |
| `service/DocumentIngestionService` | оркестрация; всё, что может отклонить файл, выполняется **до** первой записи в БД |
| `web/DocumentController`, `web/dto/DocumentResponse` | `POST /api/v1/documents`, 201 + `Location` |
| `service/exception/UnreadableDocumentException` (422), `DocumentTooLargeException` (413) | новые ошибки |

Тесты: `DocumentParserTest` (9), `DocumentIngestionServiceTest` (5), `DocumentIngestionFailureTest`
(сбой эмбеддингов → FAILED и ноль чанков), `DocumentControllerTest` (5, включая «владелец берётся из
токена, а не из запроса»).

## Готово, когда

- Файл загружается, статус `READY`, в `vector_store` есть чанки с `document_id`, `owner`,
  `allowed_groups`.
- Сбой эмбеддингов оставляет документ `FAILED` и **ни одного** чанка.
