-- Every ACL-filtered similarity search runs `metadata::jsonb @@ '<jsonpath>'::jsonpath` (verified by
-- decompiling PgVectorFilterExpressionConverter in spring-ai-pgvector-store:2.0.1). The expression
-- below is written to match that cast exactly, which is what makes the index eligible for the planner
-- to use. GIN with jsonb_path_ops supports the @@ / @? operators since PostgreSQL 12.
CREATE INDEX vector_store_metadata_gin_idx ON vector_store USING gin ((metadata::jsonb) jsonb_path_ops);

COMMENT ON INDEX vector_store_metadata_gin_idx IS
    'Supports the owner/allowed_groups jsonpath predicate AccessFilter builds for every search.';
