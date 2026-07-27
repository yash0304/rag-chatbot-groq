"""OpenAI backend using the Responses API for text and /v1/embeddings for vectors."""

import httpx

from app.core.config import get_settings
from app.services.ai.provider import AIProvider

_API = "https://api.openai.com/v1"


class OpenAIProvider(AIProvider):
    name = "openai"
    embed_dim = 1536  # text-embedding-3-small

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {get_settings().openai_api_key}"}

    def embed(self, texts: list[str]) -> list[list[float]]:
        settings = get_settings()
        resp = httpx.post(
            f"{_API}/embeddings",
            headers=self._headers(),
            json={"model": settings.openai_embed_model, "input": texts},
            timeout=60,
        )
        resp.raise_for_status()
        data = sorted(resp.json()["data"], key=lambda d: d["index"])
        return [d["embedding"] for d in data]

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
        input_items: list[dict] = []
        for msg in history or []:
            input_items.append({"role": msg["role"], "content": msg["content"]})
        input_items.append({"role": "user", "content": user})
        body: dict = {
            "model": settings.openai_model,
            "instructions": system,
            "input": input_items,
            "metadata": {"purpose": purpose},
        }
        if json_mode:
            body["text"] = {"format": {"type": "json_object"}}
        resp = httpx.post(f"{_API}/responses", headers=self._headers(), json=body, timeout=120)
        resp.raise_for_status()
        payload = resp.json()
        if payload.get("output_text"):
            return payload["output_text"]
        parts: list[str] = []
        for item in payload.get("output", []):
            for content in item.get("content", []):
                if content.get("type") == "output_text":
                    parts.append(content.get("text", ""))
        return "".join(parts)
