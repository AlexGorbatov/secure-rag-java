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
