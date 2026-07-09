"use client";

import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { ChatMessage } from "@/lib/types";
import { useToast } from "@/components/Toast";

interface Session {
  id: string;
  title: string;
}

export default function ChatPage() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [active, setActive] = useState<Session | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const toast = useToast();

  useEffect(() => {
    api.get<Session[]>("/chat/sessions").then((s) => {
      setSessions(s);
      if (s.length > 0) setActive(s[0]);
    });
  }, []);

  useEffect(() => {
    if (!active) return;
    api.get<ChatMessage[]>(`/chat/sessions/${active.id}/messages`).then(setMessages);
  }, [active]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  async function newSession() {
    const s = await api.post<Session>("/chat/sessions", { title: "New conversation" });
    setSessions((prev) => [s, ...prev]);
    setActive(s);
    setMessages([]);
  }

  async function send(e: React.FormEvent) {
    e.preventDefault();
    if (!input.trim() || busy) return;
    let session = active;
    if (!session) {
      session = await api.post<Session>("/chat/sessions", {});
      setSessions((prev) => [session!, ...prev]);
      setActive(session);
    }
    const text = input;
    setInput("");
    setBusy(true);
    setMessages((prev) => [
      ...prev,
      { id: "tmp", role: "user", content: text, citations: [], created_at: "" },
    ]);
    try {
      const reply = await api.post<ChatMessage>(`/chat/sessions/${session.id}/messages`, {
        content: text,
      });
      setMessages((prev) => [...prev.filter((m) => m.id !== "tmp"),
        { id: `u-${Date.now()}`, role: "user", content: text, citations: [], created_at: "" },
        reply,
      ]);
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "The Narrator is silent — try again.", "error");
      setMessages((prev) => prev.filter((m) => m.id !== "tmp"));
      setInput(text);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex h-[calc(100vh-6rem)] flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl font-bold text-parchment">The Narrator</h1>
          <p className="text-sm text-slate-500">Answers grounded in your archives, with citations.</p>
        </div>
        <button onClick={newSession} className="btn-ghost">
          ＋ New conversation
        </button>
      </div>

      {sessions.length > 1 && (
        <div className="flex gap-2 overflow-x-auto">
          {sessions.map((s) => (
            <button
              key={s.id}
              onClick={() => setActive(s)}
              className={`whitespace-nowrap rounded-full px-3 py-1 text-xs ${
                active?.id === s.id ? "bg-rune text-abyss" : "bg-slate-800 text-slate-400"
              }`}
            >
              {s.title}
            </button>
          ))}
        </div>
      )}

      <div className="card flex-1 overflow-y-auto">
        {messages.length === 0 && (
          <p className="text-sm text-slate-500">
            Ask the Narrator about anything you&apos;ve archived. Every claim is cited back to your
            own documents.
          </p>
        )}
        <div className="flex flex-col gap-4">
          {messages.map((m, i) => (
            <div key={m.id + i} className={m.role === "user" ? "text-right" : ""}>
              <div
                className={`inline-block max-w-[85%] rounded-xl px-4 py-3 text-sm ${
                  m.role === "user" ? "bg-rune/20 text-parchment" : "bg-slate-800 text-slate-200"
                }`}
              >
                <p className="whitespace-pre-wrap">{m.content}</p>
                {m.citations.length > 0 && (
                  <div className="mt-3 border-t border-slate-700 pt-2 text-left">
                    {m.citations.map((c) => (
                      <div key={c.index} className="mb-1 text-xs text-slate-400">
                        <span className="text-rune">[{c.index}]</span> {c.title}
                        {c.location && ` · ${c.location}`} — “{c.snippet}…”
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
          {busy && <p className="text-sm text-slate-500">The Narrator consults the archives…</p>}
          <div ref={bottomRef} />
        </div>
      </div>

      <form onSubmit={send} className="flex gap-2">
        <input
          className="input"
          placeholder="Ask about your knowledge…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={busy}
        />
        <button className="btn" disabled={busy || !input.trim()}>
          Send
        </button>
      </form>
    </div>
  );
}
