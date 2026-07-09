"""Deterministic offline provider for tests and keyless development.

Produces plausible, purpose-shaped outputs without any network access so the
entire product loop (ingestion → search → RAG chat → quest generation →
weekly review) runs in CI.
"""

import json
import re

from app.services.ai.hashing import hash_embed
from app.services.ai.provider import AIProvider


class StubProvider(AIProvider):
    name = "stub"
    embed_dim = 256

    def embed(self, texts: list[str]) -> list[list[float]]:
        return [hash_embed(t, self.embed_dim) for t in texts]

    def complete(
        self,
        system: str,
        user: str,
        *,
        purpose: str = "chat",
        json_mode: bool = False,
        history: list[dict] | None = None,
    ) -> str:
        if purpose == "summary":
            sentences = re.split(r"(?<=[.!?])\s+", user.strip())
            return " ".join(sentences[:2])[:400] or "A tome of gathered knowledge."
        if purpose == "tags":
            words = re.findall(r"[a-zA-Z]{5,}", user.lower())
            seen: list[str] = []
            for w in words:
                if w not in seen:
                    seen.append(w)
                if len(seen) == 4:
                    break
            return json.dumps(seen or ["knowledge"])
        if purpose == "domain":
            words = re.findall(r"[a-zA-Z]{5,}", user.lower())
            return (words[0].capitalize() if words else "General") + " Realm"
        if purpose == "quests":
            return json.dumps(
                [
                    {
                        "title": "Chart the newly discovered archives",
                        "description": "Review your latest materials and note three key insights.",
                        "difficulty": "easy",
                    },
                    {
                        "title": "Forge a summary scroll",
                        "description": "Write a one-page synthesis of a recent document.",
                        "difficulty": "normal",
                    },
                    {
                        "title": "Venture beyond the map's edge",
                        "description": "Spend 45 focused minutes advancing your active goal.",
                        "difficulty": "hard",
                    },
                ]
            )
        if purpose == "arc_theme":
            return "The Ascent of the Quiet Cartographer"
        if purpose == "review":
            return (
                "This week your hero pressed steadily onward: quests were struck from the ledger, "
                "daily missions kept the campfires lit, and new territories were charted in the "
                "archives. The Narrator counsels one bold quest for the coming week — pick the "
                "milestone you have been avoiding and strike first at dawn."
            )
        # purpose == "chat": cite every provided context block, or admit the archives are silent.
        markers = re.findall(r"^\[(\d+)\]", user, flags=re.MULTILINE)
        if markers:
            cites = " ".join(f"[{m}]" for m in markers[:3])
            return f"Drawing on the archives, the answer is grounded in your records {cites}."
        return "The archives hold no scrolls on this matter; I cannot answer from your knowledge base."
