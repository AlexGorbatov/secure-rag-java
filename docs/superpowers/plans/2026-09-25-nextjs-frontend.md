# Next.js Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single-page Next.js UI (login, ask a question, upload/list/delete documents) that is
statically exported and served by the existing Spring Boot app on `:8080`, so the need-to-know
demo (Alice sees her answer with a citation, Bob gets "not found") can be driven from a browser.

**Architecture:** `frontend/` is a standalone Next.js (App Router, TypeScript, Tailwind v4) project.
`npm run build` runs `next build` with `output: 'export'` and copies the static bundle into
`src/main/resources/static/`, so the running Spring Boot app serves both the page and the
`/api/v1/**` endpoints on one origin — no CORS, no second process. The page authenticates by
posting directly to Keycloak's token endpoint (Resource Owner Password Credentials grant, same as
the README's existing `curl` example) and calls the backend with the resulting JWT.

**Tech Stack:** Next.js 16 (App Router, static export), React 19, TypeScript, Tailwind CSS v4.
Backend: one `SecurityConfig` change (Spring Security) plus one new MockMvc test.

**Governance note:** this repo's `CLAUDE.md` says *"Never commit automatically."* Every "Commit"
step below shows the exact command as this plan's format requires, but whoever executes this plan
must get the user's go-ahead before running each `git commit` (batching several tasks into one
confirmation is fine if the user prefers).

Full design: [`docs/superpowers/specs/2026-09-25-nextjs-frontend-design.md`](../specs/2026-09-25-nextjs-frontend-design.md)

---

### Task 1: Backend — let the static frontend load before login

The static export (`index.html`, `_next/**`) must be reachable without a token; `/api/**` must not
change. This is a security-relevant change to the public allow-list (see
`.claude/rules/security.md`), so it gets its own regression test proving the widening is exactly
what was intended and nothing more.

**Files:**
- Modify: `src/main/java/com/altronixsoft/securerag/config/SecurityConfig.java`
- Test: `src/test/java/com/altronixsoft/securerag/web/StaticAssetsSecurityTest.java` (new)

- [ ] **Step 1: Write the failing test**

```java
package com.altronixsoft.securerag.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The static frontend bundle must be reachable before login; nothing under /api/** may loosen.
 * No frontend is built in the test classpath, so a permitted path resolves to 404 (no file), not
 * 401 (blocked by the security filter) — that distinction is exactly what this test checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StaticAssetsSecurityTest {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/favicon.ico", "/_next/static/chunk.js"})
    void staticAssetPathsAreReachableWithoutAToken(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void apiStillRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }

}
```

- [ ] **Step 2: Run the test, confirm it fails on the static paths**

Run: `./mvnw test -Dtest=StaticAssetsSecurityTest`
Expected: `staticAssetPathsAreReachableWithoutAToken` FAILS (actual status 401, not 404) for all four
paths; `apiStillRequiresAToken` already PASSES (no change needed there — it's the guard rail).

- [ ] **Step 3: Widen the allow-list**

In `src/main/java/com/altronixsoft/securerag/config/SecurityConfig.java`, change:

```java
    static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health",       // UP/DOWN only: details and components are never shown
            "/actuator/health/**",    // Kubernetes liveness and readiness probes, same content
            "/v3/api-docs/**",        // OpenAPI document; disable with OPENAPI_ENABLED=false
            "/swagger-ui.html",       // Swagger UI; same switch
            "/swagger-ui/**"
    };
```

to:

```java
    static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health",       // UP/DOWN only: details and components are never shown
            "/actuator/health/**",    // Kubernetes liveness and readiness probes, same content
            "/v3/api-docs/**",        // OpenAPI document; disable with OPENAPI_ENABLED=false
            "/swagger-ui.html",       // Swagger UI; same switch
            "/swagger-ui/**",
            "/",                      // Static frontend bundle (index.html); public JS/CSS, no document data
            "/index.html",
            "/favicon.ico",
            "/_next/**"               // Next.js static export chunks
    };
```

- [ ] **Step 4: Run the test again, confirm it passes**

Run: `./mvnw test -Dtest=StaticAssetsSecurityTest`
Expected: PASS — both test methods green.

- [ ] **Step 5: Run the full suite**

Run: `./mvnw verify`
Expected: BUILD SUCCESS, all tests green (152 existing + 3 new = 155).

- [ ] **Step 6: Commit** (confirm with the user first — see Governance note above)

```bash
git add src/main/java/com/altronixsoft/securerag/config/SecurityConfig.java \
        src/test/java/com/altronixsoft/securerag/web/StaticAssetsSecurityTest.java
git commit -m "Allow the static frontend bundle to load without a token"
```

---

### Task 2: Frontend scaffold and build pipeline

Get an empty-but-real page through the whole pipeline (Next static export → copied into
`src/main/resources/static/` → served by Spring Boot on `:8080`) before adding any feature, so
every later task is verifying UI, not plumbing.

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/next.config.ts`
- Create: `frontend/postcss.config.mjs`
- Create: `frontend/scripts/copy-to-backend.mjs`
- Create: `frontend/.env.example`
- Create: `frontend/app/layout.tsx`
- Create: `frontend/app/globals.css`
- Create: `frontend/app/page.tsx`
- Create: `src/main/resources/static/.gitkeep`
- Modify: `.gitignore`

- [ ] **Step 1: Create `frontend/package.json`**

```json
{
  "name": "securerag-frontend",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build && node scripts/copy-to-backend.mjs",
    "lint": "next lint"
  },
  "dependencies": {
    "next": "16.3.6",
    "react": "^19",
    "react-dom": "^19"
  },
  "devDependencies": {
    "@tailwindcss/postcss": "4.3.3",
    "@types/node": "^22",
    "@types/react": "^19",
    "@types/react-dom": "^19",
    "tailwindcss": "4.3.3",
    "typescript": "^5"
  }
}
```

- [ ] **Step 2: Create `frontend/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": false,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "plugins": [{ "name": "next" }],
    "paths": { "@/*": ["./*"] }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules"]
}
```

- [ ] **Step 3: Create `frontend/next.config.ts`**

```ts
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "export",
};

export default nextConfig;
```

- [ ] **Step 4: Create `frontend/postcss.config.mjs`**

```js
const config = {
  plugins: {
    "@tailwindcss/postcss": {},
  },
};

export default config;
```

- [ ] **Step 5: Create `frontend/scripts/copy-to-backend.mjs`**

```js
import { cp, rm } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";

const projectRoot = path.resolve(import.meta.dirname, "..");
const sourceDir = path.join(projectRoot, "out");
const targetDir = path.resolve(projectRoot, "..", "src", "main", "resources", "static");

if (!existsSync(sourceDir)) {
  throw new Error(`Expected a static export at ${sourceDir}. Run "next build" first.`);
}

await rm(targetDir, { recursive: true, force: true });
await cp(sourceDir, targetDir, { recursive: true });

console.log(`Copied ${sourceDir} -> ${targetDir}`);
```

- [ ] **Step 6: Create `frontend/.env.example`**

```
# Copy to .env.local for local overrides. Baked in at build time (npm run build) — the static
# export has no server at runtime to read these from later.
NEXT_PUBLIC_KEYCLOAK_ISSUER=http://localhost:8180/realms/securerag
NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=securerag-web
```

- [ ] **Step 7: Create `frontend/app/globals.css`** (Midnight Glass theme tokens)

```css
@import "tailwindcss";

@theme {
  --color-bg: #0a0a0f;
  --color-bg-violet: #1b1235;
  --color-panel-border: rgba(255, 255, 255, 0.09);
  --color-text: #e7e7ee;
  --color-text-muted: #9a9aac;
  --color-accent-from: #8b5cf6;
  --color-accent-to: #22d3ee;
}

body {
  background:
    radial-gradient(circle at 15% 0%, var(--color-bg-violet) 0%, var(--color-bg) 55%),
    var(--color-bg);
  color: var(--color-text);
  font-family: var(--font-inter), ui-sans-serif, system-ui, sans-serif;
  min-height: 100vh;
}

.glass-panel {
  background: rgba(255, 255, 255, 0.045);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid var(--color-panel-border);
  border-radius: 1rem;
}

.gradient-text {
  background: linear-gradient(90deg, var(--color-accent-from), var(--color-accent-to));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.gradient-button {
  background: linear-gradient(90deg, var(--color-accent-from), var(--color-accent-to));
  color: var(--color-bg);
  box-shadow: 0 0 28px -4px rgba(139, 92, 246, 0.65);
}
```

- [ ] **Step 8: Create `frontend/app/layout.tsx`**

`next/font` self-hosts Google Fonts at build time — no runtime request to Google, works fine with
`output: 'export'`, and is what actually makes the "Inter" used throughout the mockups render
instead of silently falling back to the OS default.

```tsx
import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });

export const metadata: Metadata = {
  title: "secure-rag",
  description: "Ask questions about the documents you have access to.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={inter.variable}>
      <body>{children}</body>
    </html>
  );
}
```

- [ ] **Step 9: Create `frontend/app/page.tsx`** (walking skeleton — replaced task by task below)

```tsx
export default function Home() {
  return (
    <main className="flex min-h-screen items-center justify-center">
      <h1 className="text-lg font-bold">
        secure<span className="gradient-text">rag</span>
      </h1>
    </main>
  );
}
```

- [ ] **Step 10: Create `src/main/resources/static/.gitkeep`**

Empty file — keeps the directory tracked while its build output stays ignored (next step).

- [ ] **Step 11: Update `.gitignore`**

Append:

```
### Frontend (Next.js) ###
frontend/node_modules/
frontend/.next/
frontend/out/
src/main/resources/static/*
!src/main/resources/static/.gitkeep
frontend/.env.local
```

- [ ] **Step 12: Install and build**

Run:
```bash
cd frontend && npm install && npm run build
```
Expected: `next build` succeeds (static export to `frontend/out/`), then the copy script prints
`Copied .../frontend/out -> .../src/main/resources/static`. Confirm:
```bash
ls ../src/main/resources/static/index.html
```
Expected: file exists.

- [ ] **Step 13: Serve it and check in the browser**

From the repo root:
```bash
./mvnw spring-boot:test-run
```
In another terminal:
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
```
Expected: `200`. Stop the app (Ctrl+C) once confirmed.

- [ ] **Step 14: Commit** (confirm with the user first)

```bash
git add frontend .gitignore src/main/resources/static/.gitkeep
git commit -m "Scaffold Next.js frontend with static export build pipeline"
```

---

### Task 3: API client and shared types

**Files:**
- Create: `frontend/lib/types.ts`
- Create: `frontend/lib/session.ts`
- Create: `frontend/lib/api.ts`

- [ ] **Step 1: Create `frontend/lib/types.ts`**

```ts
export interface CurrentUser {
  subject: string;
  username: string;
  groups: string[];
  roles: string[];
}

export interface DocumentSummary {
  id: string;
  title: string;
  contentType: string;
  sizeBytes: number;
  status: "PROCESSING" | "READY" | "FAILED";
  chunkCount: number;
  allowedGroups: string[];
  createdAt: string;
}

export interface Citation {
  documentId: string;
  title: string;
}

export interface AnswerResponse {
  answer: string;
  citations: Citation[];
}
```

- [ ] **Step 2: Create `frontend/lib/session.ts`**

```ts
const TOKEN_KEY = "securerag.token";

export function saveToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function loadToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}
```

- [ ] **Step 3: Create `frontend/lib/api.ts`**

```ts
import type { AnswerResponse, CurrentUser, DocumentSummary } from "./types";

const KEYCLOAK_ISSUER =
  process.env.NEXT_PUBLIC_KEYCLOAK_ISSUER ?? "http://localhost:8180/realms/securerag";
const KEYCLOAK_CLIENT_ID = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? "securerag-web";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

interface ProblemDetail {
  detail?: string;
}

export async function login(username: string, password: string): Promise<string> {
  const response = await fetch(`${KEYCLOAK_ISSUER}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "password",
      client_id: KEYCLOAK_CLIENT_ID,
      username,
      password,
    }),
  });
  if (!response.ok) {
    throw new ApiError(response.status, "Invalid username or password.");
  }
  const body = (await response.json()) as { access_token: string };
  return body.access_token;
}

async function authorizedFetch(token: string, path: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(path, {
    ...init,
    headers: { ...(init.headers ?? {}), Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as ProblemDetail | null;
    throw new ApiError(response.status, problem?.detail ?? "Request failed.");
  }
  return response;
}

export async function fetchCurrentUser(token: string): Promise<CurrentUser> {
  const response = await authorizedFetch(token, "/api/v1/me");
  return response.json();
}

export async function fetchDocuments(token: string): Promise<DocumentSummary[]> {
  const response = await authorizedFetch(token, "/api/v1/documents");
  return response.json();
}

export async function uploadDocument(
  token: string,
  file: File,
  groups: string[],
): Promise<DocumentSummary> {
  const form = new FormData();
  form.append("file", file);
  groups.forEach((group) => form.append("groups", group));
  const response = await authorizedFetch(token, "/api/v1/documents", {
    method: "POST",
    body: form,
  });
  return response.json();
}

export async function deleteDocument(token: string, id: string): Promise<void> {
  await authorizedFetch(token, `/api/v1/documents/${id}`, { method: "DELETE" });
}

export async function askQuestion(token: string, question: string): Promise<AnswerResponse> {
  const response = await authorizedFetch(token, "/api/v1/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question }),
  });
  return response.json();
}
```

- [ ] **Step 4: Type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors (this module isn't wired into any page yet, but must compile standalone).

- [ ] **Step 5: Commit** (confirm with the user first)

```bash
git add frontend/lib
git commit -m "Add typed API client for the frontend"
```

---

### Task 4: Auth flow — login, session, logout

**Files:**
- Create: `frontend/components/LoginCard.tsx`
- Create: `frontend/components/Header.tsx`
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Create `frontend/components/LoginCard.tsx`**

```tsx
"use client";

import { FormEvent, useState } from "react";
import { login } from "@/lib/api";

interface LoginCardProps {
  onLoggedIn: (token: string) => Promise<void>;
}

export function LoginCard({ onLoggedIn }: LoginCardProps) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const token = await login(username, password);
      try {
        await onLoggedIn(token);
      } catch {
        setError("Signed in, but could not load your profile. Please try again.");
      }
    } catch {
      setError("Invalid username or password.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={handleSubmit} className="glass-panel w-full max-w-sm p-8">
        <h1 className="mb-1 text-lg font-bold">
          secure<span className="gradient-text">rag</span>
        </h1>
        <p className="mb-6 text-sm text-[var(--color-text-muted)]">
          Sign in to ask about the documents you can read.
        </p>
        <label className="mb-3 block text-xs uppercase tracking-wide text-[var(--color-text-muted)]">
          Username
          <input
            className="mt-1 w-full rounded-xl border border-[var(--color-panel-border)] bg-white/5 px-3 py-2 text-sm text-[var(--color-text)] outline-none focus:border-[var(--color-accent-to)]"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label className="mb-4 block text-xs uppercase tracking-wide text-[var(--color-text-muted)]">
          Password
          <input
            type="password"
            className="mt-1 w-full rounded-xl border border-[var(--color-panel-border)] bg-white/5 px-3 py-2 text-sm text-[var(--color-text)] outline-none focus:border-[var(--color-accent-to)]"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && <p className="mb-4 text-sm text-red-400">{error}</p>}
        <button
          type="submit"
          disabled={submitting}
          className="gradient-button w-full rounded-xl py-2 text-sm font-semibold disabled:opacity-50"
        >
          {submitting ? "Signing in..." : "Sign in"}
        </button>
      </form>
    </div>
  );
}
```

- [ ] **Step 2: Create `frontend/components/Header.tsx`**

```tsx
"use client";

import type { CurrentUser } from "@/lib/types";

interface HeaderProps {
  user: CurrentUser;
  onLogout: () => void;
}

export function Header({ user, onLogout }: HeaderProps) {
  return (
    <header className="mb-6 flex items-center justify-between border-b border-[var(--color-panel-border)] pb-4">
      <div className="text-sm font-bold">
        secure<span className="gradient-text">rag</span>
      </div>
      <div className="flex items-center gap-3 text-xs text-[var(--color-text-muted)]">
        <span>
          {user.username} · {user.groups.length > 0 ? user.groups.join(", ") : "no groups"}
        </span>
        <button
          onClick={onLogout}
          className="rounded-lg border border-[var(--color-panel-border)] px-3 py-1 text-[var(--color-text)] hover:border-[var(--color-accent-to)]"
        >
          Sign out
        </button>
      </div>
    </header>
  );
}
```

- [ ] **Step 3: Replace `frontend/app/page.tsx`**

```tsx
"use client";

import { useEffect, useState } from "react";
import { Header } from "@/components/Header";
import { LoginCard } from "@/components/LoginCard";
import { fetchCurrentUser } from "@/lib/api";
import { clearToken, loadToken, saveToken } from "@/lib/session";
import type { CurrentUser } from "@/lib/types";

export default function Home() {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    const stored = loadToken();
    if (!stored) {
      setCheckingSession(false);
      return;
    }
    fetchCurrentUser(stored)
      .then((me) => {
        setToken(stored);
        setUser(me);
      })
      .catch(() => clearToken())
      .finally(() => setCheckingSession(false));
  }, []);

  async function handleLoggedIn(newToken: string) {
    const me = await fetchCurrentUser(newToken);
    saveToken(newToken);
    setToken(newToken);
    setUser(me);
    setSessionError(null);
  }

  function handleLogout() {
    clearToken();
    setToken(null);
    setUser(null);
  }

  function handleUnauthorized() {
    clearToken();
    setToken(null);
    setUser(null);
    setSessionError("Session expired — please log in again.");
  }

  if (checkingSession) {
    return null;
  }

  if (!token || !user) {
    return (
      <div>
        {sessionError && <p className="pt-6 text-center text-sm text-red-400">{sessionError}</p>}
        <LoginCard onLoggedIn={handleLoggedIn} />
      </div>
    );
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-8">
      <Header user={user} onLogout={handleLogout} />
      <p className="text-sm text-[var(--color-text-muted)]">
        Signed in. Ask panel and documents panel land in the next tasks.
      </p>
      {/* handleUnauthorized is wired into AskPanel/DocumentsPanel in Tasks 5 and 6 */}
    </main>
  );
}
```

- [ ] **Step 4: Build and manually verify**

```bash
cd frontend && npm run build && cd .. && ./mvnw spring-boot:test-run
```
Open `http://localhost:8080` in the browser tool. Expected: Midnight Glass login card. Log in with
`alice` / `alice` (from the README's demo users table). Expected: header shows
`alice · all-staff, hr` and a "Sign out" button; clicking it returns to the login card. Stop the app.

- [ ] **Step 5: Commit** (confirm with the user first)

```bash
git add frontend/components frontend/app/page.tsx
git commit -m "Add login flow and session handling to the frontend"
```

---

### Task 5: Ask panel

**Files:**
- Create: `frontend/components/AskPanel.tsx`
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Create `frontend/components/AskPanel.tsx`**

```tsx
"use client";

import { FormEvent, useState } from "react";
import { ApiError, askQuestion } from "@/lib/api";
import type { AnswerResponse } from "@/lib/types";

interface AskPanelProps {
  token: string;
  onUnauthorized: () => void;
}

export function AskPanel({ token, onUnauthorized }: AskPanelProps) {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<AnswerResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [asking, setAsking] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setAsking(true);
    try {
      const result = await askQuestion(token, question);
      setAnswer(result);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        onUnauthorized();
        return;
      }
      setAnswer(null);
      setError(
        err instanceof ApiError
          ? err.message
          : "The assistant could not answer right now. Please try again later.",
      );
    } finally {
      setAsking(false);
    }
  }

  const isGrounded = (answer?.citations.length ?? 0) > 0;

  return (
    <section className="glass-panel p-8 text-center">
      <p className="mb-4 text-xs uppercase tracking-wide text-[var(--color-text-muted)]">
        Ask about the documents you have access to
      </p>
      <form onSubmit={handleSubmit} className="mx-auto flex max-w-xl flex-col items-center gap-3">
        <input
          className="w-full rounded-2xl border border-[var(--color-panel-border)] bg-white/5 px-4 py-3 text-center text-sm outline-none focus:border-[var(--color-accent-to)]"
          placeholder="What is the salary band for a Senior Engineer?"
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          maxLength={2000}
          required
        />
        <button
          type="submit"
          disabled={asking || question.trim().length === 0}
          className="gradient-button rounded-xl px-6 py-2 text-xs font-bold disabled:opacity-50"
        >
          {asking ? "Asking..." : "Ask →"}
        </button>
      </form>

      {error && <p className="mt-4 text-sm text-red-400">{error}</p>}

      {answer && (
        <div
          className={
            isGrounded
              ? "mt-6 rounded-2xl border border-[var(--color-panel-border)] bg-white/5 p-5 text-left text-sm leading-relaxed"
              : "mt-6 rounded-2xl border border-dashed border-[var(--color-panel-border)] p-5 text-left text-sm italic leading-relaxed text-[var(--color-text-muted)]"
          }
        >
          {answer.answer}
          {isGrounded && (
            <div className="mt-3 flex flex-wrap gap-2">
              {answer.citations.map((citation) => (
                <span
                  key={citation.documentId}
                  className="rounded-full border border-[var(--color-accent-to)]/30 bg-[var(--color-accent-to)]/10 px-3 py-1 text-[10px] text-[var(--color-accent-to)]"
                >
                  📄 {citation.title}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
}
```

- [ ] **Step 2: Wire it into `frontend/app/page.tsx`**

Replace the placeholder paragraph in the logged-in branch:

```tsx
      <p className="text-sm text-[var(--color-text-muted)]">
        Signed in. Ask panel and documents panel land in the next tasks.
      </p>
      {/* handleUnauthorized is wired into AskPanel/DocumentsPanel in Tasks 5 and 6 */}
```

with:

```tsx
      <AskPanel token={token} onUnauthorized={handleUnauthorized} />
```

and add the import at the top of the file:

```tsx
import { AskPanel } from "@/components/AskPanel";
```

- [ ] **Step 3: Build and manually verify the alice/bob contrast**

```bash
cd frontend && npm run build && cd .. && ./mvnw spring-boot:test-run
```
In the browser tool:
1. Log in as `alice`. Upload isn't wired yet (Task 6) — if no `hr` document exists yet, skip to
   step 2 and expect the "not found" state for everyone; otherwise ask
   "What is the salary band for a Senior Engineer?" and expect a normal answer card with a citation
   pill once a matching document exists.
2. Ask a question with no matching document at all (e.g. "What is the weather today?"). Expected:
   muted, dashed, italic "not found" card, no citation pills.
Stop the app.

- [ ] **Step 4: Commit** (confirm with the user first)

```bash
git add frontend/components/AskPanel.tsx frontend/app/page.tsx
git commit -m "Add the ask panel with grounded/not-found states"
```

---

### Task 6: Documents panel

**Files:**
- Create: `frontend/components/DocumentsPanel.tsx`
- Modify: `frontend/app/page.tsx`

- [ ] **Step 1: Create `frontend/components/DocumentsPanel.tsx`**

```tsx
"use client";

import { ChangeEvent, FormEvent, useEffect, useRef, useState } from "react";
import { ApiError, deleteDocument, fetchDocuments, uploadDocument } from "@/lib/api";
import type { CurrentUser, DocumentSummary } from "@/lib/types";

interface DocumentsPanelProps {
  token: string;
  user: CurrentUser;
  onUnauthorized: () => void;
}

export function DocumentsPanel({ token, user, onUnauthorized }: DocumentsPanelProps) {
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [file, setFile] = useState<File | null>(null);
  const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  async function loadDocuments() {
    try {
      setDocuments(await fetchDocuments(token));
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        onUnauthorized();
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadDocuments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function toggleGroup(group: string) {
    setSelectedGroups((current) =>
      current.includes(group) ? current.filter((g) => g !== group) : [...current, group],
    );
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    setFile(event.target.files?.[0] ?? null);
  }

  async function handleUpload(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      return;
    }
    setUploadError(null);
    try {
      await uploadDocument(token, file, selectedGroups);
      setFile(null);
      setSelectedGroups([]);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      await loadDocuments();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          onUnauthorized();
          return;
        }
        setUploadError(err.message);
      }
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteDocument(token, id);
      await loadDocuments();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        onUnauthorized();
        return;
      }
      setToast("Could not delete — not found, or you are not the owner.");
      setTimeout(() => setToast(null), 4000);
    }
  }

  return (
    <section className="glass-panel mt-6 p-6">
      <p className="mb-4 text-xs uppercase tracking-wide text-[var(--color-text-muted)]">Documents</p>

      {loading ? (
        <p className="text-sm text-[var(--color-text-muted)]">Loading...</p>
      ) : documents.length === 0 ? (
        <p className="mb-4 text-sm text-[var(--color-text-muted)]">
          No documents yet — upload one to get started.
        </p>
      ) : (
        <ul className="mb-4 flex flex-col gap-2">
          {documents.map((doc) => (
            <li
              key={doc.id}
              className="flex items-center justify-between rounded-xl bg-white/5 px-3 py-2 text-sm"
            >
              <div className="flex items-center gap-2">
                <span>{doc.title}</span>
                {doc.allowedGroups.map((group) => (
                  <span
                    key={group}
                    className="rounded-full bg-[var(--color-accent-from)]/15 px-2 py-0.5 text-[10px] text-violet-300"
                  >
                    {group}
                  </span>
                ))}
              </div>
              <button
                onClick={() => handleDelete(doc.id)}
                className="text-[var(--color-text-muted)] hover:text-red-400"
                aria-label={`Delete ${doc.title}`}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}

      <form
        onSubmit={handleUpload}
        className="rounded-xl border border-dashed border-[var(--color-panel-border)] p-4"
      >
        <input
          ref={fileInputRef}
          type="file"
          onChange={handleFileChange}
          className="mb-3 block w-full text-xs"
          required
        />
        {user.groups.length > 0 && (
          <div className="mb-3 flex flex-wrap gap-3">
            {user.groups.map((group) => (
              <label key={group} className="flex items-center gap-1 text-xs text-[var(--color-text-muted)]">
                <input
                  type="checkbox"
                  checked={selectedGroups.includes(group)}
                  onChange={() => toggleGroup(group)}
                />
                {group}
              </label>
            ))}
          </div>
        )}
        {uploadError && <p className="mb-3 text-sm text-red-400">{uploadError}</p>}
        <button type="submit" className="gradient-button rounded-lg px-4 py-1.5 text-xs font-semibold">
          + Upload document
        </button>
      </form>

      {toast && (
        <div className="mt-3 rounded-lg border border-red-400/30 bg-red-400/10 px-3 py-2 text-xs text-red-300">
          {toast}
        </div>
      )}
    </section>
  );
}
```

- [ ] **Step 2: Wire it into `frontend/app/page.tsx`**

Add the import:

```tsx
import { DocumentsPanel } from "@/components/DocumentsPanel";
```

Add the panel right after `<AskPanel .../>` in the logged-in branch:

```tsx
      <AskPanel token={token} onUnauthorized={handleUnauthorized} />
      <DocumentsPanel token={token} user={user} onUnauthorized={handleUnauthorized} />
```

- [ ] **Step 3: Build and manually verify**

```bash
cd frontend && npm run build && cd .. && ./mvnw spring-boot:test-run
```
In the browser tool, logged in as `alice`:
1. Upload a small `.txt` file, check the `hr` box. Expected: it appears in the list with an `hr`
   pill, upload form clears.
2. Try uploading a file the backend rejects (e.g. a `.exe`, or nothing selected). Expected: inline
   error text matching the backend's message (unsupported type / too large / etc.).
3. Click the ✕ on a document owned by someone else (or any doc from another user visible via a
   shared group) — Expected: red toast "Could not delete — not found, or you are not the owner."
4. Delete a document you do own. Expected: it disappears from the list.
Stop the app.

- [ ] **Step 4: Commit** (confirm with the user first)

```bash
git add frontend/components/DocumentsPanel.tsx frontend/app/page.tsx
git commit -m "Add the documents panel: upload, list, delete"
```

---

### Task 7: End-to-end demo verification and docs

Prove the actual product claim from the spec (Alice gets a cited answer, Bob gets "not found" for
the same question) and bring the docs up to date.

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Full backend regression**

Run: `./mvnw verify`
Expected: BUILD SUCCESS, all tests green (unchanged since Task 1 — no backend code changed after
that task).

- [ ] **Step 2: Frontend build**

Run: `cd frontend && npm run build`
Expected: succeeds, `../src/main/resources/static/index.html` is refreshed.

- [ ] **Step 3: Run the exact demo scenario in the browser tool**

```bash
./mvnw spring-boot:test-run
```
1. Log in as `alice` (`alice`/`alice`). Upload a `.txt` file containing salary-band text, e.g.
   "Senior Engineer band is L5, $145k-$175k.", shared with group `hr`.
2. Ask "What is the salary band for a Senior Engineer?". Expected: normal answer card with one
   citation pill for that document. Screenshot this state.
3. Sign out, log in as `bob` (`bob`/`bob`, groups `all-staff`, `engineering` — no `hr`).
4. Ask the exact same question. Expected: muted "not found" card, no citations. Screenshot this
   state.
5. Confirm the two screenshots differ exactly as the design describes (this is the product's core
   claim, made visible).

Stop the app.

- [ ] **Step 4: Update `README.md`**

Add a new subsection right after the existing `## Running` section's command block (after the
profile table, before the demo-users table — i.e. right before the `curl` example already there):

```markdown
### Frontend

A single-page UI (login, ask a question, upload/list/delete documents) lives in `frontend/` and is
statically exported into `src/main/resources/static/`, so it's served by the same app on the same
port — no separate process.

```bash
cd frontend && npm install && npm run build   # builds and copies into src/main/resources/static
cd .. && ./mvnw spring-boot:test-run           # open http://localhost:8080
```

Rebuild the frontend (`npm run build`) and restart the backend after every UI change — there's no
dev-server proxy. Keycloak/client config for local login lives in `frontend/.env.example`.
```

- [ ] **Step 5: Update `CLAUDE.md`**

In the "What this is" section's flow diagram area, no change needed (it's about ingestion/retrieval,
not UI). Instead, add one row to the Commands table:

```markdown
| Build the frontend                     | `cd frontend && npm install && npm run build` |
```

placed right after the `Run locally, no API keys (test profile)` row.

- [ ] **Step 6: Commit** (confirm with the user first)

```bash
git add README.md CLAUDE.md
git commit -m "Document the frontend build and run workflow"
```

---

## Definition of done for this plan

- `./mvnw verify` passes (155 tests: 152 existing + 3 from Task 1).
- `cd frontend && npm run build` succeeds with no TypeScript errors.
- The alice/bob contrast from Task 7 Step 3 is demonstrated and screenshotted.
- `README.md` and `CLAUDE.md` reflect the new frontend workflow.
- No commit happened without the user's explicit go-ahead (see Governance note).
