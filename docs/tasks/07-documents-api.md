# 07. Список, просмотр и удаление документов

## Зачем

Первые эндпоинты, которые **читают** данные с учётом прав. Здесь ты закрепишь главное правило:
права проверяются в запросе к базе, а чужой документ неотличим от несуществующего (404).

## Эндпоинты

| Метод | Путь | Кто может | Ответ |
|---|---|---|---|
| `GET` | `/api/v1/documents` | любой аутентифицированный | свои + расшаренные на его группы |
| `GET` | `/api/v1/documents/{id}` | владелец или член группы | 200 или **404** |
| `DELETE` | `/api/v1/documents/{id}` | **только владелец** | 204 или 404 |

## Кто кого вызывает

```
GET /api/v1/documents
 └─ DocumentController.list(jwt)
     └─ DocumentQueryService.listVisible(entitlements)               @Transactional(readOnly = true)
         └─ repository.findAllVisibleTo(entitlements)
 ← List<DocumentResponse>

GET /api/v1/documents/{id}
 └─ DocumentQueryService.getVisible(id, entitlements)
     └─ repository.findVisibleById(id, entitlements) .orElseThrow(DocumentNotFoundException)

DELETE /api/v1/documents/{id}
 └─ DocumentService.delete(id, entitlements)                         @Transactional
     ├─ repository.findByIdAndOwnerSub(id, sub)  пусто → DocumentNotFoundException (404!)
     ├─ vectorStore.delete(b.eq("document_id", id.toString()).build())
     └─ repository.delete(entity)                                    → document_groups удалятся каскадом
```

## Что создать

| Файл | Что |
|---|---|
| `service/DocumentQueryService` | `List<DocumentEntity> listVisible(Entitlements)`, `DocumentEntity getVisible(UUID, Entitlements)` |
| `service/DocumentService` | + `void delete(UUID, Entitlements)` |
| `web/DocumentController` | + `@GetMapping`, `@GetMapping("/{id}")`, `@DeleteMapping("/{id}")` с `@ResponseStatus(NO_CONTENT)` |

Добавь `@Operation` / `@ApiResponse` — эндпоинты появятся в Swagger с описанием.

## Подводные камни

- **Никогда** не делай `findById(id)` и проверку прав в Java для списков — только для одной строки
  (как в `delete`). Список фильтруется в SQL.
- Удаление чанков из `vector_store` и строки из `documents` — это две разные системы. Если
  `vectorStore.delete` упал, транзакция откатит удаление строки — это правильный порядок: лучше
  остаться с документом, чем с «осиротевшими» чанками, которые ещё и найдутся поиском.
- Не отдавай entity в ответ — только `DocumentResponse`.

## Как проверить

`web/DocumentControllerTest` (или отдельный `DocumentAccessTest`), с `jwt()` для alice и bob:

- alice загрузила `hr`-документ → alice видит его в списке, bob — нет.
- bob `GET /documents/{id-alice}` → **404**, а не 403.
- bob `DELETE /documents/{id-alice}` → 404, документ остался.
- alice шарит документ на `all-staff` → bob видит его в списке, но `DELETE` → 404 (он не владелец).
- alice `DELETE` → 204, в `vector_store` нет чанков с этим `document_id`.

## Как сделано в проекте

- `service/DocumentQueryService` — `@Transactional(readOnly = true)`, `listVisible` и `getVisible`;
  вся логика видимости — в `DocumentRepository`.
- `service/DocumentService.delete` — `findByIdAndOwnerSub` (владелец проверяется в SQL), затем
  `vectorStore.delete(ChunkMetadata.belongsTo(id))` и `repository.delete`. `PgVectorStore` пишет
  через тот же `DataSource`, поэтому удаление чанков идёт **в той же транзакции**, что и удаление
  строки: при сбое откатываются оба. Это проверяет `DocumentServiceTest`.
- `ChunkMetadata.belongsTo(id)` — единый фильтр «все чанки документа» для удаления и очистки после
  сбоя загрузки.
- Тесты: `DocumentAccessApiTest` (10 — матрица через HTTP), `DocumentServiceTest` (2).

## Готово, когда

Все сценарии выше зелёные, и ты можешь объяснить, почему 404, а не 403.
