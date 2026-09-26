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
