"""AI provider abstraction.

One interface, three backends:
- OpenAIProvider  — OpenAI Responses API + embeddings
- GroqProvider    — Groq's OpenAI-compatible chat completions (hash embeddings fallback)
- StubProvider    — deterministic, offline; used in tests and keyless dev

Switch with the AI_PROVIDER env var. Services never import a concrete backend.
"""

from abc import ABC, abstractmethod

from app.core.config import get_settings


class AIProvider(ABC):
    name: str = "base"
    embed_dim: int = 256

    @abstractmethod
    def embed(self, texts: list[str]) -> list[list[float]]:
        """Return one vector per input text."""

    @abstractmethod
    def complete(
        self,
        system: str,
        user: str,
        *,
        purpose: str = "chat",
        json_mode: bool = False,
        history: list[dict] | None = None,
    ) -> str:
        """Return the model's text response. `purpose` labels the call for
        observability and lets the stub produce fit-for-purpose fixtures."""


_provider: AIProvider | None = None


def get_ai_provider() -> AIProvider:
    global _provider
    settings = get_settings()
    if _provider is not None and _provider.name == settings.ai_provider:
        return _provider
    if settings.ai_provider == "openai":
        from app.services.ai.openai_provider import OpenAIProvider

        _provider = OpenAIProvider()
    elif settings.ai_provider == "groq":
        from app.services.ai.groq_provider import GroqProvider

        _provider = GroqProvider()
    else:
        from app.services.ai.stub import StubProvider

        _provider = StubProvider()
    return _provider


def reset_provider() -> None:
    global _provider
    _provider = None
