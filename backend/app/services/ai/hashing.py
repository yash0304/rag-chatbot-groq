"""Deterministic bag-of-words hashing embeddings.

Dev/test-quality vectors: tokens are hashed into a fixed-size bucket vector and
L2-normalized, so texts sharing vocabulary land near each other under cosine
distance. No network, no model weights — recall quality is far below real
embedding models, which is fine for tests and keyless local development.
"""

import hashlib
import math
import re

_TOKEN_RE = re.compile(r"[a-z0-9]+")


def hash_embed(text: str, dim: int = 256) -> list[float]:
    vec = [0.0] * dim
    for token in _TOKEN_RE.findall(text.lower()):
        h = int.from_bytes(hashlib.md5(token.encode()).digest()[:8], "big")
        vec[h % dim] += 1.0
    norm = math.sqrt(sum(v * v for v in vec))
    if norm > 0:
        vec = [v / norm for v in vec]
    return vec
