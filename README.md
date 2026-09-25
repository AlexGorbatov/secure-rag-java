# secure-rag-java

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-2.0-6DB33F?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17_+_pgvector-4169E1?logo=postgresql&logoColor=white)
![Status](https://img.shields.io/badge/status-bootstrap-orange)

An AI assistant for a company's internal documents that answers each user only from the
documents that user is allowed to read.

Users upload documents (PDF, DOCX and other formats) and ask questions about them in plain
language. The service finds the relevant passages, generates an answer with an LLM and returns it
with links to the source documents.

What sets it apart from a regular RAG chatbot is **need-to-know access control**. Every document
has an owner and an access list. When Alice and Bob ask the same question, each gets an answer
built only from their own permitted documents. Bob cannot get anything out of Alice's documents,
however he phrases the question.

> Early stage: the project skeleton is in place and the features above are being built. See the
> [roadmap](#roadmap).

### How access is enforced

- The user is identified by a JWT from Keycloak or Microsoft Entra ID. Access rights come only from
  the token, never from request parameters.
- Every text chunk in the vector index carries the access list of its source document.
- The access filter runs **inside** the pgvector similarity search, not afterwards. Forbidden
  chunks never reach the model, the answer or the citations.
- A document the user may not read returns `404`, so its existence is not revealed either.
почс
## Stack

Java 25 · Spring Boot 4.1 · Spring Security (OAuth2 Resource Server) · Spring AI 2.0 ·
PostgreSQL 17 + pgvector · Spring Data JPA · Flyway · Apache Tika · Testcontainers

## Running

Requires JDK 25 and Docker.

```bash
./mvnw spring-boot:test-run                            # no API keys, local ONNX embeddings
OPENAI_API_KEY=... ./mvnw spring-boot:run              # OpenAI
./mvnw spring-boot:run -Dspring-boot.run.profiles=azure # Azure OpenAI, see .env.example
./mvnw verify                                          # tests
```

| Profile | Chat | Embeddings |
|---|---|---|
| default | OpenAI | OpenAI |
| `azure` | Azure OpenAI | Azure OpenAI |
| `test` | — | ONNX (local) |

Identity provider: Keycloak by default; `--spring.profiles.active=entra` switches to Microsoft Entra ID
(combine as `azure,entra`). Variables for every profile are in [`.env.example`](.env.example).

In production set `OPENAPI_ENABLED=false`. Actuator exposes only `health` (with liveness/readiness
probes, status only) and `info`.

`compose.yaml` also starts Keycloak on http://localhost:8180 with the `securerag` realm
(admin console: `admin` / `admin`, local only). Demo users:

| User | Password | Groups |
|---|---|---|
| `alice` | `alice` | `all-staff`, `hr` |
| `bob` | `bob` | `all-staff`, `engineering` |

```bash
curl -s http://localhost:8180/realms/securerag/protocol/openid-connect/token \
  -d grant_type=password -d client_id=securerag-web -d username=alice -d password=alice
```

## API

OpenAPI document: `/v3/api-docs` · Swagger UI: `/swagger-ui.html`. In Swagger UI, **Authorize**
logs in through Keycloak (authorization code + PKCE, client `securerag-web`). Both pages are public
and can be switched off with `OPENAPI_ENABLED=false`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/me` | Caller's identity, groups and roles resolved from the token |
| `POST` | `/api/v1/documents` | Upload a PDF, DOCX, Markdown or text file; optionally share it with your groups |
| `GET` | `/api/v1/documents` | Your documents and those shared with your groups |
| `GET` | `/api/v1/documents/{id}` | One document; `404` if it is not visible to you |
| `DELETE` | `/api/v1/documents/{id}` | Delete your own document and all its chunks |
| `POST` | `/api/v1/search` | Semantic search over the documents you may read |
| `POST` | `/api/v1/chat` | Answer a question from the documents you may read, with citations |

## Roadmap

- [x] Project bootstrap, profiles, Testcontainers
- [x] Keycloak realm with demo users (`alice`, `bob`)
- [x] JWT resource server, entitlements from token claims, OpenAPI / Swagger UI
- [x] Documents schema and ingestion
- [x] ACL-filtered retrieval and chat with citations
- [x] Access-control tests: Bob cannot see Alice's documents (search, chat, get, delete)
- [x] Operations: actuator, logging, timeouts, Azure OpenAI and Entra ID
