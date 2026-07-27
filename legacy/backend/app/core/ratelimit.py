"""Sliding-window rate limiter. Redis-backed when configured, in-process otherwise."""

import time
from collections import defaultdict, deque

from app.core.config import get_settings

_local_windows: dict[str, deque[float]] = defaultdict(deque)


def _redis_client():
    settings = get_settings()
    if not settings.redis_url:
        return None
    try:
        import redis

        return redis.Redis.from_url(settings.redis_url, socket_timeout=1)
    except Exception:
        return None


def allow(key: str, limit: int, window_seconds: int = 60) -> bool:
    """Return True if a request identified by `key` is within `limit` per window."""
    r = _redis_client()
    now = time.time()
    if r is not None:
        try:
            pipe = r.pipeline()
            zkey = f"rl:{key}"
            pipe.zremrangebyscore(zkey, 0, now - window_seconds)
            pipe.zadd(zkey, {str(now): now})
            pipe.zcard(zkey)
            pipe.expire(zkey, window_seconds)
            count = pipe.execute()[2]
            return count <= limit
        except Exception:
            pass  # fall through to local limiter on Redis outage
    window = _local_windows[key]
    while window and window[0] < now - window_seconds:
        window.popleft()
    if len(window) >= limit:
        return False
    window.append(now)
    return True


def reset_local() -> None:
    _local_windows.clear()
