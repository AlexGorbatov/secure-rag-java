# Next.js frontend — design

Date: 2026-09-25
Status: approved, pending implementation plan

## Purpose

`secure-rag-java` currently has no UI: it is exercised through `curl`/Swagger UI only. This adds a
single testable page that lets a person log in as a demo user (`alice`/`bob`), upload a document,
and ask a question — so the core value of the project (need-to-know access control: Alice's
documents don't exist for Bob) is something you can *see*, not just read about in a test.

This is a demo/verification surface, not a production admin console. Scope is deliberately small:
login, ask a question, upload/list/delete documents. Nothing else (no search-only UI, no settings,
no multi-page navigation). All UI copy is in English, regardless of the language used to design it.

## Packaging: one process, one port

The frontend is a separate Next.js project (`frontend/`) built with `output: 'export'` (static
HTML/CSS/JS, no Node server at runtime). The build output is copied into
`src/main/resources/static/`, so the existing Spring Boot app serves both the page and the API on
the same port (`:8080`) as one deployable — no second process, no reverse proxy, no CORS between
frontend and backend.

```
frontend/            Next.js App Router project (TypeScript, Tailwind), one route: app/page.tsx
  npm run build   →   next build (static export to out/) + copy out/* into ../src/main/resources/static/
```

This is a manual two-step local workflow (`npm run build` in `frontend/`, then
`./mvnw spring-boot:test-run` from the repo root) — no new Maven plugin, no automatic Node install
during `./mvnw verify`. Iterating on the UI means rebuilding the static export and restarting the
Spring Boot app; that's an accepted trade-off for a small, single-page demo surface.

**Required backend change:** `SecurityConfig`'s public allow-list currently covers only
`/actuator/health`, `/v3/api-docs`, `/swagger-ui`. The static export (`index.html`, `_next/**`,
`favicon.ico`) must be reachable *before* login, so it needs to be added to that allow-list. This is
called out explicitly per `.claude/rules/security.md` ("adding to that list is a security
decision"): the assets served are a public JS/CSS bundle with no document data and no secrets (the
Keycloak issuer URL and client id baked into it are already public in `.env.example` and the
README). All `/api/**` endpoints keep requiring a valid JWT — nothing about that changes.

## Auth

The backend is a pure OAuth2 resource server with no login endpoint of its own. The page logs in by
calling Keycloak's token endpoint directly with the Resource Owner Password Credentials grant —
the same flow as the `curl` example already in the README:

```
POST {NEXT_PUBLIC_KEYCLOAK_ISSUER}/protocol/openid-connect/token
grant_type=password&client_id={NEXT_PUBLIC_KEYCLOAK_CLIENT_ID}&username=...&password=...
```

- `NEXT_PUBLIC_KEYCLOAK_ISSUER` (default `http://localhost:8180/realms/securerag`) and
  `NEXT_PUBLIC_KEYCLOAK_CLIENT_ID` (default `securerag-web`) are build-time env vars, documented in
  `frontend/.env.example`. The `securerag-web` Keycloak client already has `directAccessGrantsEnabled`
  and `webOrigins` including `http://localhost:8080`, so no Keycloak realm change is needed.
- On success: the JWT is kept in `sessionStorage` (cleared on tab close or explicit logout — this is
  a demo tool, not a security-hardened SPA, so this is an accepted, documented trade-off) and
  `GET /api/v1/me` is called immediately to populate the header (username, groups) and confirm the
  token actually works against the API.
- Wrong credentials → inline error on the login form.
- Any later API call that comes back `401` (expired token) clears the session and returns to the
  login card with "Session expired — please log in again."

## Page structure (one route, three states)

**1. Logged out** — centered login card: username, password, submit. Nothing else rendered.

**2. Logged in — dashboard** (layout: chat hero on top, documents panel below; visual style:
"Midnight Glass" — dark radial background, frosted-glass panels with backdrop blur, violet→cyan
gradient accents, Inter font, rounded-2xl corners):

- **Header**: brand mark, current user chip (`username · groups`), logout button.
- **Ask** (hero, full width): question input + "Ask" button → `POST /api/v1/chat`
  `{question}` → `{answer, citations[]}`.
  - `citations.length > 0` → normal answer card, one citation pill per document (`title`).
  - `citations.length === 0` → same answer text (the backend's fixed "I could not find information
    about this in the documents available to you.") rendered in a visually muted "not grounded"
    treatment instead of a normal answer card. This is how the Alice/Bob contrast from the product
    brief shows up: there is no `grounded` field in the API, so it's derived client-side from an
    empty citation list.
  - Errors: `400` (blank/too-long question) → inline validation message. `502` (answer generation
    failed) → "The assistant could not answer right now. Please try again later." (mirrors the
    backend's own `ProblemDetail.detail`).
- **Documents** (below, own panel): list from `GET /api/v1/documents` (title, `allowedGroups` as
  pills, status, chunk count, newest first); upload control (file input + checkboxes for the
  caller's own groups, from `GET /api/v1/me`) → `POST /api/v1/documents` multipart; delete icon per
  row → `DELETE /api/v1/documents/{id}`.
  - The list has no owner flag, so every row gets a delete icon; a `404` on delete (not found or not
    the owner — the API doesn't distinguish) shows a toast: "Could not delete — not found, or you
    are not the owner."
  - Upload errors map 1:1 to the backend's `ProblemDetail.detail`: `403` group not allowed, `413`
    file too large, `415` unsupported type, `422` unreadable document.
  - Empty state: "No documents yet — upload one to get started."

There is no client-side routing: state is `loggedOut | loggedIn`, held in React state, hydrated
once from `sessionStorage` on mount.

## Testing / verification

- No frontend unit-test framework is added — `next build` (TypeScript + ESLint via Next's build
  step) is the correctness gate. This is a single demo page; a test harness for it is not
  proportionate (YAGNI).
- Functional proof is a manual walkthrough in the browser tool after building: log in as `alice`,
  upload a document shared with `hr`, ask the salary question, see the answer with a citation; log
  out, log in as `bob`, ask the same question, see the muted "not found" state. Screenshot both.
  Also exercise one upload-error case (wrong file type or a group the user isn't in) and one delete.
- The backend is not modified except the `SecurityConfig` allow-list addition above, so
  `./mvnw verify` (152 tests today) stays the regression gate for everything server-side, including
  that the new public paths don't leak into `/api/**` — worth a small addition to
  `ActuatorExposureTest`-adjacent security tests or a new assertion that `/api/v1/documents` (GET)
  still returns `401` with no token, so the widened allow-list didn't accidentally cover more than
  intended.

## Out of scope

- No standalone search UI (`/api/v1/search`) — chat only, per product brief.
- No document viewer/preview, no multi-turn conversation history, no settings page.
- No automated Maven build of the frontend (`frontend-maven-plugin` or similar) — manual
  `npm run build` step, revisit if this becomes a CI requirement later.
- No production hardening of token storage (httpOnly cookie, refresh flow) — `sessionStorage` is
  accepted for a local demo tool.
