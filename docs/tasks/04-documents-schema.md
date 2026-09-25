# 04. Таблица `documents`: миграция, entity, repository

## Зачем

`documents` — источник правды о том, **кому принадлежит документ и с кем он расшарен**. Векторное
хранилище (задача 05) будет хранить копию этих прав в метаданных каждого чанка, но решает всегда
эта таблица.

Здесь ты поймёшь, как Flyway создаёт схему, а Hibernate только **проверяет**, что entity ей
соответствует (`ddl-auto=validate`).

## Модель данных

Права хранятся так: владелец (`owner_sub` = `sub` из токена) + список групп, которым документ
открыт. Группы — в отдельной таблице, это обычная JPA-коллекция (`@ElementCollection`).

## Что создать

### 1. `src/main/resources/db/migration/V1__create_documents.sql`

```sql
CREATE TABLE documents (
    id            UUID         PRIMARY KEY,
    owner_sub     TEXT         NOT NULL,
    title         TEXT         NOT NULL,
    content_type  TEXT         NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    status        TEXT         NOT NULL,          -- PROCESSING | READY | FAILED
    chunk_count   INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT documents_status_check CHECK (status IN ('PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX documents_owner_sub_idx ON documents (owner_sub);

CREATE TABLE document_groups (
    document_id  UUID  NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    group_name   TEXT  NOT NULL,
    CONSTRAINT document_groups_pk PRIMARY KEY (document_id, group_name)
);

CREATE INDEX document_groups_group_name_idx ON document_groups (group_name);
```

### 2. `model/DocumentStatus` — `enum { PROCESSING, READY, FAILED }`

### 3. `model/DocumentEntity`

Называй именно `DocumentEntity`: в Spring AI есть свой `org.springframework.ai.document.Document`,
и одинаковые имена будут путать импорты.

```java
@Entity
@Table(name = "documents")
public class DocumentEntity {
    @Id UUID id;
    @Column(name = "owner_sub") String ownerSub;
    String title;
    @Column(name = "content_type") String contentType;
    @Column(name = "size_bytes") long sizeBytes;
    @Enumerated(EnumType.STRING) DocumentStatus status;
    @Column(name = "chunk_count") int chunkCount;
    @Column(name = "created_at") Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "document_groups", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "group_name")
    Set<String> allowedGroups = new HashSet<>();

    protected DocumentEntity() {}                    // для Hibernate
    public DocumentEntity(UUID id, String ownerSub, String title, String contentType,
                          long sizeBytes, Set<String> allowedGroups, Instant createdAt) { ... status = PROCESSING; }

    public void markReady(int chunkCount) { ... }
    public void markFailed() { ... }
    // геттеры
}
```

Проверки видимости (`isVisibleTo`) в entity **нет**: кто что видит, решают только запросы
репозитория ниже. Две копии одного правила доступа рано или поздно разойдутся.

`id` генерируй сам (`UUID.randomUUID()`) в сервисе, а не `@GeneratedValue` — он понадобится ещё
до сохранения, чтобы положить его в метаданные чанков.

### 4. `repository/DocumentRepository`

Всё правило видимости — в репозитории, в одном месте. Сервисы вызывают только два метода с
`Entitlements`; JPQL-запросы под ними — строительные блоки.

```java
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    default List<DocumentEntity> findAllVisibleTo(Entitlements caller) {
        if (caller.subject() == null) return List.of();            // без sub — ничего
        return caller.groups().isEmpty()                             // пустой IN () — не отправляем
                ? findByOwnerSubOrderByCreatedAtDesc(caller.subject())
                : findOwnedOrSharedWith(caller.subject(), caller.groups());
    }

    default Optional<DocumentEntity> findVisibleById(UUID id, Entitlements caller) { /* так же */ }

    List<DocumentEntity> findByOwnerSubOrderByCreatedAtDesc(String ownerSub);
    Optional<DocumentEntity> findByIdAndOwnerSub(UUID id, String ownerSub);   // пригодится для DELETE

    @Query("""
            select distinct d from DocumentEntity d left join d.allowedGroups g
            where d.ownerSub = :sub or g in :groups
            order by d.createdAt desc
            """)
    List<DocumentEntity> findOwnedOrSharedWith(String sub, Collection<String> groups);

    @Query("""
            select distinct d from DocumentEntity d left join d.allowedGroups g
            where d.id = :id and (d.ownerSub = :sub or g in :groups)
            """)
    Optional<DocumentEntity> findByIdOwnedOrSharedWith(UUID id, String sub, Collection<String> groups);
}
```

`@Repository` на интерфейсе Spring Data не нужен — реализацию Spring создаёт сам.

## Подводные камни

- **Пустой список групп.** `g in :groups` с пустой коллекцией разные версии Hibernate рендерят
  по-разному. Поэтому `default`-методы репозитория при пустых группах идут в запрос только по
  владельцу.
- **Нет `sub` — нет доступа.** Иначе вызывающий без `sub`, но с группой, увидел бы групповые
  документы.
- **`ddl-auto=validate`.** Если тип колонки в SQL и в entity не совпадёт, приложение не стартует —
  это правильно. Чини entity или пиши новую миграцию, **никогда** не меняй на `update`.
- **Не редактируй применённую миграцию.** Flyway хранит checksum. Ошибся — пиши `V2__...`. Локально
  можно пересоздать базу (`docker compose down` и удалить volume).
- **Параметры в `@Query` без `@Param`** работают, только если компилятор сохраняет имена параметров
  (Spring Boot parent включает `-parameters`). Если увидишь ошибку про имена — добавь `@Param`.

## Как проверить

`repository/DocumentRepositoryTest`:

```java
@DataJpaTest                                             // org.springframework.boot.data.jpa.test.autoconfigure
@AutoConfigureTestDatabase(replace = Replace.NONE)       // org.springframework.boot.jdbc.test.autoconfigure
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DocumentRepositoryTest { @Autowired DocumentRepository repository; ... }
```

Тесты (реализованы в `DocumentRepositoryTest`, 10 шт.):
- `storesAndReadsBackEveryColumn` — маппинг всех колонок, включая группы и время.
- `ownerSeesOwnPrivateDocument`, `groupMemberSeesSharedDocument`.
- `bobDoesNotSeeAlicesHrDocument` — ни в списке, ни по id.
- `callerWithoutGroupsSeesOnlyOwnDocuments`, `callerWithoutSubjectSeesNothingEvenInSharedGroup`.
- `documentSharedWithSeveralOfCallersGroupsAppearsOnce` — `distinct` работает.
- `visibleDocumentsAreNewestFirst`, `unknownIdIsIndistinguishableFromForeignDocument`.
- `deletingDocumentRowDeletesItsGroupsInTheDatabase` — `ON DELETE CASCADE` в самой схеме.

## Готово, когда

- Приложение стартует (значит, entity совпала со схемой).
- Все тесты зелёные, особенно `bobDoesNotSeeAlicesHrDocument`.
