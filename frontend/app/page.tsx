"use client";

import { useEffect, useState } from "react";
import { AskPanel } from "@/components/AskPanel";
import { DocumentsPanel } from "@/components/DocumentsPanel";
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
      <AskPanel token={token} onUnauthorized={handleUnauthorized} />
      <DocumentsPanel token={token} user={user} onUnauthorized={handleUnauthorized} />
    </main>
  );
}
