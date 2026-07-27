"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Doc } from "@/lib/types";
import { useToast } from "@/components/Toast";

const STATUS_BADGE: Record<Doc["status"], string> = {
  ready: "bg-emerald-900 text-emerald-300",
  processing: "bg-amber-900 text-amber-300",
  failed: "bg-red-900 text-red-300",
};

export default function DocumentsPage() {
  const [docs, setDocs] = useState<Doc[]>([]);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<
    { chunk_id: string; title: string; snippet: string; location: string | null; score: number }[] | null
  >(null);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const toast = useToast();

  const load = useCallback(() => {
    api.get<Doc[]>("/documents").then(setDocs).catch(() => {});
  }, []);

  useEffect(load, [load]);

  useEffect(() => {
    if (docs.some((d) => d.status === "processing")) {
      const t = setTimeout(load, 2500);
      return () => clearTimeout(t);
    }
  }, [docs, load]);

  async function onUpload(files: FileList | null) {
    if (!files?.length) return;
    setBusy(true);
    try {
      for (const file of Array.from(files)) {
        const form = new FormData();
        form.append("file", file);
        await api.upload<Doc>("/documents", form);
      }
      toast(`Uploaded ${files.length} tome${files.length > 1 ? "s" : ""} — the scribes are at work.`);
      load();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Upload failed", "error");
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function onSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!query.trim()) {
      setResults(null);
      return;
    }
    const body = await api.post<{ results: NonNullable<typeof results> }>("/search", {
      query,
      limit: 8,
    });
    setResults(body.results);
  }

  async function onDelete(id: string) {
    await api.del(`/documents/${id}`);
    toast("Tome removed from the archives.");
    load();
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl font-bold text-parchment">The Archives</h1>
          <p className="text-sm text-slate-500">
            PDF, text, markdown, and images — OCR, summaries, tags, and embeddings included.
          </p>
        </div>
        <label className="btn cursor-pointer">
          {busy ? "Uploading…" : "＋ Upload tome"}
          <input
            ref={fileRef}
            type="file"
            multiple
            accept=".pdf,.txt,.md,.png,.jpg,.jpeg"
            className="hidden"
            onChange={(e) => onUpload(e.target.files)}
            disabled={busy}
          />
        </label>
      </div>

      <form onSubmit={onSearch} className="flex gap-2">
        <input
          className="input"
          placeholder="Search by meaning — e.g. 'what did I read about sovereignty?'"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="btn-ghost">Search</button>
      </form>

      {results && (
        <section className="card">
          <h2 className="mb-3 text-sm font-semibold text-parchment">Semantic results</h2>
          {results.length === 0 ? (
            <p className="text-sm text-slate-500">The archives hold nothing on this — yet.</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {results.map((r) => (
                <li key={r.chunk_id} className="border-l-2 border-rune pl-3">
                  <div className="text-xs text-rune">
                    {r.title} {r.location && `· ${r.location}`} · score {r.score.toFixed(2)}
                  </div>
                  <p className="text-sm text-slate-300">{r.snippet}…</p>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <section className="grid gap-4 sm:grid-cols-2">
        {docs.map((d) => (
          <div key={d.id} className="card">
            <div className="mb-2 flex items-start justify-between gap-2">
              <h3 className="font-semibold text-parchment">{d.title}</h3>
              <span className={`rounded px-2 py-0.5 text-xs ${STATUS_BADGE[d.status]}`}>
                {d.status}
              </span>
            </div>
            {d.domain && <div className="mb-1 text-xs text-rune">🗺️ {d.domain}</div>}
            {d.summary && <p className="mb-3 text-sm text-slate-400">{d.summary}</p>}
            {d.error && <p className="mb-3 text-sm text-red-400">{d.error}</p>}
            <div className="mb-3 flex flex-wrap gap-1">
              {d.tags.map((t) => (
                <span key={t.id} className="tag-chip">
                  {t.name}
                </span>
              ))}
            </div>
            <div className="flex items-center justify-between text-xs text-slate-600">
              <span>
                {d.chunk_count} passages{d.ocr_used && " · OCR"}
              </span>
              <button onClick={() => onDelete(d.id)} className="text-red-500 hover:underline">
                delete
              </button>
            </div>
          </div>
        ))}
        {docs.length === 0 && (
          <p className="text-sm text-slate-500">
            The shelves are empty. Upload your first document to chart new territory.
          </p>
        )}
      </section>
    </div>
  );
}
