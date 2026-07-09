"""Groq backend via its OpenAI-compatible chat completions API.

Groq does not serve embedding models, so vectors fall back to deterministic
hashing embeddings (see hashing.py). For production-quality retrieval use
AI_PROVIDER=openai or point retrieval at a dedicated embedding service.
"""

import httpx

from app.core.config import get_settings
from app.services.ai.hashing import hash_embed
from app.services.ai.provider import AIProvider

_API = "https://api.groq.com/openai/v1"


class GroqProvider(AIProvider):
    name = "groq"
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
        settings = get_settings()
        messages = [{"role": "system", "content": system}]
        for msg in history or []:
            messages.append({"role": msg["role"], "content": msg["content"]})
        messages.append({"role": "user", "content": user})
        body: dict = {"model": settings.groq_model, "messages": messages}
        if json_mode:
            body["response_format"] = {"type": "json_object"}
        resp = httpx.post(
            f"{_API}/chat/completions",
            headers={"Authorization": f"Bearer {settings.groq_api_key}"},
            json=body,
            timeout=120,
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"]
