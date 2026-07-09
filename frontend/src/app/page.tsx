import Link from "next/link";

const FEATURES = [
  {
    title: "Your knowledge is the world map",
    body: "Every document you upload is OCR'd, summarized, tagged, embedded, and charted as territory in your personal realm — searchable by meaning, not keywords.",
    icon: "🗺️",
  },
  {
    title: "Real tasks, real quests",
    body: "Tasks become quests with difficulty-scaled XP. Habits are daily missions with streak multipliers. Goals unfold as story arcs written by the Narrator.",
    icon: "⚔️",
  },
  {
    title: "A narrator that cites its sources",
    body: "Chat with an AI sage grounded in your own archives. Every answer carries citations back to the exact passage — no fabrication, ever.",
    icon: "🔮",
  },
  {
    title: "Progression you actually earned",
    body: "XP, levels, skill trees, achievements, and collectibles derive from verifiable productivity and learning. Nothing is purchasable; everything is earned.",
    icon: "🏆",
  },
];

export default function Landing() {
  return (
    <main className="mx-auto max-w-5xl px-6 py-16">
      <nav className="mb-16 flex items-center justify-between">
        <span className="font-display text-xl font-bold text-rune">MindQuest</span>
        <div className="flex gap-3">
          <Link href="/login" className="btn-ghost">
            Sign in
          </Link>
          <Link href="/register" className="btn">
            Begin your saga
          </Link>
        </div>
      </nav>

      <section className="mb-20 text-center">
        <h1 className="font-display mb-6 text-5xl font-bold leading-tight text-parchment">
          Your second brain,
          <br />
          <span className="text-rune">played like an adventure.</span>
        </h1>
        <p className="mx-auto mb-8 max-w-2xl text-lg text-slate-400">
          MindQuest turns documents into territories, tasks into quests, habits into daily
          missions, and goals into story arcs — with an AI narrator who answers from your own
          knowledge, citations included.
        </p>
        <Link href="/register" className="btn text-base">
          Create your hero — free
        </Link>
      </section>

      <section className="grid gap-6 sm:grid-cols-2">
        {FEATURES.map((f) => (
          <div key={f.title} className="card">
            <div className="mb-3 text-3xl">{f.icon}</div>
            <h2 className="mb-2 font-semibold text-parchment">{f.title}</h2>
            <p className="text-sm text-slate-400">{f.body}</p>
          </div>
        ))}
      </section>

      <footer className="mt-20 border-t border-slate-800 pt-6 text-center text-xs text-slate-600">
        An original world. No copyrighted game assets, characters, music, or storylines.
      </footer>
    </main>
  );
}
