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