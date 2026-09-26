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
