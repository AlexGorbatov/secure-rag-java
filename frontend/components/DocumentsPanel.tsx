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
  const [loadError, setLoadError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const toastTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  async function loadDocuments() {
    try {
      setDocuments(await fetchDocuments(token));
      setLoadError(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        onUnauthorized();
        return;
      }
      setLoadError(
        err instanceof ApiError
          ? err.message
          : "Could not load your documents. Please try again later.",
      );
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
      } else {
        setUploadError("Could not upload the document. Please try again later.");
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
      if (toastTimeoutRef.current) {
        clearTimeout(toastTimeoutRef.current);
      }
      toastTimeoutRef.current = setTimeout(() => setToast(null), 4000);
    }
  }

  return (
    <section className="glass-panel mt-6 p-6">
      <p className="mb-4 text-xs uppercase tracking-wide text-[var(--color-text-muted)]">Documents</p>

      {loading ? (
        <p className="text-sm text-[var(--color-text-muted)]">Loading...</p>
      ) : loadError ? (
        <p className="mb-4 text-sm text-red-400">{loadError}</p>
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
        <label className="mb-3 block text-xs uppercase tracking-wide text-[var(--color-text-muted)]">
          Choose a file to upload
          <input
            ref={fileInputRef}
            type="file"
            onChange={handleFileChange}
            className="mt-1 block w-full text-xs"
            required
          />
        </label>
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
