# 08. Поиск с фильтром доступа — `POST /api/v1/search`

## Зачем

**Это сердце проекта.** Поиск похожих чанков, в котором фильтр по правам стоит **внутри**
SQL-запроса к pgvector. Чанки чужих документов не читаются из базы вообще — значит, дальше (в
промпт, в ответ, в цитаты) они попасть не могут.

Отдельный эндпоинт поиска нужен, чтобы отладить поиск до того, как подключать LLM.

## Почему не «найти top-K и отфильтровать»

```
ПЛОХО:  SELECT ... ORDER BY embedding <=> q LIMIT 5        → 5 чанков, 4 из них чужие
        затем в Java выкинуть чужие                         → 1 результат, чужой текст уже был в памяти
ХОРОШО: SELECT ... WHERE <права> ORDER BY embedding <=> q LIMIT 5  → 5 своих чанков
```

## Кто кого вызывает

```
web/SearchController.search(@Valid SearchRequestDto, jwt)
 └─ service/RetrievalService.findRelevant(query, topK, entitlements)
     ├─ subject == null → List.of()     (без прав — не ищем вообще)
     ├─ service/AccessFilter.forCaller(entitlements) → Filter.Expression
     │      owner == sub  OR  allowed_groups IN [группы]
     └─ vectorStore.similaritySearch(SearchRequest.builder()
             .query(query).topK(topK).similarityThreshold(0.3)
             .filterExpression(filter).build())
         └─ PgVectorStore → SELECT ... WHERE metadata::jsonb @@ '<jsonpath>' ORDER BY embedding <=> ...
 ← List<SearchHitResponse(documentId, title, snippet, score)>
```

## Что создать

| Файл | Что |
|---|---|
| `service/AccessFilter` | `Filter.Expression forCaller(Entitlements)` — **единственное** место, где строится фильтр |
| `service/RetrievalService` | `List<Document> findRelevant(String query, int topK, Entitlements)` |
| `web/SearchController` | `POST /api/v1/search` |
| `web/dto/SearchRequestDto` | `record(@NotBlank @Size(max = 2000) String query, @Min(1) @Max(20) Integer topK)` |
| `web/dto/SearchHitResponse` | `record(UUID documentId, String title, String snippet, Double score)` |

## Ключевые вызовы

```java
var b = new FilterExpressionBuilder();

FilterExpressionBuilder.Op byOwner = b.eq("owner", e.subject());
Filter.Expression filter = e.groups().isEmpty()
        ? byOwner.build()
        : b.or(byOwner, b.in("allowed_groups", e.groups().toArray())).build();
```

`in(String, Object...)` — передавай массив. Вариант `in(String, List<Object>)` не примет
`List<String>` без приведения типов.

`b.in("allowed_groups", ...)` превращается в jsonpath
`($.allowed_groups == "hr" || $.allowed_groups == "all-staff")`. Для **массива** в метаданных
Postgres в режиме `lax` сравнивает с каждым элементом — то есть «хотя бы одна группа совпала».
Это поведение надо **закрепить тестом** (ниже), а не принимать на веру.

`Document` из результата: `getText()`, `getMetadata()`, `getScore()`.

## Подводные камни

- **Никогда** не собирай фильтр строкой (`"owner == '" + sub + "'"`) — это инъекция в фильтр.
  Только `FilterExpressionBuilder`.
- **Никогда** не бери группы или владельца из запроса — только из `Entitlements`.
- `similarityThreshold` отсекает совсем непохожее; подбери значение на своих данных (0.2–0.5).
- `snippet` — обрезай текст чанка (например, 300 символов), не отдавай огромные куски.

## Как проверить

`service/RetrievalServiceTest` — **самый важный тест проекта**:

1. Подготовь в `vectorStore` чанки двух документов **на похожие темы**, чтобы без фильтра поиск
   точно нашёл оба:
   - alice, группы `[hr]`: «Salary bands for 2026: engineers earn between ...»
   - bob, группы `[engineering]`: «Salary review process for the engineering team ...»
2. `bobCannotRetrieveAlicesChunks` — bob ищет «salary» → только его чанк.
3. `groupMemberRetrievesSharedChunk` — документ alice с `[all-staff]`, bob находит.
4. `callerWithoutGroupsSeesOnlyOwnChunks`.
5. `callerWithoutSubjectGetsNothing`.
6. **Контрольный тест**: тот же поиск **без** фильтра находит чанк alice — доказывает, что тест 2
   зелёный из-за фильтра, а не потому что документы просто не похожи.

## Как сделано в проекте

- `service/AccessFilter.visibleTo(Entitlements)` — единственное место, где строится фильтр;
  возвращает `Optional.empty()` для вызывающего без `sub` (тогда поиск не выполняется вовсе).
- `service/RetrievalService` — `findRelevant(query, [topK], caller)`; порог и `topK` по умолчанию —
  `securerag.retrieval.*` (`config/RetrievalProperties`).
- `web/SearchController` — `POST /api/v1/search`; DTO `SearchQuery` (не `SearchRequest`, чтобы не
  путать со Spring AI) и `SearchHitResponse` (сниппет до 300 символов).
- Проверено тестами, а не принято на веру: `in("allowed_groups", …)` по **массиву** в metadata
  работает («хотя бы одна группа совпала»); кавычки в `sub` или имени группы экранируются —
  jsonpath-инъекция не расширяет доступ; чанк без `owner`/`allowed_groups` не виден никому.
- Если убрать `.filterExpression(filter)`, падают 5 тестов `RetrievalServiceTest` — проверено.

## Готово, когда

Все шесть тестов зелёные. Можешь объяснить, почему фильтр в SQL защищает от prompt injection в
задаче 09.
