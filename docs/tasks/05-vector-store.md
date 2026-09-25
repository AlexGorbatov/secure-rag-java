# 05. Векторное хранилище pgvector

## Зачем

RAG ищет не по словам, а по смыслу: текст превращается в вектор (эмбеддинг), и ищутся ближайшие
векторы. Здесь ты создашь таблицу для векторов, выберешь размерность и научишься класть и искать
документы через Spring AI `VectorStore` — пока без прав доступа.

## Решение: размерность эмбеддингов

| Модель | Размерность |
|---|---|
| ONNX `all-MiniLM-L6-v2` (профиль `test`) | **384** |
| OpenAI `text-embedding-3-small` по умолчанию | 1536 |

Колонка `vector(N)` имеет **одну** размерность. Решение: просить у OpenAI/Azure тоже 384 —
модели `text-embedding-3-*` это умеют. Тогда одна миграция работает во всех профилях.

## Что создать

### 1. `db/migration/V2__create_vector_store.sql`

Структура таблицы должна совпадать с тем, что ожидает `PgVectorStore` из Spring AI:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content    TEXT,
    metadata   JSON,
    embedding  VECTOR(384)
);

CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE vector_store IS 'Document chunks with embeddings; metadata carries document_id, owner and allowed_groups';
```

### 2. `application.properties`

```properties
spring.ai.vectorstore.pgvector.dimensions=384
spring.ai.vectorstore.pgvector.initialize-schema=false     # уже есть — схемой владеет Flyway
spring.ai.vectorstore.pgvector.schema-validation=true      # при старте сверить размерность колонки
spring.ai.openai.embedding.model=text-embedding-3-small
spring.ai.openai.embedding.dimensions=384
```

Для Azure (`application-azure.properties`) — деплоймент `text-embedding-3-small` и те же
`dimensions=384`.

### 3. Попробуй `VectorStore` руками — тест `service/VectorStoreSmokeTest`

```java
@SpringBootTest @ActiveProfiles("test") @Import(TestcontainersConfiguration.class)
class VectorStoreSmokeTest {
    @Autowired VectorStore vectorStore;

    @Test
    void findsSemanticallyClosestChunk() {
        vectorStore.add(List.of(
            new Document("Vacation policy: employees get 25 days of paid leave.", Map.of("document_id", "a")),
            new Document("The build pipeline runs unit tests on every commit.", Map.of("document_id", "b"))));

        List<Document> hits = vectorStore.similaritySearch(
            SearchRequest.builder().query("how many holidays do I have?").topK(1).build());

        assertThat(hits.getFirst().getMetadata()).containsEntry("document_id", "a");
    }
}
```

`Document` здесь — `org.springframework.ai.document.Document`.

## Что происходит внутри `vectorStore.add(...)`

```
PgVectorStore.add(docs)
 ├─ EmbeddingModel.embed(texts)          ONNX локально / HTTP в OpenAI
 └─ INSERT INTO vector_store (id, content, metadata, embedding) ... ON CONFLICT (id) DO UPDATE
```

и внутри `similaritySearch(...)`:

```
 ├─ EmbeddingModel.embed(query)
 └─ SELECT ... FROM vector_store
    WHERE <фильтр по metadata, если задан>
    ORDER BY embedding <=> :queryVector       -- косинусное расстояние
    LIMIT :topK
```

## Подводные камни

- `expected 384 dimensions, not 1536` — забыл `spring.ai.openai.embedding.dimensions`.
- `Actual vector dimensions is 384, required vector dimensions is 1536` при старте — это сработала
  `schema-validation`: настройка `pgvector.dimensions` не совпадает с колонкой. Так и задумано —
  ошибка на старте лучше, чем на первой загрузке документа.
- `type "vector" does not exist` — не тот образ Postgres или миграция не создала расширение.
- Первый запуск теста скачивает ONNX-модель (~90 МБ) — нужен интернет.
- Не включай `initialize-schema=true`: две системы, создающие одну таблицу, разойдутся.

## Готово, когда

- `VectorStoreSmokeTest` зелёный.
- Ты можешь объяснить, что такое эмбеддинг, почему размерность должна совпадать и что делает
  оператор `<=>`.
