from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.core import ratelimit
from app.core.config import get_settings
from app.core.security import decode_access_token
from app.db.session import get_db
from app.models import User

_bearer = HTTPBearer(auto_error=False)


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
) -> User:
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Not authenticated")
    user_id = decode_access_token(credentials.credentials)
    if user_id is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired token")
    user = db.get(User, user_id)
    if user is None or not user.is_active:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "User not found or inactive")
    return user


def rate_limit_ai(request: Request, user: User = Depends(get_current_user)) -> User:
    settings = get_settings()
    if not ratelimit.allow(f"ai:{user.id}", settings.rate_limit_ai):
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS, "AI rate limit exceeded", headers={"Retry-After": "60"}
        )
    return user


def rate_limit_auth(request: Request) -> None:
    settings = get_settings()
    client_ip = request.client.host if request.client else "unknown"
    if not ratelimit.allow(f"auth:{client_ip}", settings.rate_limit_auth):
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS, "Too many attempts", headers={"Retry-After": "60"}
        )
