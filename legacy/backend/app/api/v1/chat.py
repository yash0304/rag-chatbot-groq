from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, rate_limit_ai
from app.db.session import get_db
from app.models import ChatMessage, ChatSession, User
from app.schemas.chat import ChatMessageIn, ChatMessageOut, ChatSessionIn, ChatSessionOut
from app.schemas.document import SearchIn, SearchOut
from app.services.rag import chat_answer, semantic_search

router = APIRouter(tags=["search & chat"])


@router.post("/search", response_model=SearchOut)
def search(body: SearchIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return {"results": semantic_search(db, user.id, body.query, limit=min(body.limit, 20))}


@router.post("/chat/sessions", response_model=ChatSessionOut, status_code=status.HTTP_201_CREATED)
def create_session(
    body: ChatSessionIn, user: User = Depends(get_current_user), db: Session = Depends(get_db)
):
    session = ChatSession(user_id=user.id, title=body.title or "New conversation")
    db.add(session)
    db.commit()
    return session


@router.get("/chat/sessions", response_model=list[ChatSessionOut])
def list_sessions(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return db.scalars(
        select(ChatSession)
        .where(ChatSession.user_id == user.id)
        .order_by(ChatSession.created_at.desc())
    ).all()


def _owned_session(db: Session, user: User, session_id: str) -> ChatSession:
    session = db.get(ChatSession, session_id)
    if session is None or session.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Session not found")
    return session


@router.get("/chat/sessions/{session_id}/messages", response_model=list[ChatMessageOut])
def get_messages(session_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    session = _owned_session(db, user, session_id)
    return db.scalars(
        select(ChatMessage)
        .where(ChatMessage.session_id == session.id)
        .order_by(ChatMessage.created_at)
    ).all()


@router.post("/chat/sessions/{session_id}/messages", response_model=ChatMessageOut)
def post_message(
    session_id: str,
    body: ChatMessageIn,
    user: User = Depends(rate_limit_ai),
    db: Session = Depends(get_db),
):
    session = _owned_session(db, user, session_id)
    return chat_answer(db, user.id, session.id, body.content)
