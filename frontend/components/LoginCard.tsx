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
