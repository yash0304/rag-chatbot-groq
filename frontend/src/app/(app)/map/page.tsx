"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import type { GraphEdge, GraphNode } from "@/lib/types";

interface SimNode extends GraphNode {
  x: number;
  y: number;
  vx: number;
  vy: number;
}

const COLORS: Record<GraphNode["type"], string> = {
  domain: "#f5b942",
  document: "#7dd3fc",
  tag: "#64748b",
};

export default function MapPage() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [graph, setGraph] = useState<{ nodes: GraphNode[]; edges: GraphEdge[] } | null>(null);

  useEffect(() => {
    api.get<{ nodes: GraphNode[]; edges: GraphEdge[] }>("/graph").then(setGraph).catch(() => {});
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !graph || graph.nodes.length === 0) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const W = (canvas.width = canvas.offsetWidth * 2);
    const H = (canvas.height = 1000);
    const nodes: SimNode[] = graph.nodes.map((n, i) => ({
      ...n,
      x: W / 2 + Math.cos((i / graph.nodes.length) * Math.PI * 2) * 200,
      y: H / 2 + Math.sin((i / graph.nodes.length) * Math.PI * 2) * 200,
      vx: 0,
      vy: 0,
    }));
    const byId = new Map(nodes.map((n) => [n.id, n]));
    let frame = 0;
    let raf = 0;

    function tick() {
      // basic force layout: repulsion + spring edges + centering
      for (const a of nodes) {
        for (const b of nodes) {
          if (a === b) continue;
          const dx = a.x - b.x;
          const dy = a.y - b.y;
          const d2 = Math.max(dx * dx + dy * dy, 100);
          const f = 60000 / d2;
          const d = Math.sqrt(d2);
          a.vx += (dx / d) * f * 0.01;
          a.vy += (dy / d) * f * 0.01;
        }
        a.vx += (W / 2 - a.x) * 0.0015;
        a.vy += (H / 2 - a.y) * 0.0015;
      }
      for (const e of graph!.edges) {
        const s = byId.get(e.source);
        const t = byId.get(e.target);
        if (!s || !t) continue;
        const dx = t.x - s.x;
        const dy = t.y - s.y;
        const d = Math.max(Math.sqrt(dx * dx + dy * dy), 1);
        const f = (d - 150) * 0.002 * e.weight;
        s.vx += (dx / d) * f;
        s.vy += (dy / d) * f;
        t.vx -= (dx / d) * f;
        t.vy -= (dy / d) * f;
      }
      for (const n of nodes) {
        n.vx *= 0.85;
        n.vy *= 0.85;
        n.x += n.vx;
        n.y += n.vy;
      }

      ctx!.clearRect(0, 0, W, H);
      ctx!.strokeStyle = "rgba(100,116,139,0.3)";
      for (const e of graph!.edges) {
        const s = byId.get(e.source);
        const t = byId.get(e.target);
        if (!s || !t) continue;
        ctx!.beginPath();
        ctx!.moveTo(s.x, s.y);
        ctx!.lineTo(t.x, t.y);
        ctx!.stroke();
      }
      for (const n of nodes) {
        const r = n.type === "domain" ? 14 + n.size * 2 : n.type === "document" ? 10 : 6;
        ctx!.beginPath();
        ctx!.fillStyle = COLORS[n.type];
        ctx!.arc(n.x, n.y, r, 0, Math.PI * 2);
        ctx!.fill();
        ctx!.fillStyle = "#e8dcc3";
        ctx!.font = n.type === "domain" ? "bold 22px Georgia" : "18px sans-serif";
        ctx!.fillText(n.label.slice(0, 28), n.x + r + 6, n.y + 5);
      }

      frame += 1;
      if (frame < 300) raf = requestAnimationFrame(tick);
    }
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [graph]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-3xl font-bold text-parchment">World Map</h1>
        <p className="text-sm text-slate-500">
          Your knowledge as territory: <span className="text-rune">● domains</span>,{" "}
          <span className="text-sky-300">● documents</span>,{" "}
          <span className="text-slate-400">● tags</span>.
        </p>
      </div>
      {graph && graph.nodes.length === 0 ? (
        <div className="card text-sm text-slate-500">
          The map is blank parchment. Upload documents to chart your first territory.
        </div>
      ) : (
        <canvas ref={canvasRef} className="h-[500px] w-full rounded-xl border border-slate-800 bg-realm" />
      )}
    </div>
  );
}
